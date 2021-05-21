package dev.luin.file.server.web;

import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.springframework.web.context.ContextLoaderListener;

import dev.luin.file.server.Config;
import dev.luin.file.server.SystemInterface;
import dev.luin.file.server.core.server.servlet.HealthServlet;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.val;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class HealthServer implements Config, SystemInterface
{
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum Option
	{
		HOST("host"),
		HEALTH("health"),
		HEALTH_PORT("healthPort");

		String name;
	}

	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum DefaultValue
	{
		HOST("0.0.0.0"),
		HEALTH_PORT("8008");

		String value;
	}

	private static final String HEALTH_PATH = "/health";
	String healthConnectorName;

	public static Options addOptions(Options result)
	{
		result.addOption(Option.HEALTH.name,false,"start health service");
		result.addOption(Option.HEALTH_PORT.name,true,"set health service port [default: " + DefaultValue.HEALTH_PORT.value + "]");
		return result;
	}

	public void init(CommandLine cmd, Server server) throws MalformedURLException, IOException
	{
		val connector = createHealthConnector(cmd, server, healthConnectorName);
		server.addConnector(connector);
	}

	private ServerConnector createHealthConnector(CommandLine cmd, Server server, String healthConnectorName)
	{
		val result = new ServerConnector(server);
		result.setHost(cmd.getOptionValue(Option.HOST.name,DefaultValue.HOST.value));
		result.setPort(Integer.parseInt(cmd.getOptionValue(Option.HEALTH_PORT.name,DefaultValue.HEALTH_PORT.value)));
		result.setName(healthConnectorName);
		println("Health service configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + HEALTH_PATH);
		return result;
	}

	public ServletContextHandler createHealthContextHandler(CommandLine cmd, ContextLoaderListener contextLoaderListener) throws Exception
	{
		val result = new ServletContextHandler(ServletContextHandler.SESSIONS);
		result.setVirtualHosts(new String[] {"@" + healthConnectorName});
		result.setInitParameter("configuration","deployment");
		result.setContextPath("/");
		result.addServlet(HealthServlet.class,HEALTH_PATH + "/*");
		return result;
	}

}
