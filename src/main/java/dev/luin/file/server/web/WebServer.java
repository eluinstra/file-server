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
package dev.luin.file.server.web;

import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.ConnectionLimit;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import dev.luin.file.server.Config;
import dev.luin.file.server.SystemInterface;
import dev.luin.file.server.core.KeyStoreManager.KeyStoreType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.val;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class WebServer implements Config, SystemInterface
{
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum Option
	{
		HOST("host"),
		PORT("port"),
		PATH("path"),
		SSL("ssl"),
		PROTOCOLS("protocols"),
		CIPHER_SUITES("cipherSuites"),
		KEY_STORE_TYPE("keyStoreType"),
		KEY_STORE_PATH("keyStorePath"),
		KEY_STORE_PASSWORD("keyStorePassword"),
		CLIENT_AUTHENTICATION("clientAuthentication"),
		TRUST_STORE_TYPE("trustStoreType"),
		TRUST_STORE_PATH("trustStorePath"),
		TRUST_STORE_PASSWORD("trustStorePassword"),
		CONNECTION_LIMIT("connectionLimit");

		String name;
	}

	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum DefaultValue
	{
		HOST("0.0.0.0"),
		PORT("8080"),
		SSL_PORT("8443"),
		PATH("/"),
		KEYSTORE_TYPE(KeyStoreType.PKCS12.name()),
		KEYSTORE_FILE("dev/luin/file/server/core/keystore.p12"),
		KEYSTORE_PASSWORD("password");

		String value;
	}

	private static final String WEB_CONNECTOR_NAME = "web";
	private static final String SOAP_PATH = "/service";
	CommandLine cmd;

	public static Options addOptions(Options options)
	{
		options.addOption(Option.HOST.name,true,"set host [default: " + DefaultValue.HOST.value + "]");
		options.addOption(Option.PORT.name,true,"set port [default: <" + DefaultValue.PORT.value + "|" + DefaultValue.SSL_PORT.value + ">]");
		options.addOption(Option.PATH.name,true,"set path [default: " + DefaultValue.PATH.value + "]");
		options.addOption(Option.SSL.name,false,"enable SSL");
		options.addOption(Option.PROTOCOLS.name,true,"set SSL Protocols [default: " + NONE + "]");
		options.addOption(Option.CIPHER_SUITES.name,true,"set SSL CipherSuites [default: " + NONE + "]");
		options.addOption(Option.KEY_STORE_TYPE.name,true,"set keystore type [default: " + DefaultValue.KEYSTORE_TYPE.value + "]");
		options.addOption(Option.KEY_STORE_PATH.name,true,"set keystore path [default: " + DefaultValue.KEYSTORE_FILE.value + "]");
		options.addOption(Option.KEY_STORE_PASSWORD.name,true,"set keystore password [default: " + DefaultValue.KEYSTORE_PASSWORD.value + "]");
		options.addOption(Option.CLIENT_AUTHENTICATION.name,false,"enable SSL client authentication");
		options.addOption(Option.TRUST_STORE_TYPE.name,true,"set truststore type [default: " + DefaultValue.KEYSTORE_TYPE.value + "]");
		options.addOption(Option.TRUST_STORE_PATH.name,true,"set truststore path [default: " + NONE + "]");
		options.addOption(Option.TRUST_STORE_PASSWORD.name,true,"set truststore password [default: " + NONE + "]");
		options.addOption(Option.CONNECTION_LIMIT.name,true,"set connection limit [default: " + NONE + "]");
		return options;
	}

	public void init(Server server) throws IOException
	{
		val connector = isSSLEnabled()
				? createHttpsConnector(cmd,server,createSslContextFactory())
				: createHttpConnector(cmd,server);
		server.addConnector(connector);
		initConnectionLimit(server,connector);
	}

	private ServerConnector createHttpsConnector(CommandLine cmd, Server server, SslContextFactory sslContextFactory)
	{
		val httpConfig = new HttpConfiguration();
		httpConfig.setSendServerVersion(false);
		val result = new ServerConnector(server,sslContextFactory,new HttpConnectionFactory(httpConfig));
		result.setHost(cmd.getOptionValue(Option.HOST.name) == null ? DefaultValue.HOST.value : cmd.getOptionValue(Option.HOST.name));
		result.setPort(Integer.parseInt(cmd.getOptionValue(Option.PORT.name) == null ? DefaultValue.SSL_PORT.value : cmd.getOptionValue(Option.PORT.name)));
		result.setName(WEB_CONNECTOR_NAME);
		println("SOAP Service configured on https://" + getHost(result.getHost()) + ":" + result.getPort() + SOAP_PATH);
		return result;
	}

	String getPath(CommandLine cmd)
	{
		return cmd.getOptionValue(Option.PATH.name) == null ? DefaultValue.PATH.value : cmd.getOptionValue(Option.PATH.name);
	}

	private SslContextFactory createSslContextFactory() throws IOException
	{
		val result = new SslContextFactory.Server();
		addKeyStore(result);
		if (cmd.hasOption(Option.CLIENT_AUTHENTICATION.name))
			addTrustStore(result);
		result.setExcludeCipherSuites();
		return result;
	}

	private void addKeyStore(SslContextFactory sslContextFactory) throws IOException
	{
		val keyStoreType = cmd.getOptionValue(Option.KEY_STORE_TYPE.name, DefaultValue.KEYSTORE_TYPE.value);
		val keyStorePath = cmd.getOptionValue(Option.KEY_STORE_PATH.name,DefaultValue.KEYSTORE_FILE.value);
		val keyStorePassword = cmd.getOptionValue(Option.KEY_STORE_PASSWORD.name,DefaultValue.KEYSTORE_PASSWORD.value);
		val keyStore = getResource(keyStorePath);
		if (keyStore != null && keyStore.exists())
		{
			println("Using keyStore " + keyStore.getURI());
			val protocols = cmd.getOptionValue(Option.PROTOCOLS.name);
			if (!StringUtils.isEmpty(protocols))
				sslContextFactory.setIncludeProtocols(StringUtils.stripAll(StringUtils.split(protocols,',')));
			val cipherSuites = cmd.getOptionValue(Option.CIPHER_SUITES.name);
			if (!StringUtils.isEmpty(cipherSuites))
				sslContextFactory.setIncludeCipherSuites(StringUtils.stripAll(StringUtils.split(cipherSuites,',')));
			sslContextFactory.setKeyStoreType(keyStoreType);
			sslContextFactory.setKeyStoreResource(keyStore);
			sslContextFactory.setKeyStorePassword(keyStorePassword);
		}
		else
		{
			println("KeyStore " + keyStorePath + " not found!");
			exit(1);
		}
	}

	private void addTrustStore(SslContextFactory.Server sslContextFactory) throws IOException
	{
		val trustStoreType = cmd.getOptionValue(Option.TRUST_STORE_TYPE.name,DefaultValue.KEYSTORE_TYPE.value);
		val trustStorePath = cmd.getOptionValue(Option.TRUST_STORE_PATH.name);
		val trustStorePassword = cmd.getOptionValue(Option.TRUST_STORE_PASSWORD.name);
		val trustStore = getResource(trustStorePath);
		if (trustStore != null && trustStore.exists())
		{
			println("Using trustStore " + trustStore.getURI());
			sslContextFactory.setNeedClientAuth(true);
			sslContextFactory.setTrustStoreType(trustStoreType);
			sslContextFactory.setTrustStoreResource(trustStore);
			sslContextFactory.setTrustStorePassword(trustStorePassword);
		}
		else
		{
			println("Web Server not available: trustStore " + trustStorePath + " not found!");
			exit(1);
		}
	}

	private ServerConnector createHttpConnector(CommandLine cmd, Server server)
	{
		val httpConfig = new HttpConfiguration();
		httpConfig.setSendServerVersion(false);
		val result = new ServerConnector(server,new HttpConnectionFactory(httpConfig));
		result.setHost(cmd.getOptionValue(Option.HOST.name) == null ? DefaultValue.HOST.value : cmd.getOptionValue(Option.HOST.name));
		result.setPort(Integer.parseInt(cmd.getOptionValue(Option.PORT.name) == null ? DefaultValue.PORT.value : cmd.getOptionValue(Option.PORT.name)));
		result.setName(WEB_CONNECTOR_NAME);
		println("SOAP Service configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + SOAP_PATH);
		return result;
	}

	private void initConnectionLimit(Server server, final org.eclipse.jetty.server.ServerConnector connector)
	{
		if (cmd.hasOption(Option.CONNECTION_LIMIT.name))
			server.addBean(new ConnectionLimit(Integer.parseInt(cmd.getOptionValue(Option.CONNECTION_LIMIT.name)),connector));
	}

	public boolean isSSLEnabled()
	{
		return cmd.hasOption(Option.SSL.name);
	}

	public boolean isClientAuthenticationEnabled()
	{
		return cmd.hasOption(Option.CLIENT_AUTHENTICATION.name);
	}

	public String getSoapPath()
	{
		return SOAP_PATH;
	}

	public String getWebConnectorName()
	{
		return WEB_CONNECTOR_NAME;
	}

	public String getHost()
	{
		return cmd.getOptionValue(Option.HOST.name,DefaultValue.HOST.value);
	}
}
