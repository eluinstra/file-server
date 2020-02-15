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
package org.bitbucket.eluinstra.fs.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.management.remote.JMXServiceURL;
import javax.servlet.DispatcherType;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bitbucket.eluinstra.fs.core.KeyStoreManager.KeyStoreType;
import org.bitbucket.eluinstra.fs.service.common.SecurityUtils;
import org.bitbucket.eluinstra.fs.service.web.ExtensionProvider;
import org.bitbucket.eluinstra.fs.service.web.configuration.JdbcURL;
import org.eclipse.jetty.jmx.ConnectorServer;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
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
import org.hsqldb.persist.HsqlProperties;
import org.hsqldb.server.FSServiceProperties;
import org.hsqldb.server.ServerAcl.AclFormatException;
import org.hsqldb.server.ServerConfiguration;
import org.hsqldb.server.ServerConstants;
import org.hsqldb.server.ServerProperties;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.XmlWebApplicationContext;

public class Start
{
	protected final String DEFAULT_KEYSTORE_TYPE = KeyStoreType.PKCS12.name();
	protected final String DEFAULT_KEYSTORE_FILE = "keystore.p12";
	protected final String DEFAULT_KEYSTORE_PASSWORD = "password";
	protected final String REALM = "Realm";
	protected final String REALM_FILE = "realm.properties";
	protected Options options;
	protected CommandLine cmd;
	protected Server server = new Server();
	protected ContextHandlerCollection handlerCollection = new ContextHandlerCollection();
	protected Map<String,String> properties;

	public static void main(String[] args) throws Exception
	{
		Start app = new Start();
		app.options = app.createOptions();
		app.cmd = new DefaultParser().parse(app.options,args);

		if (app.cmd.hasOption("h"))
			app.printUsage();

		app.init(app.cmd);
		
		app.server.setHandler(app.handlerCollection);

		app.properties = app.getProperties("org/bitbucket/eluinstra/fs/service/applicationConfig.xml");

		app.startHSQLDB(app.cmd,app.properties);
		app.initWebServer(app.cmd,app.server);
		app.initFSServer(app.properties,app.server);
		app.initJMX(app.cmd,app.server);

		XmlWebApplicationContext context = new XmlWebApplicationContext();
		context.setConfigLocations(getConfigLocations("classpath:org/bitbucket/eluinstra/fs/service/applicationContext.xml"));
		ContextLoaderListener contextLoaderListener = new ContextLoaderListener(context);
		if (app.cmd.hasOption("soap") || !app.cmd.hasOption("headless"))
			app.handlerCollection.addHandler(app.createWebContextHandler(app.cmd,contextLoaderListener));
		app.handlerCollection.addHandler(app.createFSContextHandler(app.properties,contextLoaderListener));

		System.out.println("Starting web server...");

		try
		{
			app.server.start();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			app.server.stop();
			System.exit(1);
		}
		System.out.println("Web server started.");
		app.server.join();
	}

	protected Options createOptions()
	{
		Options result = new Options();
		result.addOption("h",false,"print this message");
		result.addOption("host",true,"set host");
		result.addOption("port",true,"set port");
		result.addOption("path",true,"set path");
		result.addOption("ssl",false,"use ssl");
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
		result.addOption("propertiesFilesDir",true,"set properties files directory (default=current dir)");
		result.addOption("jmx",false,"start mbean server");
		result.addOption("hsqldb",false,"start hsqldb server");
		result.addOption("hsqldbDir",true,"set hsqldb location (default: hsqldb)");
		result.addOption("soap",false,"start soap service");
		result.addOption("headless",false,"start without web interface");
		return result;
	}
	
	protected void printUsage()
	{
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("Start",options,true);
		System.exit(0);
	}

	protected void init(CommandLine cmd)
	{
		String propertiesFilesDir = cmd.getOptionValue("propertiesFilesDir","");
		System.setProperty("fs.propertiesFilesDir",propertiesFilesDir);
		System.out.println("Using properties files directory: " + propertiesFilesDir);
	}

	private Map<String,String> getProperties(String...files)
	{
		try (AbstractApplicationContext applicationContext = new ClassPathXmlApplicationContext(files))
		{
			PropertyPlaceholderConfigurer properties = (PropertyPlaceholderConfigurer)applicationContext.getBean("propertyConfigurer");
			return properties.getProperties();
		}
	}

	private void startHSQLDB(CommandLine cmd, Map<String,String> properties) throws IOException, AclFormatException, URISyntaxException
	{
		Optional<JdbcURL> jdbcURL = initHSQLDB(cmd,properties);
		if (jdbcURL.isPresent())
		{
			System.out.println("Starting hsqldb...");
			org.hsqldb.server.Server server = startHSQLDBServer(cmd,jdbcURL.get());
			initDatabase(server);
		}
	}

	private Optional<JdbcURL> initHSQLDB(CommandLine cmd, Map<String,String> properties) throws IOException, AclFormatException, URISyntaxException
	{
		if ("org.hsqldb.jdbcDriver".equals(properties.get("fs.jdbc.driverClassName")) && cmd.hasOption("hsqldb"))
		{
			JdbcURL jdbcURL = org.bitbucket.eluinstra.fs.service.web.configuration.Utils.parseJdbcURL(properties.get("fs.jdbc.url"),new JdbcURL());
			if (!jdbcURL.getHost().matches("(localhost|127.0.0.1)"))
			{
				System.out.println("Cannot start server on " + jdbcURL.getHost());
				System.exit(1);
			}
			return Optional.of(jdbcURL);
		}
		return Optional.empty();
	}

	public org.hsqldb.server.Server startHSQLDBServer(CommandLine cmd, JdbcURL jdbcURL) throws IOException, AclFormatException, URISyntaxException
	{
		List<String> options = new ArrayList<>();
		options.add("-database.0");
		options.add((cmd.hasOption("hsqldbDir") ? "file:" + cmd.getOptionValue("hsqldbDir") : "file:hsqldb") + "/" + jdbcURL.getDatabase());
		options.add("-dbname.0");
		options.add(jdbcURL.getDatabase());
		if (jdbcURL.getPort() != null)
		{
			options.add("-port");
			options.add(jdbcURL.getPort().toString());
		}
		HsqlProperties argProps = HsqlProperties.argArrayToProps(options.toArray(new String[0]),"server");
		ServerProperties props = new FSServiceProperties(ServerConstants.SC_PROTOCOL_HSQL);
		props.addProperties(argProps);
		ServerConfiguration.translateDefaultDatabaseProperty(props);
		ServerConfiguration.translateDefaultNoSystemExitProperty(props);
		ServerConfiguration.translateAddressProperty(props);
		org.hsqldb.server.Server server = new org.hsqldb.server.Server();
		server.setProperties(props);
		server.start();
		return server;
	}

	private void initDatabase(org.hsqldb.server.Server server)
	{
    try (Connection c = DriverManager.getConnection("jdbc:hsqldb:hsql://localhost:" + server.getPort() + "/" + server.getDatabaseName(0,true), "sa", ""))
		{
			if (!c.createStatement().executeQuery("select table_name from information_schema.tables where table_name = 'fs_client'").next())
			{
				c.createStatement().executeUpdate(IOUtils.toString(this.getClass().getResourceAsStream("/org/bitbucket/eluinstra/fs/service/database/hsqldb.create.sql"),Charset.defaultCharset()));
				System.out.println("FS tables created");
			}
			else
				System.out.println("FS tables already exist");
			ExtensionProvider.get().stream()
				.filter(p -> StringUtils.isNotEmpty(p.getHSQLDBFile()))
				.forEach(p -> executeSQLFile(c,p));
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private void executeSQLFile(Connection c, ExtensionProvider provider)
	{
		try
		{
			c.createStatement().executeUpdate(IOUtils.toString(this.getClass().getResourceAsStream(provider.getHSQLDBFile()),Charset.defaultCharset()));
			System.out.println(provider.getName() + " tables created");
		}
		catch (Exception e)
		{
			if (e.getMessage().contains("already exists"))
				System.out.println(provider.getName() + " tables already exist");
			else
				e.printStackTrace();
		}
	}

	protected void initJMX(CommandLine cmd, Server server) throws Exception
	{
		if (cmd.hasOption("jmx"))
		{
			System.out.println("Starting mbean server...");
			MBeanContainer mBeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
			server.addBean(mBeanContainer);
			server.addBean(Log.getLog());
			JMXServiceURL jmxURL = new JMXServiceURL("rmi",null,1999,"/jndi/rmi:///jmxrmi");
			ConnectorServer jmxServer = new ConnectorServer(jmxURL,"org.eclipse.jetty.jmx:name=rmiconnectorserver");
			server.addBean(jmxServer);
		}
	}

	protected static String[] getConfigLocations(String configLocation)
	{
		List<String> result = new ArrayList<>();
		result.add(0,configLocation);
		return result.toArray(new String[]{});
	}

	private void initWebServer(CommandLine cmd, Server server) throws MalformedURLException, IOException
	{
		if (!cmd.hasOption("ssl"))
		{
			server.addConnector(createHttpConnector(cmd));
		}
		else
		{
			SslContextFactory factory = createSslContextFactory(cmd);
			server.addConnector(createHttpsConnector(cmd,factory));
		}
	}

	private ServerConnector createHttpConnector(CommandLine cmd)
	{
		ServerConnector result = new ServerConnector(this.server);
		result.setHost(cmd.getOptionValue("host") == null ? "0.0.0.0" : cmd.getOptionValue("host"));
		result.setPort(cmd.getOptionValue("port") == null ? 8080 : Integer.parseInt(cmd.getOptionValue("port")));
		result.setName("web");
		if (!cmd.hasOption("headless"))
			System.out.println("Web server configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + getPath(cmd));
		if (cmd.hasOption("soap"))
			System.out.println("SOAP service configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + "/service");
		return result;
	}

	private SslContextFactory createSslContextFactory(CommandLine cmd) throws MalformedURLException, IOException
	{
		SslContextFactory result = new SslContextFactory();
		addKeyStore(result);
		if (cmd.hasOption("clientAuthentication"))
			addTrustStore(result);
		return result;
	}

	private void addKeyStore(SslContextFactory result) throws MalformedURLException, IOException
	{
		String keyStoreType = cmd.getOptionValue("keyStoreType",DEFAULT_KEYSTORE_TYPE);
		String keyStorePath = cmd.getOptionValue("keyStorePath",DEFAULT_KEYSTORE_FILE);
		String keyStorePassword = cmd.getOptionValue("keyStorePassword",DEFAULT_KEYSTORE_PASSWORD);
		Resource keyStore = getResource(keyStorePath);
		System.out.println("Using keyStore " + keyStore.getURI());
		if (keyStore != null && keyStore.exists())
		{
			result.setKeyStoreType(keyStoreType);
			result.setKeyStoreResource(keyStore);
			result.setKeyStorePassword(keyStorePassword);
		}
		else
		{
			System.out.println("Web server not available: keyStore " + keyStorePath + " not found!");
			System.exit(1);
		}
	}

	private void addTrustStore(SslContextFactory sslContextFactory) throws MalformedURLException, IOException
	{
		String trustStoreType = cmd.getOptionValue("trustStoreType",DEFAULT_KEYSTORE_TYPE);
		String trustStorePath = cmd.getOptionValue("trustStorePath");
		String trustStorePassword = cmd.getOptionValue("trustStorePassword");
		Resource trustStore = getResource(trustStorePath);
		System.out.println("Using trustStore " + trustStore.getURI());
		if (trustStore != null && trustStore.exists())
		{
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

	private ServerConnector createHttpsConnector(CommandLine cmd, SslContextFactory factory)
	{
		ServerConnector connector = new ServerConnector(this.server,factory);
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

	private Handler createWebContextHandler(CommandLine cmd, ContextLoaderListener contextLoaderListener) throws NoSuchAlgorithmException, IOException
	{
		ServletContextHandler result = new ServletContextHandler(ServletContextHandler.SESSIONS);
		result.setVirtualHosts(new String[] {"@web"});

		result.setInitParameter("configuration","deployment");

		result.setContextPath(getPath(cmd));
		if (cmd.hasOption("authentication"))
		{
			if (!cmd.hasOption("clientAuthentication"))
			{
				System.out.println("Configuring web server basic authentication:");
				File file = new File(REALM_FILE);
				if (file.exists())
					System.out.println("Using file " + file.getAbsoluteFile());
				else
					createRealmFile(file);
				result.setSecurityHandler(getSecurityHandler());
			}
			else if (cmd.hasOption("ssl") && cmd.hasOption("clientAuthentication"))
			{
				result.addFilter(createClientCertificateManagerFilterHolder(cmd),"/*",EnumSet.of(DispatcherType.REQUEST,DispatcherType.ERROR));
				result.addFilter(createClientCertificateAuthenticationFilterHolder(),"/*",EnumSet.of(DispatcherType.REQUEST,DispatcherType.ERROR));
			}
		}

		if (cmd.hasOption("soap"))
			result.addServlet(org.apache.cxf.transport.servlet.CXFServlet.class,"/service/*");

		if (!cmd.hasOption("headless"))
		{
			ServletHolder servletHolder = new ServletHolder(org.bitbucket.eluinstra.fs.service.web.ResourceServlet.class);
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
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		String username = readLine("enter username: ",reader);
		String password = readPassword(reader);
		System.out.println("Writing to file: " + file.getAbsoluteFile());
		FileUtils.writeStringToFile(file,username + ": " + password + ",user",Charset.defaultCharset(),false);
	}

	private String readLine(String prompt, BufferedReader reader) throws IOException
	{
		String result = null;
		while (StringUtils.isBlank(result))
		{
			System.out.print(prompt);
			result = reader.readLine();
		}
		return result;
	}

	private String readPassword(BufferedReader reader) throws IOException, NoSuchAlgorithmException
	{
		String result = null;
		while (true)
		{
			result = SecurityUtils.toMD5(readLine("enter password: ",reader));
			String password = SecurityUtils.toMD5(readLine("re-enter password: ",reader));
			if (!result.equals(password))
				System.out.println("Passwords do not match! Try again.");
			else
				break;
		}
		return result;
	}
	
	protected SecurityHandler getSecurityHandler()
	{
		ConstraintSecurityHandler result = new ConstraintSecurityHandler();

		Constraint constraint = new Constraint();
		constraint.setName("auth");
		constraint.setAuthenticate(true);
		constraint.setRoles(new String[]{"user","admin"});

		ConstraintMapping mapping = new ConstraintMapping();
		mapping.setPathSpec("/*");
		mapping.setConstraint(constraint);

		result.setConstraintMappings(Collections.singletonList(mapping));
		result.setAuthenticator(new BasicAuthenticator());
		result.setLoginService(new HashLoginService(REALM,REALM_FILE));

		return result;
	}

	private FilterHolder createClientCertificateManagerFilterHolder(CommandLine cmd)
	{
		FilterHolder result = new FilterHolder(org.bitbucket.eluinstra.fs.core.server.servlet.ClientCertificateManagerFilter.class); 
		result.setInitParameter("x509CertificateHeader",cmd.getOptionValue("clientCertificateHeader"));
		return result;
	}

	private FilterHolder createClientCertificateAuthenticationFilterHolder() throws MalformedURLException, IOException
	{
		System.out.println("Configuring web server client certificate authentication:");
		FilterHolder result = new FilterHolder(org.bitbucket.eluinstra.fs.core.service.servlet.ClientCertificateAuthenticationFilter.class); 
		String clientTrustStoreType = cmd.getOptionValue("clientTrustStoreType",DEFAULT_KEYSTORE_TYPE);
		String clientTrustStorePath = cmd.getOptionValue("clientTrustStorePath");
		String clientTrustStorePassword = cmd.getOptionValue("clientTrustStorePassword");
		Resource trustStore = getResource(clientTrustStorePath);
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

	private FilterHolder createWicketFilterHolder()
	{
		FilterHolder result = new FilterHolder(org.apache.wicket.protocol.http.WicketFilter.class); 
		result.setInitParameter("applicationClassName","org.bitbucket.eluinstra.fs.service.web.WicketApplication");
		result.setInitParameter("filterMappingUrlPattern","/*");
		return result;
	}

	private ErrorPageErrorHandler createErrorHandler()
	{
		ErrorPageErrorHandler result = new ErrorPageErrorHandler();
		Map<String,String> errorPages = new HashMap<>();
		errorPages.put("404","/404");
		result.setErrorPages(errorPages);
		return result;
	}

	private void initFSServer(Map<String,String> properties, Server server) throws MalformedURLException, IOException
	{
		if (!"true".equals(properties.get("fs.ssl")))
		{
			server.addConnector(createFSHttpConnector(properties));
		}
		else
		{
			SslContextFactory factory = createFSSslContextFactory(properties);
			server.addConnector(createFSHttpsConnector(properties,factory));
		}
	}

	private ServerConnector createFSHttpConnector(Map<String,String> properties)
	{
		ServerConnector result = new ServerConnector(this.server);
		result.setHost(StringUtils.isEmpty(properties.get("fs.host")) ? "0.0.0.0" : properties.get("fs.host"));
		result.setPort(StringUtils.isEmpty(properties.get("fs.port"))  ? 8888 : Integer.parseInt(properties.get("fs.port")));
		result.setName("fs");
		System.out.println("FS Service configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + properties.get("fs.path"));
		return result;
	}

	private SslContextFactory createFSSslContextFactory(Map<String,String> properties) throws MalformedURLException, IOException
	{
		SslContextFactory result = new SslContextFactory();
		addFSKeyStore(properties,result);
		if ("true".equals(properties.get("https.requireClientAuthentication")))
			addFSTrustStore(properties,result);
		return result;
	}

	private void addFSKeyStore(Map<String,String> properties, SslContextFactory sslContextFactory) throws MalformedURLException, IOException
	{
		Resource keyStore = getResource(properties.get("keystore.path"));
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

	private void addFSTrustStore(Map<String,String> properties, SslContextFactory sslContextFactory) throws MalformedURLException, IOException
	{
		Resource trustStore = getResource(properties.get("truststore.path"));
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

	private ServerConnector createFSHttpsConnector(Map<String,String> properties, SslContextFactory factory)
	{
		ServerConnector result = new ServerConnector(this.server,factory);
		result.setHost(StringUtils.isEmpty(properties.get("fs.host")) ? "0.0.0.0" : properties.get("fs.host"));
		result.setPort(StringUtils.isEmpty(properties.get("fs.port"))  ? 8888 : Integer.parseInt(properties.get("fs.port")));
		result.setName("fs");
		System.out.println("FS Service configured on https://" + result.getHost() + ":" + result.getPort() + properties.get("fs.path"));
		return result;
	}

	private Handler createFSContextHandler(Map<String,String> properties, ContextLoaderListener contextLoaderListener)
	{
		ServletContextHandler result = new ServletContextHandler(ServletContextHandler.SESSIONS);
		result.setVirtualHosts(new String[] {"@fs"});

		result.setContextPath("/");

		String clientCertificateAuthentication = properties.get("https.clientCertificateAuthentication");
		if (clientCertificateAuthentication != null && "true".equals(clientCertificateAuthentication.toLowerCase()))
			result.addFilter(createClientCertificateManagerFilterHolder(properties),"/*",EnumSet.allOf(DispatcherType.class));

		result.addServlet(org.bitbucket.eluinstra.fs.core.server.servlet.FSServlet.class,properties.get("fs.path"));

		result.addEventListener(contextLoaderListener);
		
		return result;
	}

	private FilterHolder createClientCertificateManagerFilterHolder(Map<String,String> properties)
	{
		FilterHolder result = new FilterHolder(org.bitbucket.eluinstra.fs.core.server.servlet.ClientCertificateManagerFilter.class); 
		result.setInitParameter("x509CertificateHeader",properties.get("https.clientCertificateHeader"));
		return result;
	}

	protected Resource getResource(String path) throws MalformedURLException, IOException
	{
		Resource result = Resource.newResource(path);
		return result.exists() ? result : Resource.newClassPathResource(path);
	}

	protected String getHost(String host)
	{
		return "0.0.0.0".equals(host) ? "localhost" : host;
	}

}
