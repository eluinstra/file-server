/**
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
package dev.luin.fs.web.configuration;

import java.beans.PropertyVetoException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Scanner;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import dev.luin.fs.core.KeyStoreManager.KeyStoreType;
import lombok.NonNull;
import lombok.val;

public class Utils
{
	private static SSLEngine sslEngine;
	static 
	{
		try
		{
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null,null,null);
			sslEngine = sslContext.createSSLEngine();
		}
		catch (Exception e)
		{
		}
	}

	public static String[] getSupportedSSLProtocols()
	{
		return sslEngine.getSupportedProtocols();
	}

	public static String[] getSupportedSSLCipherSuites()
	{
		return sslEngine.getSupportedCipherSuites();
	}

	public static String createURL(@NonNull final String hostname, final int port)
	{
		return hostname + (port == -1 ? "" : ":" + port); 
	}
	
	public static String createURL(@NonNull final String hostname, @NonNull final Integer port)
	{
		return hostname + (port == null ? "" : ":" + port); 
	}
	
	public static JdbcURL parseJdbcURL(@NonNull final String jdbcURL, @NonNull final JdbcURL model) throws MalformedURLException
	{
		try (val scanner = new Scanner(jdbcURL))
		{
			val protocol = scanner.findInLine("(://|@|:@//)");
			if (protocol != null)
			{
				val urlString = scanner.findInLine("[^/:]+(:\\d+){0,1}");
				scanner.findInLine("(/|:|;databaseName=)");
				val database = scanner.findInLine("[^;]*");
				if (urlString != null)
				{
					val url = new URL("http://" + urlString);
					model.setHost(url.getHost());
					model.setPort(url.getPort() == -1 ? null : url.getPort());
					model.setDatabase(database);
				}
			}
			return model;
		}
	}

	public static Resource getResource(@NonNull final String path) throws MalformedURLException, IOException
	{
		val result = new FileSystemResource(path);
		return result.exists() ? result : new ClassPathResource(path);
	}
  
	public static void testKeystore(@NonNull final KeyStoreType type, @NonNull final String path, @NonNull final String password) throws MalformedURLException, IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException
	{
		val resource = getResource(path);
		val keyStore = KeyStore.getInstance(type.name());
		keyStore.load(resource.getInputStream(),password.toCharArray());
		val aliases = keyStore.aliases();
		while (aliases.hasMoreElements())
		{
			val alias = aliases.nextElement();
			if (keyStore.isKeyEntry(alias))
				keyStore.getKey(alias,password.toCharArray());
		}
	}

	public static void testJdbcConnection(@NonNull final String driverClassName, @NonNull final String jdbcUrl, @NonNull final String username, @NonNull final String password) throws PropertyVetoException, SQLException, ClassNotFoundException, InstantiationException, IllegalAccessException
	{
    val loader = Utils.class.getClassLoader();
    val driverClass = loader.loadClass(driverClassName);
    val driver = (Driver)driverClass.newInstance();
    if (!driver.acceptsURL(jdbcUrl))
    	throw new IllegalArgumentException("Jdbc Url '" + jdbcUrl + "' not valid!");
    val info = new Properties();
    info.setProperty("user",username);
    if (password != null)
    	info.setProperty("password",password);
    try (val connection = driver.connect(jdbcUrl,info))
    {
    }
	}

}
