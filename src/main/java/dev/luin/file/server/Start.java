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
import dev.luin.file.server.core.server.download.http.DownloadServlet;
import dev.luin.file.server.core.server.servlet.ClientCertificateAuthenticationFilter;
import dev.luin.file.server.core.server.servlet.ClientCertificateManagerFilter;
import dev.luin.file.server.core.server.servlet.HealthServlet;
import dev.luin.file.server.core.server.upload.http.UploadServlet;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level=AccessLevel.PRIVATE, makeFinal=true)
public class Start implements SystemInterface
{
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum Option
	{
		HELP("h"),
		HOST("host"),
		PORT("port"),
		PATH("path"),
		HEALTH("health"),
		HEALTH_PORT("healthPort"),
		CONNECTION_LIMIT("connectionLimit"),
		SSL("ssl"),
		PROTOCOLS("protocols"),
		CIPHER_SUITES("cipherSuites"),
		KEY_STORE_TYPE("keyStoreType"),
		KEY_STORE_PATH("keyStorePath"),
		KEY_STORE_PASSWORD("keyStorePassword"),
		CLIENT_AUTHENTICATION("clientAuthentication"),
		CLIENT_CERTIFICATE_HEADER("clientCertificateHeader"),
		TRUST_STORE_TYPE("trustStoreType"),
		TRUST_STORE_PATH("trustStorePath"),
		TRUST_STORE_PASSWORD("trustStorePassword"),
		AUTHENTICATION("authentication"),
		CLIENT_TRUST_STORE_TYPE("clientTrustStoreType"),
		CLIENT_TRUST_STORE_PATH("clientTrustStorePath"),
		CLIENT_TRUST_STORE_PASSWORD("clientTrustStorePassword"),
		CONFIG_DIR("configDir"),
		JMX("jmx"),
		JMX_PORT("jmxPort"),
		JMX_ACCESS_FILE("jmxAccessFile"),
		JMX_PASSWORD_FILE("jmxPasswordFile"),
		HSQLDB("hsqldb"),
		HSQLDB_DIR("hsqldbDir");
		
		String name;
	}

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
		SERVER_CLIENT_CERTIFICATE_HEADER("server.clientCertificateHeader"),
		KEYSTORE_TYPE("keystore.type"),
		KEYSTORE_PATH("keystore.path"),
		KEYSTORE_PASSWORD("keystore.password"),
		TRUSTSTORE_TYPE("truststore.type"),
		TRUSTSTORE_PATH("truststore.path"),
		TRUSTSTORE_PASSWORD("truststore.password");
		
		String name;
	}

	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum DefaultValue
	{
		HOST("0.0.0.0"),
		PORT("8080"),
		SERVER_PORT("8888"),
		SSL_PORT("8443"),
		PATH("/"),
		HEALTH_PORT("8008"),
		JMS_PORT("1999"),
		KEYSTORE_TYPE(KeyStoreType.PKCS12.name()),
		KEYSTORE_FILE("dev/luin/file/server/core/keystore.p12"),
		KEYSTORE_PASSWORD("password"),
		CONFIG_DIR(""),
		HSQLDB_DIR("hsqldb");
		
		String value;
	}

	private static final String WEB_CONNECTOR_NAME = "web";
	private static final String HEALTH_CONNECTOR_NAME = "health";
	private static final String SERVER_CONNECTOR_NAME = "server";
	private static final String SOAP_PATH = "/service";
	private static final String HEALTH_PATH = "/health";
	private static final String NONE = "<none>";
	private static final String TRUE = "true";
	private static final String REALM = "Realm";
	private static final String REALM_FILE = "realm.properties";

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
		server.addBean(new CustomErrorHandler());
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
		result.addOption(Option.HELP.name,false,"print this message");
		result.addOption(Option.HOST.name,true,"set host [default: " + DefaultValue.HOST.value + "]");
		result.addOption(Option.PORT.name,true,"set port [default: <" + DefaultValue.PORT.value + "|" + DefaultValue.SSL_PORT.value + ">]");
		result.addOption(Option.PATH.name,true,"set path [default: " + DefaultValue.PATH.value + "]");
		result.addOption(Option.HEALTH.name,false,"start health service");
		result.addOption(Option.HEALTH_PORT.name,true,"set health service port [default: " + DefaultValue.HEALTH_PORT.value + "]");
		result.addOption(Option.CONNECTION_LIMIT.name,true,"set connection limit [default: " + NONE + "]");
		result.addOption(Option.SSL.name,false,"enable SSL");
		result.addOption(Option.PROTOCOLS.name,true,"set SSL Protocols [default: " + NONE + "]");
		result.addOption(Option.CIPHER_SUITES.name,true,"set SSL CipherSuites [default: " + NONE + "]");
		result.addOption(Option.KEY_STORE_TYPE.name,true,"set keystore type [default: " + DefaultValue.KEYSTORE_TYPE.value + "]");
		result.addOption(Option.KEY_STORE_PATH.name,true,"set keystore path [default: " + DefaultValue.KEYSTORE_FILE.value + "]");
		result.addOption(Option.KEY_STORE_PASSWORD.name,true,"set keystore password [default: " + DefaultValue.KEYSTORE_PASSWORD.value + "]");
		result.addOption(Option.CLIENT_AUTHENTICATION.name,false,"enable SSL client authentication");
		result.addOption(Option.CLIENT_CERTIFICATE_HEADER.name,true,"set client certificate header [default: " + NONE + "]");
		result.addOption(Option.TRUST_STORE_TYPE.name,true,"set truststore type [default: " + DefaultValue.KEYSTORE_TYPE.value + "]");
		result.addOption(Option.TRUST_STORE_PATH.name,true,"set truststore path [default: " + NONE + "]");
		result.addOption(Option.TRUST_STORE_PASSWORD.name,true,"set truststore password [default: " + NONE + "]");
		result.addOption(Option.AUTHENTICATION.name,false,"enable basic | client certificate authentication");
		result.addOption(Option.CLIENT_TRUST_STORE_TYPE.name,true,"set client truststore type [default: " + DefaultValue.KEYSTORE_TYPE.value + "]");
		result.addOption(Option.CLIENT_TRUST_STORE_PATH.name,true,"set client truststore path [default: " + NONE + "]");
		result.addOption(Option.CLIENT_TRUST_STORE_PASSWORD.name,true,"set client truststore password [default: " + NONE + "]");
		result.addOption(Option.CONFIG_DIR.name,true,"set config directory [default: <startup_directory>]");
		result.addOption(Option.JMX.name,false,"start JMX server");
		result.addOption(Option.JMX_PORT.name,true,"set JMX port [default: " + DefaultValue.JMS_PORT.value + "]");
		result.addOption(Option.JMX_ACCESS_FILE.name,true,"set JMX access file [default: " + NONE + "]");
		result.addOption(Option.JMX_PASSWORD_FILE.name,true,"set JMX password file [default: " + NONE + "]");
		result.addOption(Option.HSQLDB.name,false,"start HSQLDB server");
		result.addOption(Option.HSQLDB_DIR.name,true,"set HSQLDB location [default: " + DefaultValue.HSQLDB_DIR.value + "]");
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
		val configDir = cmd.getOptionValue(Option.CONFIG_DIR.name,"");
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
		if ("org.hsqldb.jdbcDriver".equals(properties.getProperty("jdbc.driverClassName")) && cmd.hasOption(Option.HSQLDB.name))
		{
			val jdbcURL = parseJdbcURL(properties.getProperty("jdbc.url"),new JdbcURL());
			val allowedHosts = "localhost|127.0.0.1";
			if (!jdbcURL.getHost().matches("^(" + allowedHosts + ")$"))
			{
				println("Cannot start HSQLDB Server on " + jdbcURL.getHost() + ". Use " + allowedHosts + " instead.");
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
		options.add((cmd.hasOption(Option.HSQLDB_DIR.name) ? "file:" + cmd.getOptionValue(Option.HSQLDB_DIR.name) : "file:" + DefaultValue.HSQLDB_DIR.value) + "/" + jdbcURL.getDatabase());
		options.add("-dbname.0");
		options.add(jdbcURL.getDatabase());
		if (jdbcURL.getPort() != null)
		{
			options.add("-port");
			options.add(jdbcURL.getPort().toString());
		}
		val argProps = HsqlProperties.argArrayToProps(options.toArray(new String[0]),SERVER_CONNECTOR_NAME);
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
		if (cmd.hasOption(Option.JMX.name))
		{
			println("Starting JMX Server...");
			val mBeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
			server.addBean(mBeanContainer);
			server.addBean(Log.getLog());
			val jmxURL = new JMXServiceURL("rmi",null,Integer.parseInt(cmd.getOptionValue(Option.JMX_PORT.name,DefaultValue.JMS_PORT.value)),"/jndi/rmi:///jmxrmi");
			//val sslContextFactory = cmd.hasOption(Option.SSL.name) ? createSslContextFactory(cmd,false) : null;
			val jmxServer = new ConnectorServer(jmxURL,createEnv(cmd),"org.eclipse.jetty.jmx:name=rmiconnectorserver");//,sslContextFactory);
			server.addBean(jmxServer);
			println("JMX Server configured on " + jmxURL);
		}
	}

	private Map<String,Object> createEnv(CommandLine cmd)
	{
		val result = new HashMap<String, Object>();
		if (cmd.hasOption(Option.JMX_ACCESS_FILE.name) && cmd.hasOption(Option.JMX_PASSWORD_FILE.name))
		{
			result.put("jmx.remote.x.access.file",cmd.hasOption(Option.JMX_ACCESS_FILE.name));
			result.put("jmx.remote.x.password.file",cmd.hasOption(Option.JMX_PASSWORD_FILE.name));
		}
		return result;
	}

	protected void initWebServer(CommandLine cmd, Server server) throws MalformedURLException, IOException
	{
		val connector = cmd.hasOption(Option.SSL.name) ? createHttpsConnector(cmd,server,createSslContextFactory(cmd)) : createHttpConnector(cmd,server);
		server.addConnector(connector);
		if (cmd.hasOption(Option.CONNECTION_LIMIT.name))
			server.addBean(new ConnectionLimit(Integer.parseInt(cmd.getOptionValue(Option.CONNECTION_LIMIT.name)),connector));
	}

	protected ServerConnector createHttpConnector(CommandLine cmd, Server server)
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

	protected SslContextFactory createSslContextFactory(CommandLine cmd) throws MalformedURLException, IOException
	{
		val result = new SslContextFactory.Server();
		addKeyStore(cmd,result);
		if (cmd.hasOption(Option.CLIENT_AUTHENTICATION.name))
			addTrustStore(cmd,result);
		result.setExcludeCipherSuites();
		return result;
	}

	protected void addKeyStore(CommandLine cmd, SslContextFactory sslContextFactory) throws MalformedURLException, IOException
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
			println("Web Server not available: keyStore " + keyStorePath + " not found!");
			exit(1);
		}
	}

	protected void addTrustStore(CommandLine cmd, SslContextFactory.Server sslContextFactory) throws MalformedURLException, IOException
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

	protected ServerConnector createHttpsConnector(CommandLine cmd, Server server, SslContextFactory factory)
	{
		val connector = new ServerConnector(server,factory);
		connector.setHost(cmd.getOptionValue(Option.HOST.name) == null ? DefaultValue.HOST.value : cmd.getOptionValue(Option.HOST.name));
		connector.setPort(Integer.parseInt(cmd.getOptionValue(Option.PORT.name) == null ? DefaultValue.SSL_PORT.value : cmd.getOptionValue(Option.PORT.name)));
		connector.setName(WEB_CONNECTOR_NAME);
		println("SOAP Service configured on https://" + getHost(connector.getHost()) + ":" + connector.getPort() + SOAP_PATH);
		return connector;
	}

	protected String getPath(CommandLine cmd)
	{
		return cmd.getOptionValue(Option.PATH.name) == null ? DefaultValue.PATH.value : cmd.getOptionValue(Option.PATH.name);
	}

	protected Handler createWebContextHandler(CommandLine cmd, ContextLoaderListener contextLoaderListener) throws NoSuchAlgorithmException, IOException
	{
		val result = new ServletContextHandler(ServletContextHandler.SESSIONS);
		result.setVirtualHosts(new String[] {"@" + WEB_CONNECTOR_NAME});
		result.setInitParameter("configuration","deployment");
		result.setContextPath(getPath(cmd));
		if (cmd.hasOption(Option.AUTHENTICATION.name))
		{
			if (!cmd.hasOption(Option.CLIENT_AUTHENTICATION.name))
			{
				println("Configuring Web Server basic authentication:");
				val file = new File(REALM_FILE);
				if (file.exists())
					println("Using file " + file.getAbsoluteFile());
				else
					createRealmFile(file);
				result.setSecurityHandler(getSecurityHandler());
			}
			else if (cmd.hasOption(Option.SSL.name) && cmd.hasOption(Option.CLIENT_AUTHENTICATION.name))
			{
				result.addFilter(createClientCertificateManagerFilterHolder(cmd),"/*",EnumSet.of(DispatcherType.REQUEST,DispatcherType.ERROR));
				result.addFilter(createClientCertificateAuthenticationFilterHolder(cmd),"/*",EnumSet.of(DispatcherType.REQUEST,DispatcherType.ERROR));
			}
		}
		result.addServlet(CXFServlet.class,SOAP_PATH + "/*");
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
		result.setInitParameter("x509CertificateHeader",cmd.getOptionValue(Option.CLIENT_CERTIFICATE_HEADER.name));
		return result;
	}

	protected FilterHolder createClientCertificateAuthenticationFilterHolder(CommandLine cmd) throws MalformedURLException, IOException
	{
		println("Configuring Web Server client certificate authentication:");
		val result = new FilterHolder(ClientCertificateAuthenticationFilter.class); 
		val clientTrustStoreType = cmd.getOptionValue(Option.CLIENT_TRUST_STORE_TYPE.name,DefaultValue.KEYSTORE_TYPE.value);
		val clientTrustStorePath = cmd.getOptionValue(Option.CLIENT_TRUST_STORE_PATH.name);
		val clientTrustStorePassword = cmd.getOptionValue(Option.CLIENT_TRUST_STORE_PASSWORD.name);
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
		if (!TRUE.equals(properties.getProperty(ServerProperties.SERVER_SSL.name)))
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
		result.setHost(StringUtils.isEmpty(properties.getProperty(ServerProperties.SERVER_HOST.name)) ? DefaultValue.HOST.value : properties.getProperty(ServerProperties.SERVER_HOST.name));
		result.setPort(Integer.parseInt(StringUtils.isEmpty(properties.getProperty(ServerProperties.SERVER_PORT.name))  ? DefaultValue.SERVER_PORT.value : properties.getProperty(ServerProperties.SERVER_PORT.name)));
		result.setName(SERVER_CONNECTOR_NAME);
		println("File Server configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + properties.getProperty(ServerProperties.SERVER_PATH.name));
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
			println("File Server not available: keystore " + properties.getProperty(ServerProperties.KEYSTORE_PATH.name) + " not found!");
			exit(1);
		}
	}

	protected void addFileServerTrustStore(Properties properties, SslContextFactory.Server sslContextFactory) throws MalformedURLException, IOException
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

	protected ServerConnector createFileServerHttpsConnector(Properties properties, Server server, SslContextFactory factory)
	{
		val result = new ServerConnector(server,factory);
		result.setHost(StringUtils.isEmpty(properties.getProperty(ServerProperties.SERVER_HOST.name)) ? DefaultValue.HOST.value : properties.getProperty(ServerProperties.SERVER_HOST.name));
		result.setPort(StringUtils.isEmpty(properties.getProperty(ServerProperties.SERVER_PORT.name))  ? 8888 : Integer.parseInt(properties.getProperty(ServerProperties.SERVER_PORT.name)));
		result.setName(SERVER_CONNECTOR_NAME);
		println("File Server configured on https://" + getHost(result.getHost()) + ":" + result.getPort() + properties.getProperty(ServerProperties.SERVER_PATH.name));
		return result;
	}

	protected Handler createFileServerContextHandler(Properties properties, ContextLoaderListener contextLoaderListener)
	{
		val result = new ServletContextHandler(ServletContextHandler.SESSIONS);
		result.setVirtualHosts(new String[] {"@" + SERVER_CONNECTOR_NAME});
		result.setContextPath("/");
		result.addFilter(createClientCertificateManagerFilterHolder(properties),"/*",EnumSet.allOf(DispatcherType.class));
		result.addServlet(DownloadServlet.class,properties.getProperty(ServerProperties.SERVER_PATH.name) + "/download/*");
		result.addServlet(UploadServlet.class,properties.getProperty(ServerProperties.SERVER_PATH.name) + "/upload/*");
		result.addEventListener(contextLoaderListener);
		return result;
	}

	protected FilterHolder createClientCertificateManagerFilterHolder(Properties properties)
	{
		val result = new FilterHolder(ClientCertificateManagerFilter.class); 
		result.setInitParameter("x509CertificateHeader",properties.getProperty(ServerProperties.SERVER_CLIENT_CERTIFICATE_HEADER.name));
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
		result.setHost(cmd.getOptionValue(Option.HOST.name,DefaultValue.HOST.value));
		result.setPort(Integer.parseInt(cmd.getOptionValue(Option.HEALTH_PORT.name,DefaultValue.HEALTH_PORT.value)));
		result.setName(HEALTH_CONNECTOR_NAME);
		println("Health service configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + HEALTH_PATH);
		return result;
	}

	protected ServletContextHandler createHealthContextHandler(CommandLine cmd, ContextLoaderListener contextLoaderListener) throws Exception
	{
		val result = new ServletContextHandler(ServletContextHandler.SESSIONS);
		result.setVirtualHosts(new String[] {"@" + HEALTH_CONNECTOR_NAME});
		result.setInitParameter("configuration","deployment");
		result.setContextPath("/");
		result.addServlet(HealthServlet.class,HEALTH_PATH + "/*");
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
