package org.bitbucket.eluinstra.fs.service;

import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.Flyway;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.val;
import lombok.var;
import lombok.experimental.FieldDefaults;

public class DBMigrate
{
	public static final String BASEPATH = "classpath:/org/bitbucket/eluinstra/fs/db/migration/";

	@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
	@AllArgsConstructor
	@Getter
	enum Location
	{
		DB2("jdbc:db2:",BASEPATH + "db2"),
		HSQLDB("jdbc:hsqldb:",BASEPATH + "hsqldb"),
		MSSQL("jdbc:sqlserver:",BASEPATH + "mssql"),
		MYSQL("jdbc:sqlserver:",BASEPATH + "mysql"),
		ORACLE("jdbc:oracle:",BASEPATH + "oracle"),
		POSTGRES("jdbc:postgresql:",BASEPATH + "postgresql");
		
		String jdbcUrl;
		String location;
		
		public static Optional<String> getLocation(String jdbcUrl)
		{
			return Arrays.stream(values())
					.filter(l -> jdbcUrl.startsWith(l.jdbcUrl))
					.map(l -> l.location)
					.findFirst();
		}
	}

	public static void main(String[] args) throws ParseException
	{
		val options = createOptions();
		val cmd = new DefaultParser().parse(options,args);
		if (cmd.hasOption("h"))
			printUsage(options);

		migrate(cmd);
	}

	protected static Options createOptions()
	{
		val result = new Options();
		result.addOption("h",false,"print this message");
		result.addOption("jdbcUrl",true,"set jdbcUrl");
		result.addOption("username",true,"set username");
		result.addOption("password",true,"set password");
		result.addOption("baselineVersion",true,"set baselineVersion (default: none)");
		return result;
	}
	
	protected static void printUsage(Options options)
	{
		val formatter = new HelpFormatter();
		formatter.printHelp("Start",options,true);
		System.exit(0);
	}

	private static void migrate(CommandLine cmd) throws ParseException
	{
		val jdbcUrl = cmd.getOptionValue("jdbcUrl");
		val username = cmd.getOptionValue("username");
		val password = cmd.getOptionValue("password");
		val location = parseLocation(jdbcUrl);
		val baselineVersion = cmd.getOptionValue("baselineVersion");
		var config = Flyway.configure()
				.dataSource(jdbcUrl,username,password)
				.locations(location)
				.ignoreMissingMigrations(true);
		if (StringUtils.isNotEmpty(baselineVersion))
				config = config
						.baselineVersion(baselineVersion)
						.baselineOnMigrate(true);
		config.load().migrate();
	}

	private static String parseLocation(String jdbcUrl) throws ParseException
	{
		return Location.getLocation(jdbcUrl).orElseThrow(() -> new ParseException("No location found for jdbcUrl " + jdbcUrl));
	}
}
