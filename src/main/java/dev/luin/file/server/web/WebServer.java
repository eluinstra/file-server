package dev.luin.file.server.web;

import java.io.IOException;
import java.net.MalformedURLException;

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

	static final String SOAP_PATH = "/service";
	static final String NONE = "<none>";
	CommandLine cmd;
	@Getter
	String webConnectorName;

	public static Options addOptions(Options result)
	{
		result.addOption(Option.HOST.name,true,"set host [default: " + DefaultValue.HOST.value + "]");
		result.addOption(Option.PORT.name,true,"set port [default: <" + DefaultValue.PORT.value + "|" + DefaultValue.SSL_PORT.value + ">]");
		result.addOption(Option.PATH.name,true,"set path [default: " + DefaultValue.PATH.value + "]");
		result.addOption(Option.SSL.name,false,"enable SSL");
		result.addOption(Option.PROTOCOLS.name,true,"set SSL Protocols [default: " + NONE + "]");
		result.addOption(Option.CIPHER_SUITES.name,true,"set SSL CipherSuites [default: " + NONE + "]");
		result.addOption(Option.KEY_STORE_TYPE.name,true,"set keystore type [default: " + DefaultValue.KEYSTORE_TYPE.value + "]");
		result.addOption(Option.KEY_STORE_PATH.name,true,"set keystore path [default: " + DefaultValue.KEYSTORE_FILE.value + "]");
		result.addOption(Option.KEY_STORE_PASSWORD.name,true,"set keystore password [default: " + DefaultValue.KEYSTORE_PASSWORD.value + "]");
		result.addOption(Option.CLIENT_AUTHENTICATION.name,false,"enable SSL client authentication");
		result.addOption(Option.TRUST_STORE_TYPE.name,true,"set truststore type [default: " + DefaultValue.KEYSTORE_TYPE.value + "]");
		result.addOption(Option.TRUST_STORE_PATH.name,true,"set truststore path [default: " + NONE + "]");
		result.addOption(Option.TRUST_STORE_PASSWORD.name,true,"set truststore password [default: " + NONE + "]");
		result.addOption(Option.CONNECTION_LIMIT.name,true,"set connection limit [default: " + NONE + "]");
		return result;
	}

	public void initWebServer(Server server) throws MalformedURLException, IOException
	{
		val connector = isSSLEnabled(cmd) ? createHttpsConnector(cmd,server,createSslContextFactory(cmd)) : createHttpConnector(cmd,server);
		server.addConnector(connector);
		if (cmd.hasOption(Option.CONNECTION_LIMIT.name))
			server.addBean(new ConnectionLimit(Integer.parseInt(cmd.getOptionValue(Option.CONNECTION_LIMIT.name)),connector));
	}

	public ServerConnector createHttpConnector(CommandLine cmd, Server server)
	{
		val httpConfig = new HttpConfiguration();
		httpConfig.setSendServerVersion(false);
		val result = new ServerConnector(server,new HttpConnectionFactory(httpConfig));
		result.setHost(cmd.getOptionValue(Option.HOST.name) == null ? DefaultValue.HOST.value : cmd.getOptionValue(Option.HOST.name));
		result.setPort(Integer.parseInt(cmd.getOptionValue(Option.PORT.name) == null ? DefaultValue.PORT.value : cmd.getOptionValue(Option.PORT.name)));
		result.setName(webConnectorName);
		println("SOAP Service configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + SOAP_PATH);
		return result;
	}

	protected ServerConnector createHttpsConnector(CommandLine cmd, Server server, SslContextFactory factory)
	{
		val connector = new ServerConnector(server,factory);
		connector.setHost(cmd.getOptionValue(Option.HOST.name) == null ? DefaultValue.HOST.value : cmd.getOptionValue(Option.HOST.name));
		connector.setPort(Integer.parseInt(cmd.getOptionValue(Option.PORT.name) == null ? DefaultValue.SSL_PORT.value : cmd.getOptionValue(Option.PORT.name)));
		connector.setName(webConnectorName);
		println("SOAP Service configured on https://" + getHost(connector.getHost()) + ":" + connector.getPort() + SOAP_PATH);
		return connector;
	}

	protected String getPath(CommandLine cmd)
	{
		return cmd.getOptionValue(Option.PATH.name) == null ? DefaultValue.PATH.value : cmd.getOptionValue(Option.PATH.name);
	}

	public SslContextFactory createSslContextFactory(CommandLine cmd) throws MalformedURLException, IOException
	{
		val result = new SslContextFactory.Server();
		addKeyStore(cmd,result);
		if (cmd.hasOption(Option.CLIENT_AUTHENTICATION.name))
			addTrustStore(cmd,result);
		result.setExcludeCipherSuites();
		return result;
	}

	private void addKeyStore(CommandLine cmd, SslContextFactory sslContextFactory) throws MalformedURLException, IOException
	{
		val keyStoreType = cmd.getOptionValue(Option.KEY_STORE_TYPE.name, DefaultValue.KEYSTORE_TYPE.value);
		val keyStorePath = cmd.getOptionValue(Option.KEY_STORE_PATH.name,DefaultValue.KEYSTORE_FILE.value);
		val keyStorePassword = cmd.getOptionValue(Option.KEY_STORE_PASSWORD.name,DefaultValue.KEYSTORE_PASSWORD.value);
		val keyStore = getResource(keyStorePath);
		if (keyStore != null && keyStore.exists())
		{
			println("Using keyStore " + keyStore.getURI());
			String protocols = cmd.getOptionValue(Option.PROTOCOLS.name);
			if (!StringUtils.isEmpty(protocols))
				sslContextFactory.setIncludeProtocols(StringUtils.stripAll(StringUtils.split(protocols,',')));
			String cipherSuites = cmd.getOptionValue(Option.CIPHER_SUITES.name);
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

	private void addTrustStore(CommandLine cmd, SslContextFactory.Server sslContextFactory) throws MalformedURLException, IOException
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

	public boolean isSSLEnabled(CommandLine cmd)
	{
		return cmd.hasOption(Option.SSL.name);
	}

	public boolean isClientAuthenticationEnabled(CommandLine cmd)
	{
		return cmd.hasOption(Option.CLIENT_AUTHENTICATION.name);
	}
}