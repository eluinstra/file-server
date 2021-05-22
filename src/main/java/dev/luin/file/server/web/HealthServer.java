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
		HEALTH("health"),
		HEALTH_PORT("healthPort");

		String name;
	}

	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum DefaultValue
	{
		HEALTH_PORT("8008");

		String value;
	}

	private static final String HEALTH_CONNECTOR_NAME = "health";
	private static final String HEALTH_PATH = "/health";
	CommandLine cmd;
	WebServer webServer;

	public static Options addOptions(Options result)
	{
		result.addOption(Option.HEALTH.name,false,"start health service");
		result.addOption(Option.HEALTH_PORT.name,true,"set health service port [default: " + DefaultValue.HEALTH_PORT.value + "]");
		return result;
	}

	public void init(Server server) throws MalformedURLException, IOException
	{
		val connector = createHealthConnector(server);
		server.addConnector(connector);
	}

	private ServerConnector createHealthConnector(Server server)
	{
		val result = new ServerConnector(server);
		result.setHost(webServer.getHost());
		result.setPort(Integer.parseInt(cmd.getOptionValue(Option.HEALTH_PORT.name,DefaultValue.HEALTH_PORT.value)));
		result.setName(HEALTH_CONNECTOR_NAME);
		println("Health service configured on http://" + getHost(result.getHost()) + ":" + result.getPort() + HEALTH_PATH);
		return result;
	}

	public ServletContextHandler createContextHandler(ContextLoaderListener contextLoaderListener) throws Exception
	{
		val result = new ServletContextHandler(ServletContextHandler.SESSIONS);
		result.setVirtualHosts(new String[] {"@" + HEALTH_CONNECTOR_NAME});
		result.setInitParameter("configuration","deployment");
		result.setContextPath("/");
		result.addServlet(HealthServlet.class,HEALTH_PATH + "/*");
		return result;
	}

}
