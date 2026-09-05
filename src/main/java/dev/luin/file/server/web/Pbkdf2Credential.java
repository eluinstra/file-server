/*
 * Copyright 2020 E.Luinstra
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.luin.file.server.web;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import lombok.val;

/**
 * A password credential verified with PBKDF2-with-HmacSHA256, a salted, iterated key-derivation
 * function. The salt, iteration count and derived key are embedded in the stored string (base64)
 * so that verification never depends on a fixed salt. This replaces the previous unsalted,
 * single-iteration MD5 hashing for basic-auth realm entries.
 */
public class Pbkdf2Credential extends org.eclipse.jetty.util.security.Credential
{
	private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
	private static final int KEY_LENGTH_BITS = 256;
	private static final int HASH_LENGTH_BYTES = KEY_LENGTH_BITS / 8;
	private static final int SALT_LENGTH_BYTES = 16;
	private static final int DEFAULT_ITERATIONS = 210000;

	private final int iterations;
	private final byte[] salt;
	private final byte[] storedHash;

	private Pbkdf2Credential(int iterations, byte[] salt, byte[] storedHash)
	{
		this.iterations = iterations;
		this.salt = salt.clone();
		this.storedHash = storedHash.clone();
	}

	/**
	 * Derives a credential from a clear-text password, generating a fresh random salt and the
	 * PBKDF2 hash. The result is the value to persist for the user.
	 *
	 * @param password the clear-text password
	 * @return a credential whose {@link #toString()} is the persisted value
	 */
	public static Pbkdf2Credential fromPassword(final String password)
	{
		val salt = new byte[SALT_LENGTH_BYTES];
		new SecureRandom().nextBytes(salt);
		val hash = derive(password, salt, DEFAULT_ITERATIONS);
		return new Pbkdf2Credential(DEFAULT_ITERATIONS, salt, hash);
	}

	/**
	 * Decodes a credential persisted via {@link #toString()}.
	 *
	 * @param encoded the base64 credential produced by {@link #toString()}
	 * @return the credential
	 */
	public static Pbkdf2Credential decode(final String encoded)
	{
		try
		{
			val buffer = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
			val iterations = buffer.readInt();
			val salt = new byte[SALT_LENGTH_BYTES];
			buffer.readFully(salt);
			val storedHash = new byte[HASH_LENGTH_BYTES];
			buffer.readFully(storedHash);
			return new Pbkdf2Credential(iterations, salt, storedHash);
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Invalid PBKDF2 credential", e);
		}
	}

	@Override
	public boolean check(final Object password)
	{
		if (!(password instanceof String))
			return false;
		val candidate = derive((String) password, salt, iterations);
		return MessageDigest.isEqual(candidate, storedHash);
	}

	@Override
	public String toString()
	{
		try
		{
			val out = new ByteArrayOutputStream();
			val buffer = new DataOutputStream(out);
			buffer.writeInt(iterations);
			buffer.write(salt);
			buffer.write(storedHash);
			return Base64.getEncoder().encodeToString(out.toByteArray());
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static byte[] derive(final String password, final byte[] salt, final int iterations)
	{
		try
		{
			val spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
			val factory = SecretKeyFactory.getInstance(ALGORITHM);
			return factory.generateSecret(spec).getEncoded();
		}
		catch (NoSuchAlgorithmException | InvalidKeySpecException e)
		{
			throw new IllegalStateException("PBKDF2WithHmacSHA256 is required by the JDK", e);
		}
	}
}
