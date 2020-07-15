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
package dev.luin.fs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.management.remote.JMXServiceURL;
import javax.servlet.DispatcherType;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.eclipse.jetty.jmx.ConnectorServer;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.ConnectionLimit;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.hsqldb.persist.HsqlProperties;
import org.hsqldb.server.ServiceProperties;
import org.hsqldb.server.ServerAcl.AclFormatException;
import org.hsqldb.server.ServerConfiguration;
import org.hsqldb.server.ServerConstants;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import dev.luin.fs.common.SecurityUtils;
import dev.luin.fs.core.KeyStoreManager.KeyStoreType;
import dev.luin.fs.web.configuration.JdbcURL;
import lombok.AccessLevel;
import lombok.val;
import lombok.var;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level=AccessLevel.PRIVATE, makeFinal=true)
public class Start
{
	String DEFAULT_KEYSTORE_TYPE = KeyStoreType.PKCS12.name();
	String DEFAULT_KEYSTORE_FILE = "keystore.p12";
	String DEFAULT_KEYSTORE_PASSWORD = "password";
	String REALM = "Realm";
	String REALM_FILE = "realm.properties";

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
		initWebServer(cmd,server);
		initFSServer(properties,server);
		initJMX(cmd,server);

		try (val context = new AnnotationConfigWebApplicationContext())
		{
			registerConfig(context);
			val contextLoaderListener = new ContextLoaderListener(context);
			if (cmd.hasOption("soap") || !cmd.hasOption("headless"))
				handlerCollection.addHandler(createWebContextHandler(cmd,contextLoaderListener));
			handlerCollection.addHandler(createFSContextHandler(properties,contextLoaderListener));
	
			System.out.println("Starting web server...");
	
			try
			{
				server.start();
			}
			catch (Exception e)
			{
				e.printStackTrace();
				server.stop();
				System.exit(1);
			}
			System.out.println("Web server started.");
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
		result.addOption("soap",false,"start soap service");
		result.addOption("headless",false,"start without web interface");
		return result;
	}
	
	protected void printUsage(Options options)
	{
		val formatter = new HelpFormatter();
		formatter.printHelp("Start",options,true);
		System.exit(0);
	}

	protected void init(CommandLine cmd)
	{
		val configDir = cmd.getOptionValue("configDir","");
		System.setProperty("fs.configDir",configDir);
		System.out.println("Using config directory: " + configDir);
	}

	protected void registerConfig(AnnotationConfigWebApplicationContext context)
	{
		context.register(AppConfig.class);
	}

	protected Map<String,String> getProperties(String...files) throws IOException
	{
		return AppConfig.PROPERTY_SOURCE.getProperties();
	}

	protected void startHSQLDB(CommandLine cmd, Map<String,String> properties) throws IOException, AclFormatException, URISyntaxException
	{
		val jdbcURL = initHSQLDB(cmd,properties);
		if (jdbcURL.isPresent())
		{
			System.out.println("Starting hsqldb...");
			val server = startHSQLDBServer(cmd,jdbcURL.get());
			initHSQLDBDatabase(server);
		}
	}

	protected Optional<JdbcURL> initHSQLDB(CommandLine cmd, Map<String,String> properties) throws IOException, AclFormatException, URISyntaxException
	{
		if ("org.hsqldb.jdbcDriver".equals(properties.get("fs.jdbc.driverClassName")) && cmd.hasOption("hsqldb"))
		{
			val jdbcURL = dev.luin.fs.web.configuration.Utils.parseJdbcURL(properties.get("fs.jdbc.url"),new JdbcURL());
			if (!jdbcURL.getHost().matches("(localhost|127.0.0.1)"))
			{
				System.out.println("Cannot start server on " + jdbcURL.getHost());
				System.exit(1);
			}
			return Optional.of(jdbcURL);
		}
		return Optional.empty();
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

	protected void initHSQLDBDatabase(org.hsqldb.server.Server server)
	{
		val url = "jdbc:hsqldb:hsql://localhost:" + server.getPort() + "/" + server.getDatabaseName(0,true);
		val user = "sa";
		val password = "";
		Location[] locations = {new Location("classpath:/dev/luin/fs/db/migration/hsqldb")};
		var config = Flyway.configure()
				.dataSource(url,user,password)
				.locations(locations)
				.ignoreMissingMigrations(true);
		config.load().migrate();
	}

	protected void initJMX(CommandLine cmd, Server server) throws Exception
	{
		if (cmd.hasOption("jmx"))
		{
			System.out.println("Starting jmx server...");
			val mBeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
			server.addBean(mBeanContainer);
			server.addBean(Log.getLog());
			val jmxURL = new JMXServiceURL("rmi",null,Integer.parseInt(cmd.getOptionValue("jmxPort","1999")),"/jndi/rmi:///jmxrmi");
			//val sslContextFactory = cmd.hasOption("ssl") ? createSslContextFactory(cmd,false) : null;
			val jmxServer = new ConnectorServer(jmxURL,createEnv(cmd),"org.eclipse.jetty.jmx:name=rmiconnectorserver");//,sslContextFactory);
			server.addBean(jmxServer);
			System.out.println("Jmx server configured on " + jmxURL);
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
		val result = new ServerConnector(server);
		result.setHost(cmd.getOptionValue("host") == null ? "0.0.0.0" : cmd.getOptionValue("host"));
		result.setPort(cmd.getOptionValue("port") == null ? 8080 : Integer.parseInt(cmd.getOptionValue("port")));
		result.setName("web");
		if (!cmd.hasOption("headless"))
			System.out.println("Web server configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + getPath(cmd));
		if (cmd.hasOption("soap"))
			System.out.println("SOAP service configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + "/service");
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
			System.out.println("Using keyStore " + keyStore.getURI());
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
			System.out.println("Web server not available: keyStore " + keyStorePath + " not found!");
			System.exit(1);
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
			System.out.println("Using trustStore " + trustStore.getURI());
			sslContextFactory.setNeedClientAuth(true);
			sslContextFactory.setTrustStoreType(trustStoreType);
			sslContextFactory.setTrustStoreResource(trustStore);
			sslContextFactory.setTrustStorePassword(trustStorePassword);
		}
		else
		{
			System.out.println("Web server not available: trustStore " + trustStorePath + " not found!");
			System.exit(1);
		}
	}

	protected ServerConnector createHttpsConnector(CommandLine cmd, Server server, SslContextFactory factory)
	{
		val connector = new ServerConnector(server,factory);
		connector.setHost(cmd.getOptionValue("host") == null ? "0.0.0.0" : cmd.getOptionValue("host"));
		connector.setPort(cmd.getOptionValue("port") == null ? 8443 : Integer.parseInt(cmd.getOptionValue("port")));
		connector.setName("web");
		if (!cmd.hasOption("headless"))
			System.out.println("Web server configured on https://" + getHost(connector.getHost()) + ":" + connector.getPort() + getPath(cmd));
		if (cmd.hasOption("soap"))
			System.out.println("SOAP service configured on https://" + getHost(connector.getHost()) + ":" + connector.getPort() + "/service");
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
				System.out.println("Configuring web server basic authentication:");
				val file = new File(REALM_FILE);
				if (file.exists())
					System.out.println("Using file " + file.getAbsoluteFile());
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
		if (cmd.hasOption("soap"))
			result.addServlet(org.apache.cxf.transport.servlet.CXFServlet.class,"/service/*");
		if (!cmd.hasOption("headless"))
		{
			val servletHolder = new ServletHolder(dev.luin.fs.web.ResourceServlet.class);
			result.addServlet(servletHolder,"/css/*");
			result.addServlet(servletHolder,"/fonts/*");
			result.addServlet(servletHolder,"/images/*");
			result.addServlet(servletHolder,"/js/*");
			result.addFilter(createWicketFilterHolder(),"/*",EnumSet.of(DispatcherType.REQUEST,DispatcherType.ERROR));
		}
		result.setErrorHandler(createErrorHandler());
		result.addEventListener(contextLoaderListener);
		return result;
	}

	protected void createRealmFile(File file) throws IOException, NoSuchAlgorithmException
	{
		val reader = new BufferedReader(new InputStreamReader(System.in));
		val username = readLine("enter username: ",reader);
		val password = readPassword(reader);
		System.out.println("Writing to file: " + file.getAbsoluteFile());
		FileUtils.writeStringToFile(file,username + ": " + password + ",user",Charset.defaultCharset(),false);
	}

	protected String readLine(String prompt, BufferedReader reader) throws IOException
	{
		return reader.lines().filter(l -> StringUtils.isNotBlank(l)).findFirst().orElse(null);
	}

	protected String readPassword(BufferedReader reader) throws IOException, NoSuchAlgorithmException
	{
		while (true)
		{
			val result = SecurityUtils.toMD5(readLine("enter password: ",reader));
			String password = SecurityUtils.toMD5(readLine("re-enter password: ",reader));
			if (!result.equals(password))
				System.out.println("Passwords do not match! Try again.");
			else
				return result;
		}
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
		val result = new FilterHolder(dev.luin.fs.core.server.servlet.ClientCertificateManagerFilter.class); 
		result.setInitParameter("x509CertificateHeader",cmd.getOptionValue("clientCertificateHeader"));
		return result;
	}

	protected FilterHolder createClientCertificateAuthenticationFilterHolder(CommandLine cmd) throws MalformedURLException, IOException
	{
		System.out.println("Configuring web server client certificate authentication:");
		val result = new FilterHolder(dev.luin.fs.core.service.servlet.ClientCertificateAuthenticationFilter.class); 
		val clientTrustStoreType = cmd.getOptionValue("clientTrustStoreType",DEFAULT_KEYSTORE_TYPE);
		val clientTrustStorePath = cmd.getOptionValue("clientTrustStorePath");
		val clientTrustStorePassword = cmd.getOptionValue("clientTrustStorePassword");
		val trustStore = getResource(clientTrustStorePath);
		System.out.println("Using clientTrustStore " + trustStore.getURI());
		if (trustStore != null && trustStore.exists())
		{
			result.setInitParameter("trustStoreType",clientTrustStoreType);
			result.setInitParameter("trustStorePath",clientTrustStorePath);
			result.setInitParameter("trustStorePassword",clientTrustStorePassword);
			return result;
		}
		else
		{
			System.out.println("Web server not available: clientTrustStore " + clientTrustStorePath + " not found!");
			System.exit(1);
			return null;
		}
	}

	protected FilterHolder createWicketFilterHolder()
	{
		val result = new FilterHolder(org.apache.wicket.protocol.http.WicketFilter.class); 
		result.setInitParameter("applicationFactoryClassName","org.apache.wicket.spring.SpringWebApplicationFactory");
		result.setInitParameter("applicationBean","wicketApplication");
		result.setInitParameter("filterMappingUrlPattern","/*");
		return result;
	}

	protected ErrorPageErrorHandler createErrorHandler()
	{
		val result = new ErrorPageErrorHandler();
		val errorPages = new HashMap<String,String>();
		errorPages.put("404","/404");
		result.setErrorPages(errorPages);
		return result;
	}

	protected void initFSServer(Map<String,String> properties, Server server) throws MalformedURLException, IOException
	{
		if (!"true".equals(properties.get("fs.ssl")))
		{
			server.addConnector(createFSHttpConnector(properties,server));
		}
		else
		{
			val factory = createFSSslContextFactory(properties);
			server.addConnector(createFSHttpsConnector(properties,server,factory));
		}
	}

	protected ServerConnector createFSHttpConnector(Map<String,String> properties, Server server)
	{
		val result = new ServerConnector(server);
		result.setHost(StringUtils.isEmpty(properties.get("fs.host")) ? "0.0.0.0" : properties.get("fs.host"));
		result.setPort(StringUtils.isEmpty(properties.get("fs.port"))  ? 8888 : Integer.parseInt(properties.get("fs.port")));
		result.setName("fs");
		System.out.println("FS Service configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + properties.get("fs.path"));
		return result;
	}

	protected SslContextFactory createFSSslContextFactory(Map<String,String> properties) throws MalformedURLException, IOException
	{
		val result = new SslContextFactory.Server();
		addFSKeyStore(properties,result);
		addFSTrustStore(properties,result);
		return result;
	}

	protected void addFSKeyStore(Map<String,String> properties, SslContextFactory.Server sslContextFactory) throws MalformedURLException, IOException
	{
		val keyStore = getResource(properties.get("keystore.path"));
		if (keyStore != null && keyStore.exists())
		{
			if (!StringUtils.isEmpty(properties.get("https.protocols")))
				sslContextFactory.setIncludeProtocols(StringUtils.stripAll(StringUtils.split(properties.get("https.protocols"),',')));
			if (!StringUtils.isEmpty(properties.get("https.cipherSuites")))
				sslContextFactory.setIncludeCipherSuites(StringUtils.stripAll(StringUtils.split(properties.get("https.cipherSuites"),',')));
			sslContextFactory.setKeyStoreType(properties.get("keystore.type"));
			sslContextFactory.setKeyStoreResource(keyStore);
			sslContextFactory.setKeyStorePassword(properties.get("keystore.password"));
		}
		else
		{
			System.out.println("FS Service not available: keystore " + properties.get("keystore.path") + " not found!");
			System.exit(1);
		}
	}

	protected void addFSTrustStore(Map<String,String> properties, SslContextFactory.Server sslContextFactory) throws MalformedURLException, IOException
	{
		val trustStore = getResource(properties.get("truststore.path"));
		if (trustStore != null && trustStore.exists())
		{
			sslContextFactory.setNeedClientAuth(true);
			sslContextFactory.setTrustStoreType(properties.get("truststore.type"));
			sslContextFactory.setTrustStoreResource(trustStore);
			sslContextFactory.setTrustStorePassword(properties.get("truststore.password"));
		}
		else
		{
			System.out.println("FS Service not available: truststore " + properties.get("truststore.path") + " not found!");
			System.exit(1);
		}
	}

	protected ServerConnector createFSHttpsConnector(Map<String,String> properties, Server server, SslContextFactory factory)
	{
		val result = new ServerConnector(server,factory);
		result.setHost(StringUtils.isEmpty(properties.get("fs.host")) ? "0.0.0.0" : properties.get("fs.host"));
		result.setPort(StringUtils.isEmpty(properties.get("fs.port"))  ? 8888 : Integer.parseInt(properties.get("fs.port")));
		result.setName("fs");
		System.out.println("FS Service configured on https://" + getHost(result.getHost()) + ":" + result.getPort() + properties.get("fs.path"));
		return result;
	}

	protected Handler createFSContextHandler(Map<String,String> properties, ContextLoaderListener contextLoaderListener)
	{
		val result = new ServletContextHandler(ServletContextHandler.SESSIONS);
		result.setVirtualHosts(new String[] {"@fs"});
		result.setContextPath("/");
		result.addFilter(createClientCertificateManagerFilterHolder(properties),"/*",EnumSet.allOf(DispatcherType.class));
		result.addServlet(dev.luin.fs.core.server.servlet.Download.class,properties.get("fs.path") + "/*");
		result.addEventListener(contextLoaderListener);
		return result;
	}

	protected FilterHolder createClientCertificateManagerFilterHolder(Map<String,String> properties)
	{
		val result = new FilterHolder(dev.luin.fs.core.server.servlet.ClientCertificateManagerFilter.class); 
		result.setInitParameter("x509CertificateHeader",properties.get("https.clientCertificateHeader"));
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
