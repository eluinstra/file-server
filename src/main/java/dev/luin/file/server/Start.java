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
package dev.luin.file.server;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Scanner;

import javax.management.remote.JMXServiceURL;
import javax.servlet.DispatcherType;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.transport.servlet.CXFServlet;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.eclipse.jetty.jmx.ConnectorServer;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.ConnectionLimit;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.hsqldb.persist.HsqlProperties;
import org.hsqldb.server.ServerAcl.AclFormatException;
import org.hsqldb.server.ServerConfiguration;
import org.hsqldb.server.ServerConstants;
import org.hsqldb.server.ServiceProperties;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import dev.luin.file.server.core.KeyStoreManager.KeyStoreType;
import dev.luin.file.server.core.server.servlet.ClientCertificateManagerFilter;
import dev.luin.file.server.core.server.servlet.Download;
import dev.luin.file.server.core.server.servlet.Health;
import dev.luin.file.server.core.server.servlet.Upload;
import dev.luin.file.server.core.service.servlet.ClientCertificateAuthenticationFilter;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.val;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level=AccessLevel.PRIVATE, makeFinal=true)
public class Start implements SystemInterface
{
	String DEFAULT_KEYSTORE_TYPE = KeyStoreType.PKCS12.name();
	String DEFAULT_KEYSTORE_FILE = "keystore.p12";
	String DEFAULT_KEYSTORE_PASSWORD = "password";
	String REALM = "Realm";
	String REALM_FILE = "realm.properties";
	TextIO textIO = TextIoFactory.getTextIO();

	public static void main(String[] args) throws Exception
	{
		LogUtils.setLoggerClass(org.apache.cxf.common.logging.Slf4jLogger.class);
		val app = new Start();
		app.startService(args);
	}

	protected void startService(String[] args) throws ParseException, IOException, AclFormatException, URISyntaxException, MalformedURLException, Exception, NoSuchAlgorithmException, InterruptedException
	{
		val options = createOptions();
		val cmd = new DefaultParser().parse(options,args);
		if (cmd.hasOption("h"))
			printUsage(options);
		init(cmd);
		val server = new Server();
		val handlerCollection = new ContextHandlerCollection();
		server.setHandler(handlerCollection);
		val properties = getProperties();
		startHSQLDB(cmd,properties);
		initJMX(cmd,server);
		try (val context = new AnnotationConfigWebApplicationContext())
		{
			registerConfig(context);
			val contextLoaderListener = new ContextLoaderListener(context);
			initWebServer(cmd,server);
			handlerCollection.addHandler(createWebContextHandler(cmd,contextLoaderListener));
			initFileServer(properties,server);
			handlerCollection.addHandler(createFileServerContextHandler(properties,contextLoaderListener));
			if (cmd.hasOption("health"))
			{
				initHealthServer(cmd,server);
				handlerCollection.addHandler(createHealthContextHandler(cmd,contextLoaderListener));
			}
			println("Starting Server...");
	
			try
			{
				server.start();
			}
			catch (Exception e)
			{
				e.printStackTrace();
				server.stop();
				exit(1);
			}
			println("Server started.");
			server.join();
		}
	}

	protected Options createOptions()
	{
		val result = new Options();
		result.addOption("h",false,"print this message");
		result.addOption("host",true,"set host");
		result.addOption("port",true,"set port");
		result.addOption("path",true,"set path");
		result.addOption("health",false,"start health service");
		result.addOption("healthPort",true,"set health service port");
		result.addOption("connectionLimit",true,"set connection limit (default: none)");
		result.addOption("ssl",false,"use ssl");
		result.addOption("protocols",true,"set ssl protocols");
		result.addOption("cipherSuites",true,"set ssl cipherSuites");
		result.addOption("keyStoreType",true,"set keystore type (deault=" + DEFAULT_KEYSTORE_TYPE + ")");
		result.addOption("keyStorePath",true,"set keystore path");
		result.addOption("keyStorePassword",true,"set keystore password");
		result.addOption("clientAuthentication",false,"require ssl client authentication");
		result.addOption("clientCertificateHeader",true,"set client certificate header");
		result.addOption("trustStoreType",true,"set truststore type (deault=" + DEFAULT_KEYSTORE_TYPE + ")");
		result.addOption("trustStorePath",true,"set truststore path");
		result.addOption("trustStorePassword",true,"set truststore password");
		result.addOption("authentication",false,"use basic / client certificate authentication");
		result.addOption("clientTrustStoreType",true,"set client truststore type (deault=" + DEFAULT_KEYSTORE_TYPE + ")");
		result.addOption("clientTrustStorePath",true,"set client truststore path");
		result.addOption("clientTrustStorePassword",true,"set client truststore password");
		result.addOption("configDir",true,"set config directory (default=current dir)");
		result.addOption("jmx",false,"start mbean server");
		result.addOption("jmxPort",true,"set jmx port");
		result.addOption("jmxAccessFile",true,"set jmx access file");
		result.addOption("jmxPasswordFile",true,"set jmx password file");
		result.addOption("hsqldb",false,"start hsqldb server");
		result.addOption("hsqldbDir",true,"set hsqldb location (default: hsqldb)");
		return result;
	}
	
	protected void printUsage(Options options)
	{
		val formatter = new HelpFormatter();
		formatter.printHelp("Start",options,true);
		exit(0);
	}

	protected void init(CommandLine cmd)
	{
		val configDir = cmd.getOptionValue("configDir","");
		setProperty("server.configDir",configDir);
		println("Using config directory: " + configDir);
	}

	protected void registerConfig(AnnotationConfigWebApplicationContext context)
	{
		context.register(AppConfig.class);
	}

	protected Properties getProperties(String...files) throws IOException
	{
		return AppConfig.PROPERTY_SOURCE.getProperties();
	}

	protected void startHSQLDB(CommandLine cmd, Properties properties) throws IOException, AclFormatException, URISyntaxException
	{
		val jdbcURL = getHsqlDbJdbcUrl(cmd,properties);
		if (jdbcURL.isPresent())
		{
			println("Starting HSQLDB Server...");
			startHSQLDBServer(cmd,jdbcURL.get());
		}
	}

	protected Optional<JdbcURL> getHsqlDbJdbcUrl(CommandLine cmd, Properties properties) throws IOException, AclFormatException, URISyntaxException
	{
		if ("org.hsqldb.jdbcDriver".equals(properties.getProperty("jdbc.driverClassName")) && cmd.hasOption("hsqldb"))
		{
			val jdbcURL = parseJdbcURL(properties.getProperty("jdbc.url"),new JdbcURL());
			if (!jdbcURL.getHost().matches("(localhost|127.0.0.1)"))
			{
				println("Cannot start HSQLDB Server on " + jdbcURL.getHost());
				exit(1);
			}
			return Optional.of(jdbcURL);
		}
		return Optional.empty();
	}

	private JdbcURL parseJdbcURL(@NonNull final String jdbcURL, @NonNull final JdbcURL model) throws MalformedURLException
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

	protected org.hsqldb.server.Server startHSQLDBServer(CommandLine cmd, JdbcURL jdbcURL) throws IOException, AclFormatException, URISyntaxException
	{
		val options = new ArrayList<>();
		options.add("-database.0");
		options.add((cmd.hasOption("hsqldbDir") ? "file:" + cmd.getOptionValue("hsqldbDir") : "file:hsqldb") + "/" + jdbcURL.getDatabase());
		options.add("-dbname.0");
		options.add(jdbcURL.getDatabase());
		if (jdbcURL.getPort() != null)
		{
			options.add("-port");
			options.add(jdbcURL.getPort().toString());
		}
		val argProps = HsqlProperties.argArrayToProps(options.toArray(new String[0]),"server");
		val props = new ServiceProperties(ServerConstants.SC_PROTOCOL_HSQL);
		props.addProperties(argProps);
		ServerConfiguration.translateDefaultDatabaseProperty(props);
		ServerConfiguration.translateDefaultNoSystemExitProperty(props);
		ServerConfiguration.translateAddressProperty(props);
		val server = new org.hsqldb.server.Server();
		server.setProperties(props);
		server.start();
		return server;
	}

	protected void initJMX(CommandLine cmd, Server server) throws Exception
	{
		if (cmd.hasOption("jmx"))
		{
			println("Starting JMX Server...");
			val mBeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
			server.addBean(mBeanContainer);
			server.addBean(Log.getLog());
			val jmxURL = new JMXServiceURL("rmi",null,Integer.parseInt(cmd.getOptionValue("jmxPort","1999")),"/jndi/rmi:///jmxrmi");
			//val sslContextFactory = cmd.hasOption("ssl") ? createSslContextFactory(cmd,false) : null;
			val jmxServer = new ConnectorServer(jmxURL,createEnv(cmd),"org.eclipse.jetty.jmx:name=rmiconnectorserver");//,sslContextFactory);
			server.addBean(jmxServer);
			println("JMX Server configured on " + jmxURL);
		}
	}

	private Map<String,Object> createEnv(CommandLine cmd)
	{
		val result = new HashMap<String, Object>();
		if (cmd.hasOption("jmxAccessFile") && cmd.hasOption("jmxPasswordFile"))
		{
			result.put("jmx.remote.x.access.file",cmd.hasOption("jmxAccessFile"));
			result.put("jmx.remote.x.password.file",cmd.hasOption("jmxPasswordFile"));
		}
		return result;
	}

	protected void initWebServer(CommandLine cmd, Server server) throws MalformedURLException, IOException
	{
		val connector = cmd.hasOption("ssl") ? createHttpsConnector(cmd,server,createSslContextFactory(cmd)) : createHttpConnector(cmd,server);
		server.addConnector(connector);
		if (cmd.hasOption("connectionLimit"))
			server.addBean(new ConnectionLimit(Integer.parseInt(cmd.getOptionValue("connectionLimit")),connector));
	}

	protected ServerConnector createHttpConnector(CommandLine cmd, Server server)
	{
		val httpConfig = new HttpConfiguration();
		httpConfig.setSendServerVersion(false);
		val result = new ServerConnector(server,new HttpConnectionFactory(httpConfig));
		result.setHost(cmd.getOptionValue("host") == null ? "0.0.0.0" : cmd.getOptionValue("host"));
		result.setPort(cmd.getOptionValue("port") == null ? 8080 : Integer.parseInt(cmd.getOptionValue("port")));
		result.setName("web");
		println("SOAP Service configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + "/service");
		return result;
	}

	protected SslContextFactory createSslContextFactory(CommandLine cmd) throws MalformedURLException, IOException
	{
		val result = new SslContextFactory.Server();
		addKeyStore(cmd,result);
		if (cmd.hasOption("clientAuthentication"))
			addTrustStore(cmd,result);
		result.setExcludeCipherSuites();
		return result;
	}

	protected void addKeyStore(CommandLine cmd, SslContextFactory sslContextFactory) throws MalformedURLException, IOException
	{
		val keyStoreType = cmd.getOptionValue("keyStoreType",DEFAULT_KEYSTORE_TYPE);
		val keyStorePath = cmd.getOptionValue("keyStorePath",DEFAULT_KEYSTORE_FILE);
		val keyStorePassword = cmd.getOptionValue("keyStorePassword",DEFAULT_KEYSTORE_PASSWORD);
		val keyStore = getResource(keyStorePath);
		if (keyStore != null && keyStore.exists())
		{
			println("Using keyStore " + keyStore.getURI());
			String protocols = cmd.getOptionValue("protocols");
			if (!StringUtils.isEmpty(protocols))
				sslContextFactory.setIncludeProtocols(StringUtils.stripAll(StringUtils.split(protocols,',')));
			String cipherSuites = cmd.getOptionValue("cipherSuites");
			if (!StringUtils.isEmpty(cipherSuites))
				sslContextFactory.setIncludeCipherSuites(StringUtils.stripAll(StringUtils.split(cipherSuites,',')));
			sslContextFactory.setKeyStoreType(keyStoreType);
			sslContextFactory.setKeyStoreResource(keyStore);
			sslContextFactory.setKeyStorePassword(keyStorePassword);
		}
		else
		{
			println("Web Server not available: keyStore " + keyStorePath + " not found!");
			exit(1);
		}
	}

	protected void addTrustStore(CommandLine cmd, SslContextFactory.Server sslContextFactory) throws MalformedURLException, IOException
	{
		val trustStoreType = cmd.getOptionValue("trustStoreType",DEFAULT_KEYSTORE_TYPE);
		val trustStorePath = cmd.getOptionValue("trustStorePath");
		val trustStorePassword = cmd.getOptionValue("trustStorePassword");
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

	protected ServerConnector createHttpsConnector(CommandLine cmd, Server server, SslContextFactory factory)
	{
		val connector = new ServerConnector(server,factory);
		connector.setHost(cmd.getOptionValue("host") == null ? "0.0.0.0" : cmd.getOptionValue("host"));
		connector.setPort(cmd.getOptionValue("port") == null ? 8443 : Integer.parseInt(cmd.getOptionValue("port")));
		connector.setName("web");
		println("SOAP Service configured on https://" + getHost(connector.getHost()) + ":" + connector.getPort() + "/service");
		return connector;
	}

	protected String getPath(CommandLine cmd)
	{
		return cmd.getOptionValue("path") == null ? "/" : cmd.getOptionValue("path");
	}

	protected Handler createWebContextHandler(CommandLine cmd, ContextLoaderListener contextLoaderListener) throws NoSuchAlgorithmException, IOException
	{
		val result = new ServletContextHandler(ServletContextHandler.SESSIONS);
		result.setVirtualHosts(new String[] {"@web"});
		result.setInitParameter("configuration","deployment");
		result.setContextPath(getPath(cmd));
		if (cmd.hasOption("authentication"))
		{
			if (!cmd.hasOption("clientAuthentication"))
			{
				println("Configuring Web Server basic authentication:");
				val file = new File(REALM_FILE);
				if (file.exists())
					println("Using file " + file.getAbsoluteFile());
				else
					createRealmFile(file);
				result.setSecurityHandler(getSecurityHandler());
			}
			else if (cmd.hasOption("ssl") && cmd.hasOption("clientAuthentication"))
			{
				result.addFilter(createClientCertificateManagerFilterHolder(cmd),"/*",EnumSet.of(DispatcherType.REQUEST,DispatcherType.ERROR));
				result.addFilter(createClientCertificateAuthenticationFilterHolder(cmd),"/*",EnumSet.of(DispatcherType.REQUEST,DispatcherType.ERROR));
			}
		}
		result.addServlet(CXFServlet.class,"/service/*");
		result.setErrorHandler(createErrorHandler());
		result.addEventListener(contextLoaderListener);
		return result;
	}

	protected void createRealmFile(File file) throws IOException, NoSuchAlgorithmException
	{
		val username = textIO.newStringInputReader()
				.withDefaultValue("admin")
				.read("enter username");
		val password = readPassword();
		println("Writing to file: " + file.getAbsoluteFile());
		FileUtils.writeStringToFile(file,username + ": " + password + ",user",Charset.defaultCharset(),false);
	}

	private String readPassword() throws IOException, NoSuchAlgorithmException
	{
		val reader = textIO.newStringInputReader()
				.withMinLength(8)
				.withInputMasking(true);
		while (true)
		{
			val result = toMD5(reader.read("enter password"));
			val password = toMD5(reader.read("re-enter password"));
			if (result.equals(password))
				return result;
			else
				println("Passwords don't match! Try again.");
		}
	}
	
	private String toMD5(String s) throws NoSuchAlgorithmException, UnsupportedEncodingException
	{
		return "MD5:" + DigestUtils.md5Hex(s);
	}

	protected SecurityHandler getSecurityHandler()
	{
		val result = new ConstraintSecurityHandler();
		val constraint = createSecurityConstraint();
		val mapping = createSecurityConstraintMapping(constraint);
		result.setConstraintMappings(Collections.singletonList(mapping));
		result.setAuthenticator(new BasicAuthenticator());
		result.setLoginService(new HashLoginService(REALM,REALM_FILE));
		return result;
	}

	protected Constraint createSecurityConstraint()
	{
		val constraint = new Constraint();
		constraint.setName("auth");
		constraint.setAuthenticate(true);
		constraint.setRoles(new String[]{"user","admin"});
		return constraint;
	}

	protected ConstraintMapping createSecurityConstraintMapping(final Constraint constraint)
	{
		val mapping = new ConstraintMapping();
		mapping.setPathSpec("/*");
		mapping.setConstraint(constraint);
		return mapping;
	}

	protected FilterHolder createClientCertificateManagerFilterHolder(CommandLine cmd)
	{
		val result = new FilterHolder(ClientCertificateManagerFilter.class); 
		result.setInitParameter("x509CertificateHeader",cmd.getOptionValue("clientCertificateHeader"));
		return result;
	}

	protected FilterHolder createClientCertificateAuthenticationFilterHolder(CommandLine cmd) throws MalformedURLException, IOException
	{
		println("Configuring Web Server client certificate authentication:");
		val result = new FilterHolder(ClientCertificateAuthenticationFilter.class); 
		val clientTrustStoreType = cmd.getOptionValue("clientTrustStoreType",DEFAULT_KEYSTORE_TYPE);
		val clientTrustStorePath = cmd.getOptionValue("clientTrustStorePath");
		val clientTrustStorePassword = cmd.getOptionValue("clientTrustStorePassword");
		val trustStore = getResource(clientTrustStorePath);
		println("Using clientTrustStore " + trustStore.getURI());
		if (trustStore != null && trustStore.exists())
		{
			result.setInitParameter("trustStoreType",clientTrustStoreType);
			result.setInitParameter("trustStorePath",clientTrustStorePath);
			result.setInitParameter("trustStorePassword",clientTrustStorePassword);
			return result;
		}
		else
		{
			println("Web Server not available: clientTrustStore " + clientTrustStorePath + " not found!");
			exit(1);
			return null;
		}
	}

	protected ErrorPageErrorHandler createErrorHandler()
	{
		val result = new ErrorPageErrorHandler();
		val errorPages = new HashMap<String,String>();
		errorPages.put("404","/404");
		result.setErrorPages(errorPages);
		return result;
	}

	protected void initFileServer(Properties properties, Server server) throws MalformedURLException, IOException
	{
		if (!"true".equals(properties.getProperty("server.ssl")))
		{
			server.addConnector(createFileServerHttpConnector(properties,server));
		}
		else
		{
			val factory = createFileServerSslContextFactory(properties);
			server.addConnector(createFileServerHttpsConnector(properties,server,factory));
		}
	}

	protected ServerConnector createFileServerHttpConnector(Properties properties, Server server)
	{
		val result = new ServerConnector(server);
		result.setHost(StringUtils.isEmpty(properties.getProperty("server.host")) ? "0.0.0.0" : properties.getProperty("server.host"));
		result.setPort(StringUtils.isEmpty(properties.getProperty("server.port"))  ? 8888 : Integer.parseInt(properties.getProperty("server.port")));
		result.setName("server");
		println("File Server configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + properties.getProperty("server.path"));
		return result;
	}

	protected SslContextFactory createFileServerSslContextFactory(Properties properties) throws MalformedURLException, IOException
	{
		val result = new SslContextFactory.Server();
		addFileServerKeyStore(properties,result);
		addFileServerTrustStore(properties,result);
		return result;
	}

	protected void addFileServerKeyStore(Properties properties, SslContextFactory.Server sslContextFactory) throws MalformedURLException, IOException
	{
		val keyStore = getResource(properties.getProperty("keystore.path"));
		if (keyStore != null && keyStore.exists())
		{
			if (!StringUtils.isEmpty(properties.getProperty("server.protocols")))
				sslContextFactory.setIncludeProtocols(StringUtils.stripAll(StringUtils.split(properties.getProperty("server.protocols"),',')));
			if (!StringUtils.isEmpty(properties.getProperty("server.cipherSuites")))
				sslContextFactory.setIncludeCipherSuites(StringUtils.stripAll(StringUtils.split(properties.getProperty("server.cipherSuites"),',')));
			sslContextFactory.setKeyStoreType(properties.getProperty("keystore.type"));
			sslContextFactory.setKeyStoreResource(keyStore);
			sslContextFactory.setKeyStorePassword(properties.getProperty("keystore.password"));
		}
		else
		{
			println("File Server not available: keystore " + properties.getProperty("keystore.path") + " not found!");
			exit(1);
		}
	}

	protected void addFileServerTrustStore(Properties properties, SslContextFactory.Server sslContextFactory) throws MalformedURLException, IOException
	{
		val trustStore = getResource(properties.getProperty("truststore.path"));
		if (trustStore != null && trustStore.exists())
		{
			sslContextFactory.setNeedClientAuth(true);
			sslContextFactory.setTrustStoreType(properties.getProperty("truststore.type"));
			sslContextFactory.setTrustStoreResource(trustStore);
			sslContextFactory.setTrustStorePassword(properties.getProperty("truststore.password"));
		}
		else
		{
			println("File Server not available: truststore " + properties.getProperty("truststore.path") + " not found!");
			exit(1);
		}
	}

	protected ServerConnector createFileServerHttpsConnector(Properties properties, Server server, SslContextFactory factory)
	{
		val result = new ServerConnector(server,factory);
		result.setHost(StringUtils.isEmpty(properties.getProperty("server.host")) ? "0.0.0.0" : properties.getProperty("server.host"));
		result.setPort(StringUtils.isEmpty(properties.getProperty("server.port"))  ? 8888 : Integer.parseInt(properties.getProperty("server.port")));
		result.setName("server");
		println("File Server configured on https://" + getHost(result.getHost()) + ":" + result.getPort() + properties.getProperty("server.path"));
		return result;
	}

	protected Handler createFileServerContextHandler(Properties properties, ContextLoaderListener contextLoaderListener)
	{
		val result = new ServletContextHandler(ServletContextHandler.SESSIONS);
		result.setVirtualHosts(new String[] {"@server"});
		result.setContextPath("/");
		result.addFilter(createClientCertificateManagerFilterHolder(properties),"/*",EnumSet.allOf(DispatcherType.class));
		result.addServlet(Download.class,properties.getProperty("server.path") + "/download/*");
		result.addServlet(Upload.class,properties.getProperty("server.path") + "/upload/*");
		result.addEventListener(contextLoaderListener);
		return result;
	}

	protected FilterHolder createClientCertificateManagerFilterHolder(Properties properties)
	{
		val result = new FilterHolder(ClientCertificateManagerFilter.class); 
		result.setInitParameter("x509CertificateHeader",properties.getProperty("server.clientCertificateHeader"));
		return result;
	}

	protected void initHealthServer(CommandLine cmd, Server server) throws MalformedURLException, IOException
	{
		val connector = createHealthConnector(cmd, server);
		server.addConnector(connector);
	}

	private ServerConnector createHealthConnector(CommandLine cmd, Server server)
	{
		val result = new ServerConnector(server);
		result.setHost(cmd.getOptionValue("host","0.0.0.0"));
		result.setPort(Integer.parseInt(cmd.getOptionValue("healthPort","8008")));
		result.setName("health");
		println("Health service configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + "/health");
		return result;
	}

	protected ServletContextHandler createHealthContextHandler(CommandLine cmd, ContextLoaderListener contextLoaderListener) throws Exception
	{
		val result = new ServletContextHandler(ServletContextHandler.SESSIONS);
		result.setVirtualHosts(new String[] {"@health"});
		result.setInitParameter("configuration","deployment");
		result.setContextPath("/");
		result.addServlet(Health.class,"/health/*");
		return result;
	}

	protected Resource getResource(String path) throws MalformedURLException, IOException
	{
		val result = Resource.newResource(path);
		return result.exists() ? result : Resource.newClassPathResource(path);
	}

	protected String getHost(String host)
	{
		return "0.0.0.0".equals(host) ? "localhost" : host;
	}

}
