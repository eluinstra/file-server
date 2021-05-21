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

	private static final String WEB_CONNECTOR_NAME = "web";
	private static final String SERVER_CONNECTOR_NAME = "server";
	private static final String HEALTH_CONNECTOR_NAME = "health";

	public static void main(String[] args) throws Exception
	{
		LogUtils.setLoggerClass(Slf4jLogger.class);
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
		new HsqlDb(SERVER_CONNECTOR_NAME).startHSQLDB(cmd,properties);
		new Jmx().init(cmd,server);
		try (val context = new AnnotationConfigWebApplicationContext())
		{
			registerConfig(context);
			val contextLoaderListener = new ContextLoaderListener(context);
			WebServer webServer = new WebServer(cmd,WEB_CONNECTOR_NAME);
			webServer.initWebServer(server);
			handlerCollection.addHandler(new WebAuthentication(cmd).createWebContextHandler(contextLoaderListener,webServer));
			FileServer fileServer = new FileServer(properties,SERVER_CONNECTOR_NAME);
			fileServer.initFileServer(server);
			handlerCollection.addHandler(fileServer.createFileServerContextHandler(contextLoaderListener));
			if (cmd.hasOption("health"))
			{
				val health = new HealthServer(HEALTH_CONNECTOR_NAME);
				health.init(cmd,server);
				handlerCollection.addHandler(health.createHealthContextHandler(cmd,contextLoaderListener));
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

	private Options createOptions()
	{
		val result = new Options();
		result.addOption(Option.HELP.name,false,"print this message");
		HealthServer.addOptions(result);
		WebServer.addOptions(result);
		WebAuthentication.addOptions(result);
		result.addOption(Option.CONFIG_DIR.name,true,"set config directory [default: <startup_directory>]");
		Jmx.addOptions(result);
		HsqlDb.addOptions(result);
		return result;
	}
	
	private void printUsage(Options options)
	{
		val formatter = new HelpFormatter();
		formatter.printHelp("Start",options,true);
		exit(0);
	}

	private void init(CommandLine cmd)
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

}
