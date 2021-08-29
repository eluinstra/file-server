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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.logging.Slf4jLogger;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.hsqldb.server.ServerAcl.AclFormatException;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import dev.luin.file.server.file.FileServer;
import dev.luin.file.server.web.HealthServer;
import dev.luin.file.server.web.HsqlDb;
import dev.luin.file.server.web.Jmx;
import dev.luin.file.server.web.WebAuthentication;
import dev.luin.file.server.web.WebServer;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.val;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level=AccessLevel.PRIVATE, makeFinal=true)
@AllArgsConstructor
public class Start implements SystemInterface
{
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum Option
	{
		HELP("h"),
		CONFIG_DIR("configDir");

		String name;
	}

	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum DefaultValue
	{
		CONFIG_DIR("");

		String value;
	}

	CommandLine cmd;
	Properties properties;
	Server server;

	public static void main(String[] args) throws Exception
	{
		initLogger();
		val options = createOptions();
		val cmd = createCmd(args,options);
		if (showUsage(cmd))
			printUsage(options);
		else
			startService(cmd);
	}

	protected static void initLogger()
	{
		LogUtils.setLoggerClass(Slf4jLogger.class);
	}

	protected static Options createOptions()
	{
		val result = new Options();
		Start.addOptions(result);
		WebServer.addOptions(result);
		WebAuthentication.addOptions(result);
		HsqlDb.addOptions(result);
		Jmx.addOptions(result);
		HealthServer.addOptions(result);
		return result;
	}

	private static void addOptions(final Options options)
	{
		options.addOption(Option.HELP.name,false,"print this message");
		options.addOption(Option.CONFIG_DIR.name,true,"set config directory [default: <startup_directory>]");
	}
	
	protected static CommandLine createCmd(String[] args, final Options options) throws ParseException
	{
		return new DefaultParser().parse(options,args);
	}

	protected static boolean showUsage(CommandLine cmd)
	{
		return cmd.hasOption("h");
	}

	protected static void printUsage(Options options)
	{
		val formatter = new HelpFormatter();
		formatter.printHelp("Start",options,true);
	}

	private static void startService(final CommandLine cmd) throws Exception
	{
		val app = Start.of(cmd);
		app.startService();
	}

	public static Start of(CommandLine cmd) throws ParseException, IOException
	{
		val properties = getProperties();
		val server = new Server();
		return new Start(cmd,properties,server);
	}

	private static Properties getProperties() throws IOException
	{
		return AppConfig.PROPERTY_SOURCE.getProperties();
	}

	protected void startService() throws Exception
	{
		initConfig();
		initServer();
		startServer();
	}

	private void initConfig()
	{
		val configDir = cmd.getOptionValue(Option.CONFIG_DIR.name,DefaultValue.CONFIG_DIR.value);
		setProperty("server.configDir",configDir);
		println("Using config directory: " + (StringUtils.isEmpty(configDir) ? "." : configDir));
	}

	private void initServer() throws IOException, AclFormatException, URISyntaxException, NoSuchAlgorithmException
	{
		val handlerCollection = createHandlerCollection();
		initErrorHandler();
		initHsqlDb();
		initJmx();
		try (val context = new AnnotationConfigWebApplicationContext())
		{
			registerConfig(context);
			val contextLoaderListener = new ContextLoaderListener(context);
			val webServer = initWebServer(handlerCollection,contextLoaderListener);
			initFileServer(handlerCollection,contextLoaderListener);
			initHealthServer(handlerCollection,webServer);
		}
	}

	private ContextHandlerCollection createHandlerCollection()
	{
		val handlerCollection = new ContextHandlerCollection();
		server.setHandler(handlerCollection);
		return handlerCollection;
	}

	private void initErrorHandler()
	{
		server.addBean(new CustomErrorHandler());
	}

	private void initHsqlDb() throws IOException, AclFormatException, URISyntaxException
	{
		new HsqlDb().startHSQLDB(cmd,properties);
	}

	private void initJmx() throws NumberFormatException, MalformedURLException
	{
		new Jmx().init(cmd,server);
	}

	protected void registerConfig(AnnotationConfigWebApplicationContext context)
	{
		context.register(AppConfig.class);
	}

	private WebServer initWebServer(final ContextHandlerCollection handlerCollection, final ContextLoaderListener contextLoaderListener) throws IOException, NoSuchAlgorithmException
	{
		WebServer webServer = new WebServer(cmd);
		webServer.init(server);
		handlerCollection.addHandler(new WebAuthentication(cmd,webServer).createContextHandler(contextLoaderListener));
		return webServer;
	}

	private void initFileServer(final ContextHandlerCollection handlerCollection, final ContextLoaderListener contextLoaderListener) throws IOException
	{
		FileServer fileServer = new FileServer(properties);
		fileServer.init(server);
		handlerCollection.addHandler(fileServer.createContextHandler(contextLoaderListener));
	}

	private void initHealthServer(final ContextHandlerCollection handlerCollection, WebServer webServer) throws IOException
	{
		if (cmd.hasOption(HealthServer.getHealthOption()))
		{
			val health = new HealthServer(cmd,webServer);
			health.init(server);
			handlerCollection.addHandler(health.createContextHandler());
		}
	}

	private void startServer() throws Exception
	{
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
