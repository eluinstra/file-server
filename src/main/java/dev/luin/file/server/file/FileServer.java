package dev.luin.file.server.file;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.EnumSet;
import java.util.Properties;

import javax.servlet.DispatcherType;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.ConnectionLimit;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.web.context.ContextLoaderListener;

import dev.luin.file.server.Config;
import dev.luin.file.server.SystemInterface;
import dev.luin.file.server.core.server.download.http.DownloadServlet;
import dev.luin.file.server.core.server.servlet.ClientCertificateManagerFilter;
import dev.luin.file.server.core.server.upload.http.UploadServlet;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.val;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class FileServer implements Config, SystemInterface
{
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum ServerProperties
	{
		SERVER_HOST("server.host"),
		SERVER_PORT("server.port"),
		SERVER_PATH("server.path"),
		SERVER_SSL("server.ssl"),
		SERVER_PROTOCOLS("server.protocols"),
		SERVER_CIPHER_SUITES("server.cipherSuites"),
		KEYSTORE_TYPE("keystore.type"),
		KEYSTORE_PATH("keystore.path"),
		KEYSTORE_PASSWORD("keystore.password"),
		TRUSTSTORE_TYPE("truststore.type"),
		TRUSTSTORE_PATH("truststore.path"),
		TRUSTSTORE_PASSWORD("truststore.password"),
		SERVER_CLIENT_CERTIFICATE_HEADER("server.clientCertificateHeader"),
		SERVER_CONNECTION_LIMIT("server.connectionLimit");

		String name;
	}

	private static final String SERVER_CONNECTOR_NAME = "server";
	private static final String TRUE = "true";
	Properties properties;

	public void init(Server server) throws MalformedURLException, IOException
	{
		val connector = TRUE.equals(properties.getProperty(ServerProperties.SERVER_SSL.name))
				? createFileServerHttpsConnector(server,createFileServerSslContextFactory())
				: createFileServerHttpConnector(server);
		server.addConnector(connector);
		initConnectionLimit(server,connector);
	}

	private SslContextFactory createFileServerSslContextFactory() throws MalformedURLException, IOException
	{
		val result = new SslContextFactory.Server();
		addKeyStore(result);
		addFileServerTrustStore(result);
		return result;
	}

	private ServerConnector createFileServerHttpsConnector(Server server, SslContextFactory factory)
	{
		val result = new ServerConnector(server,factory);
		result.setHost(properties.getProperty(ServerProperties.SERVER_HOST.name));
		result.setPort(Integer.parseInt(properties.getProperty(ServerProperties.SERVER_PORT.name)));
		result.setName(SERVER_CONNECTOR_NAME);
		println("File Server configured on https://" + getHost(result.getHost()) + ":" + result.getPort() + properties.getProperty(ServerProperties.SERVER_PATH.name));
		return result;
	}

	private ServerConnector createFileServerHttpConnector(Server server)
	{
		val result = new ServerConnector(server);
		result.setHost(properties.getProperty(ServerProperties.SERVER_HOST.name));
		result.setPort(Integer.parseInt(properties.getProperty(ServerProperties.SERVER_PORT.name)));
		result.setName(SERVER_CONNECTOR_NAME);
		println("File Server configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + properties.getProperty(ServerProperties.SERVER_PATH.name));
		return result;
	}

	private void initConnectionLimit(Server server, final org.eclipse.jetty.server.ServerConnector connector)
	{
		if (properties.containsKey(ServerProperties.SERVER_CONNECTION_LIMIT.name))
			server.addBean(new ConnectionLimit(Integer.parseInt(properties.getProperty(ServerProperties.SERVER_CONNECTION_LIMIT.name)),connector));
	}

	public Handler createContextHandler(ContextLoaderListener contextLoaderListener)
	{
		val result = new ServletContextHandler(ServletContextHandler.SESSIONS);
		result.setVirtualHosts(new String[] {"@" + SERVER_CONNECTOR_NAME});
		result.setContextPath("/");
		result.addFilter(createClientCertificateManagerFilterHolder(),"/*",EnumSet.allOf(DispatcherType.class));
		result.addServlet(DownloadServlet.class,properties.getProperty(ServerProperties.SERVER_PATH.name) + "/download/*");
		result.addServlet(UploadServlet.class,properties.getProperty(ServerProperties.SERVER_PATH.name) + "/upload/*");
		result.addEventListener(contextLoaderListener);
		return result;
	}

	private void addKeyStore(SslContextFactory.Server sslContextFactory) throws MalformedURLException, IOException
	{
		val keyStore = getResource(properties.getProperty(ServerProperties.KEYSTORE_PATH.name));
		if (keyStore != null && keyStore.exists())
		{
			if (!StringUtils.isEmpty(properties.getProperty(ServerProperties.SERVER_PROTOCOLS.name)))
				sslContextFactory.setIncludeProtocols(StringUtils.stripAll(StringUtils.split(properties.getProperty(ServerProperties.SERVER_PROTOCOLS.name),',')));
			if (!StringUtils.isEmpty(properties.getProperty(ServerProperties.SERVER_CIPHER_SUITES.name)))
				sslContextFactory.setIncludeCipherSuites(StringUtils.stripAll(StringUtils.split(properties.getProperty(ServerProperties.SERVER_CIPHER_SUITES.name),',')));
			sslContextFactory.setKeyStoreType(properties.getProperty(ServerProperties.KEYSTORE_TYPE.name));
			sslContextFactory.setKeyStoreResource(keyStore);
			sslContextFactory.setKeyStorePassword(properties.getProperty(ServerProperties.KEYSTORE_PASSWORD.name));
		}
		else
		{
			println("KeyStore " + properties.getProperty(ServerProperties.KEYSTORE_PATH.name) + " not found!");
			exit(1);
		}
	}

	private void addFileServerTrustStore(SslContextFactory.Server sslContextFactory) throws MalformedURLException, IOException
	{
		val trustStore = getResource(properties.getProperty(ServerProperties.TRUSTSTORE_PATH.name));
		if (trustStore != null && trustStore.exists())
		{
			sslContextFactory.setNeedClientAuth(true);
			sslContextFactory.setTrustStoreType(properties.getProperty(ServerProperties.TRUSTSTORE_TYPE.name));
			sslContextFactory.setTrustStoreResource(trustStore);
			sslContextFactory.setTrustStorePassword(properties.getProperty(ServerProperties.TRUSTSTORE_PASSWORD.name));
		}
		else
		{
			println("File Server not available: truststore " + properties.getProperty(ServerProperties.TRUSTSTORE_PATH.name) + " not found!");
			exit(1);
		}
	}

	private FilterHolder createClientCertificateManagerFilterHolder()
	{
		val result = new FilterHolder(ClientCertificateManagerFilter.class); 
		result.setInitParameter("x509CertificateHeader",properties.getProperty(ServerProperties.SERVER_CLIENT_CERTIFICATE_HEADER.name));
		return result;
	}

}
