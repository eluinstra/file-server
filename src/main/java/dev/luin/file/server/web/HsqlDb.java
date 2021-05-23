package dev.luin.file.server.web;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.hsqldb.persist.HsqlProperties;
import org.hsqldb.server.Server;
import org.hsqldb.server.ServerAcl.AclFormatException;
import org.hsqldb.server.ServiceProperties;

import dev.luin.file.server.SystemInterface;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
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

	public static Options addOptions(Options options)
	{
		options.addOption(Option.HSQLDB.name,false,"start HSQLDB server");
		options.addOption(Option.HSQLDB_DIR.name,true,"set HSQLDB location [default: " + DefaultValue.HSQLDB_DIR.value + "]");
		return options;
	}

	public void startHSQLDB(CommandLine cmd, Properties properties) throws IOException, AclFormatException, URISyntaxException
	{
		if ("org.hsqldb.jdbcDriver".equals(properties.getProperty("jdbc.driverClassName")) && cmd.hasOption(Option.HSQLDB.name))
		{
			val jdbcURL = getHsqlDbJdbcUrl(cmd,properties);
			if (jdbcURL.isPresent())
			{
				val server = createHSQLDBServer(cmd,jdbcURL.get());
				println("Starting HSQLDB Server...");
				server.start();
			}
			else
				exit(1);
		}
	}

	private Optional<JdbcURL> getHsqlDbJdbcUrl(CommandLine cmd, Properties properties) throws IOException, AclFormatException, URISyntaxException
	{
		val jdbcURL = JdbcURL.of(properties.getProperty("jdbc.url"));
		val allowedHosts = "localhost|127.0.0.1";
		if (!jdbcURL.getHost().matches("^(" + allowedHosts + ")$"))
		{
			println("Cannot start HSQLDB Server on " + jdbcURL.getHost() + ". Use " + allowedHosts + " instead.");
			return Optional.empty();
		}
		return Optional.of(jdbcURL);
	}

	private Server createHSQLDBServer(CommandLine cmd, JdbcURL jdbcURL) throws IOException, AclFormatException, URISyntaxException
	{
		val options = createOptions(cmd,jdbcURL);
		val argProps = HsqlProperties.argArrayToProps(options.toArray(new String[0]), "server");
		val props = ServiceProperties.of(argProps);
		return createServer(props);
	}

	private ArrayList<Object> createOptions(CommandLine cmd, JdbcURL jdbcURL)
	{
		val result = new ArrayList<>();
		result.add("-database.0");
		result.add((cmd.hasOption(Option.HSQLDB_DIR.name) ? "file:" + cmd.getOptionValue(Option.HSQLDB_DIR.name) : "file:" + DefaultValue.HSQLDB_DIR.value) + "/" + jdbcURL.getDatabase());
		result.add("-dbname.0");
		result.add(jdbcURL.getDatabase());
		if (jdbcURL.getPort() != null)
		{
			result.add("-port");
			result.add(jdbcURL.getPort().toString());
		}
		return result;
	}

	private Server createServer(final ServiceProperties props) throws IOException, AclFormatException
	{
		val result = new Server();
		result.setProperties(props);
		return result;
	}

}
