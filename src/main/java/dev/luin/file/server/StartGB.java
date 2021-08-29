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
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.eclipse.jetty.server.Server;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import lombok.AccessLevel;
import lombok.val;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level=AccessLevel.PROTECTED, makeFinal=true)
public class StartGB extends Start
{
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
	
	private static void startService(final CommandLine cmd) throws Exception
	{
		val app = StartGB.of(cmd);
		app.startService();
	}

	public static Start of(CommandLine cmd) throws IOException
	{
		val properties = getProperties();
		val server = new Server();
		return new StartGB(cmd,properties,server);
	}

	private static Properties getProperties() throws IOException
	{
		return GBAppConfig.PROPERTY_SOURCE.getProperties();
	}

	private StartGB(CommandLine cmd, Properties properties, Server server)
	{
		super(cmd,properties,server);
	}

	@Override
	protected void registerConfig(AnnotationConfigWebApplicationContext context)
	{
		context.register(GBAppConfig.class);
	}
}
