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

import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import javax.management.remote.JMXServiceURL;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.eclipse.jetty.jmx.ConnectorServer;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.log.Log;

import dev.luin.file.server.Config;
import dev.luin.file.server.SystemInterface;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.val;
import lombok.experimental.FieldDefaults;

public class Jmx implements Config, SystemInterface
{
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum Option
	{
		JMX("jmx"),
		JMX_PORT("jmxPort"),
		JMX_ACCESS_FILE("jmxAccessFile"),
		JMX_PASSWORD_FILE("jmxPasswordFile");

		String name;
	}

	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum DefaultValue
	{
		JMS_PORT("1999");

		String value;
	}

	public static Options addOptions(Options options)
	{
		options.addOption(Option.JMX.name,false,"start JMX server");
		options.addOption(Option.JMX_PORT.name,true,"set JMX port [default: " + DefaultValue.JMS_PORT.value + "]");
		options.addOption(Option.JMX_ACCESS_FILE.name,true,"set JMX access file [default: " + NONE + "]");
		options.addOption(Option.JMX_PASSWORD_FILE.name,true,"set JMX password file [default: " + NONE + "]");
		return options;
	}

	public void init(CommandLine cmd, Server server) throws NumberFormatException, MalformedURLException
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

	private static Map<String,Object> createEnv(CommandLine cmd)
	{
		val result = new HashMap<String, Object>();
		if (cmd.hasOption(Option.JMX_ACCESS_FILE.name) && cmd.hasOption(Option.JMX_PASSWORD_FILE.name))
		{
			result.put("jmx.remote.x.access.file",cmd.hasOption(Option.JMX_ACCESS_FILE.name));
			result.put("jmx.remote.x.password.file",cmd.hasOption(Option.JMX_PASSWORD_FILE.name));
		}
		return result;
	}
}
