package dev.luin.file.server.web;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Properties;
import java.util.Scanner;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.hsqldb.persist.HsqlProperties;
import org.hsqldb.server.ServerConfiguration;
import org.hsqldb.server.ServerConstants;
import org.hsqldb.server.ServiceProperties;
import org.hsqldb.server.Server;
import org.hsqldb.server.ServerAcl.AclFormatException;

import dev.luin.file.server.JdbcURL;
import dev.luin.file.server.SystemInterface;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class HsqlDb implements SystemInterface
{
	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum Option
	{
		HSQLDB("hsqldb"),
		HSQLDB_DIR("hsqldbDir");

		String name;
	}

	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	private enum DefaultValue
	{
		HSQLDB_DIR("hsqldb");

		String value;
	}

	String serverConnectorName;

	public static Options addOptions(Options result)
	{
		result.addOption(Option.HSQLDB.name,false,"start HSQLDB server");
		result.addOption(Option.HSQLDB_DIR.name,true,"set HSQLDB location [default: " + DefaultValue.HSQLDB_DIR.value + "]");
		return result;
	}

	public void startHSQLDB(CommandLine cmd, Properties properties) throws IOException, AclFormatException, URISyntaxException
	{
		if ("org.hsqldb.jdbcDriver".equals(properties.getProperty("jdbc.driverClassName")) && cmd.hasOption(Option.HSQLDB.name))
		{
			val jdbcURL = getHsqlDbJdbcUrl(cmd,properties);
			if (jdbcURL.isPresent())
			{
				println("Starting HSQLDB Server...");
				startHSQLDBServer(cmd,jdbcURL.get());
			}
			else
				exit(1);
		}
	}

	private Optional<JdbcURL> getHsqlDbJdbcUrl(CommandLine cmd, Properties properties) throws IOException, AclFormatException, URISyntaxException
	{
		val jdbcURL = parseJdbcURL(properties.getProperty("jdbc.url"),new JdbcURL());
		val allowedHosts = "localhost|127.0.0.1";
		if (!jdbcURL.getHost().matches("^(" + allowedHosts + ")$"))
		{
			println("Cannot start HSQLDB Server on " + jdbcURL.getHost() + ". Use " + allowedHosts + " instead.");
			return Optional.empty();
		}
		return Optional.of(jdbcURL);
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

	private Server startHSQLDBServer(CommandLine cmd, JdbcURL jdbcURL) throws IOException, AclFormatException, URISyntaxException
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
		val argProps = HsqlProperties.argArrayToProps(options.toArray(new String[0]), serverConnectorName);
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

}
