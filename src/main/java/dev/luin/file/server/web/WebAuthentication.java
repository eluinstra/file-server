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

import dev.luin.file.server.Config;
import dev.luin.file.server.SystemInterface;
import dev.luin.file.server.core.KeyStoreManager.KeyStoreType;
import dev.luin.file.server.core.server.servlet.ClientCertificateAuthenticationFilter;
import dev.luin.file.server.core.server.servlet.ClientCertificateManagerFilter;
import jakarta.servlet.DispatcherType;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.val;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.cxf.transport.servlet.CXFServlet;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.eclipse.jetty.ee10.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.security.ConstraintMapping;
import org.eclipse.jetty.ee10.servlet.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.Constraint;
import org.eclipse.jetty.security.Constraint.Authorization;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Handler;
import org.springframework.web.context.ContextLoaderListener;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class WebAuthentication implements Config, SystemInterface
{
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum Option
	{
		CLIENT_CERTIFICATE_HEADER("clientCertificateHeader"),
		AUTHENTICATION("authentication"),
		NO_AUTHENTICATION("noAuthentication"),
		CLIENT_TRUST_STORE_TYPE("clientTrustStoreType"),
		CLIENT_TRUST_STORE_PATH("clientTrustStorePath"),
		CLIENT_TRUST_STORE_PASSWORD("clientTrustStorePassword");

		String name;
	}

	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum DefaultValue
	{
		KEYSTORE_TYPE(KeyStoreType.PKCS12.name());

		String value;
	}

	private static final String REALM = "Realm";
	private static final String REALM_FILE = "realm.properties";
	TextIO textIO = TextIoFactory.getTextIO();
	CommandLine cmd;
	WebServer webServer;

	public static Options addOptions(Options options)
	{
		options.addOption(Option.CLIENT_CERTIFICATE_HEADER.name, true, "set client certificate header [default: " + NONE + "]");
		options.addOption(Option.AUTHENTICATION.name, false, "basic | client certificate authentication (always enabled; this option is accepted for compatibility)");
		options.addOption(Option.NO_AUTHENTICATION.name, false, "disable SOAP/REST authentication (insecure; not for production)");
		options.addOption(Option.CLIENT_TRUST_STORE_TYPE.name, true, "set client truststore type [default: " + DefaultValue.KEYSTORE_TYPE.value + "]");
		options.addOption(Option.CLIENT_TRUST_STORE_PATH.name, true, "set client truststore path [default: " + NONE + "]");
		options.addOption(Option.CLIENT_TRUST_STORE_PASSWORD.name, true, "set client truststore password [default: " + NONE + "]");
		return options;
	}

	public Handler createContextHandler(ContextLoaderListener contextLoaderListener) throws IOException
	{
		val result = new ServletContextHandler(ServletContextHandler.SESSIONS);
		result.addVirtualHosts(new String[]{"@" + webServer.getWebConnectorName()});
		result.setInitParameter("configuration", "deployment");
		result.setContextPath(webServer.getPath(cmd));
		// Authentication on the SOAP/REST endpoints is mandatory (like the EbMS Admin): the server refuses to run
		// an unauthenticated management/data plane. It is only relaxed with the explicit --noAuthentication flag.
		if (cmd.hasOption(Option.NO_AUTHENTICATION.name))
		{
			println("WARNING: SOAP/REST authentication is DISABLED (--noAuthentication). Do not use in production.");
		}
		else if (!webServer.isClientAuthenticationEnabled())
		{
			println("Configuring Web Server basic authentication (PBKDF2):");
			val credential = createRealm();
			result.setSecurityHandler(getSecurityHandler(credential));
		}
		else
		{
			result.addFilter(createClientCertificateManagerFilterHolder(cmd), "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR));
			result.addFilter(createClientCertificateAuthenticationFilterHolder(cmd), "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.ERROR));
		}
		result.addServlet(CXFServlet.class, webServer.getSoapPath() + "/*");
		result.setErrorHandler(createErrorHandler());
		result.addEventListener(contextLoaderListener);
		return result;
	}

	private RealmEntry createRealm() throws IOException
	{
		// Persist the PBKDF2 credential to realm.properties so restarts (e.g. the docker demo) are non-interactive.
		val realmFile = new File(REALM_FILE);
		if (realmFile.exists())
		{
			println("Using basic-auth realm from " + realmFile.getAbsolutePath());
			val line = Files.readString(realmFile.toPath(), StandardCharsets.UTF_8).trim();
			val separator = line.indexOf(' ');
			return new RealmEntry(line.substring(0, separator), Pbkdf2Credential.decode(line.substring(separator + 1)));
		}
		val username = textIO.newStringInputReader().withDefaultValue("admin").read("enter username");
		val credential = readCredential();
		println("Writing basic-auth realm to " + realmFile.getAbsolutePath() + " (PBKDF2, salted):");
		Files.writeString(realmFile.toPath(), username + " " + credential.toString(), StandardCharsets.UTF_8);
		return new RealmEntry(username, credential);
	}

	private Pbkdf2Credential readCredential()
	{
		val reader = textIO.newStringInputReader().withMinLength(8).withInputMasking(true);
		while (true)
		{
			val first = reader.read("enter password");
			val second = reader.read("re-enter password");
			if (first.equals(second))
				return Pbkdf2Credential.fromPassword(first);
			else
				println("Passwords don't match! Try again.");
		}
	}

	private SecurityHandler getSecurityHandler(final RealmEntry realmEntry)
	{
		val result = new ConstraintSecurityHandler();
		val constraint = createSecurityConstraint();
		val mapping = createSecurityConstraintMapping(constraint);
		result.setConstraintMappings(Collections.singletonList(mapping));
		result.setAuthenticator(new BasicAuthenticator());
		val loginService = new HashLoginService(REALM);
		val userStore = new UserStore();
		userStore.addUser(realmEntry.username, realmEntry.credential, new String[]{"user"});
		loginService.setUserStore(userStore);
		result.setLoginService(loginService);
		return result;
	}

	/**
	 * A basic-auth realm entry: the username together with the credential used to verify the
	 * presented password.
	 */
	private static final class RealmEntry
	{
		final String username;
		final org.eclipse.jetty.util.security.Credential credential;

		RealmEntry(String username, org.eclipse.jetty.util.security.Credential credential)
		{
			this.username = username;
			this.credential = credential;
		}
	}

	private Constraint createSecurityConstraint()
	{
		return new Constraint.Builder().name("auth").roles("user", "admin").authorization(Authorization.FORBIDDEN).build();
	}

	private ConstraintMapping createSecurityConstraintMapping(final Constraint constraint)
	{
		val result = new ConstraintMapping();
		result.setPathSpec("/*");
		result.setConstraint(constraint);
		return result;
	}

	private ErrorPageErrorHandler createErrorHandler()
	{
		val result = new ErrorPageErrorHandler();
		val errorPages = new HashMap<String, String>();
		errorPages.put("404", "/404");
		result.setErrorPages(errorPages);
		return result;
	}

	protected FilterHolder createClientCertificateManagerFilterHolder(CommandLine cmd)
	{
		val result = new FilterHolder(ClientCertificateManagerFilter.class);
		result.setInitParameter("x509CertificateHeader", cmd.getOptionValue(Option.CLIENT_CERTIFICATE_HEADER.name));
		return result;
	}

	protected FilterHolder createClientCertificateAuthenticationFilterHolder(CommandLine cmd) throws IOException
	{
		println("Configuring Web Server client certificate authentication:");
		val result = new FilterHolder(ClientCertificateAuthenticationFilter.class);
		val clientTrustStoreType = cmd.getOptionValue(Option.CLIENT_TRUST_STORE_TYPE.name, DefaultValue.KEYSTORE_TYPE.value);
		val clientTrustStorePath = cmd.getOptionValue(Option.CLIENT_TRUST_STORE_PATH.name);
		val clientTrustStorePassword = cmd.getOptionValue(Option.CLIENT_TRUST_STORE_PASSWORD.name);
		val trustStore = getResource(clientTrustStorePath);
		println("Using clientTrustStore " + trustStore.getURI());
		if (trustStore.exists())
		{
			result.setInitParameter("trustStoreType", clientTrustStoreType);
			result.setInitParameter("trustStorePath", clientTrustStorePath);
			result.setInitParameter("trustStorePassword", clientTrustStorePassword);
			return result;
		}
		else
		{
			println("Web Server not available: clientTrustStore " + clientTrustStorePath + " not found!");
			exit(1);
			return null;
		}
	}
}
