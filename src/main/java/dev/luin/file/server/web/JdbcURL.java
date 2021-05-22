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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.val;

@Builder
@Value
public class JdbcURL
{
	String host;
	Integer port;
	String database;

	public static JdbcURL of(@NonNull final String jdbcURL) throws MalformedURLException
	{
		try (val scanner = new Scanner(jdbcURL))
		{
			val protocol = scanner.findInLine("(://|@|:@//)");
			if (protocol != null)
			{
				val urlString = scanner.findInLine("[^/:]+(:\\d+){0,1}");
				if (urlString != null)
				{
					val databaseNameDeclaration = scanner.findInLine("(/|:|;databaseName=)");
					if (databaseNameDeclaration != null)
					{
						val database = scanner.findInLine("[^;]*");
						if (database != null)
						{
							val url = new URL("http://" + urlString);
							return JdbcURL.builder()
								.host(url.getHost())
								.port(url.getPort() == -1 ? null : url.getPort())
								.database(database)
								.build();
						}
					}
				}
			}
			throw new MalformedURLException("Unable to parse JDBC URL " + jdbcURL);
		}
	}

}
