package dev.luin.file.server.web;

import java.lang.management.ManagementFactory;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.val;
import lombok.experimental.FieldDefaults;

public class Jmx implements Config
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

	public static Options addOptions(Options result)
	{
		result.addOption(Option.JMX.name,false,"start JMX server");
		result.addOption(Option.JMX_PORT.name,true,"set JMX port [default: " + DefaultValue.JMS_PORT.value + "]");
		result.addOption(Option.JMX_ACCESS_FILE.name,true,"set JMX access file [default: " + NONE + "]");
		result.addOption(Option.JMX_PASSWORD_FILE.name,true,"set JMX password file [default: " + NONE + "]");
		return result;
	}

	public void init(CommandLine cmd, Server server) throws Exception
	{
		if (cmd.hasOption(Option.JMX.name))
		{
			System.out.println("Starting JMX Server...");
			val mBeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
			server.addBean(mBeanContainer);
			server.addBean(Log.getLog());
			val jmxURL = new JMXServiceURL("rmi",null,Integer.parseInt(cmd.getOptionValue(Option.JMX_PORT.name,DefaultValue.JMS_PORT.value)),"/jndi/rmi:///jmxrmi");
			//val sslContextFactory = cmd.hasOption(Option.SSL.name) ? createSslContextFactory(cmd,false) : null;
			val jmxServer = new ConnectorServer(jmxURL,createEnv(cmd),"org.eclipse.jetty.jmx:name=rmiconnectorserver");//,sslContextFactory);
			server.addBean(jmxServer);
			System.out.println("JMX Server configured on " + jmxURL);
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
