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

import static io.vavr.API.For;

import java.net.URL;
import java.util.Scanner;

import io.vavr.Function1;
import io.vavr.control.Option;
import io.vavr.control.Try;
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

	public static JdbcURL of(@NonNull final String jdbcURL)
	{
		try (val scanner = new Scanner(jdbcURL))
		{
			val lineScanner = createLineScanner(scanner);
			return For(
				lineScanner.apply("(://|@|:@//)"),
				lineScanner.apply("[^/:]+(:\\d+){0,1}"),
				lineScanner.apply("(/|:|;databaseName=)"),
				lineScanner.apply("[^;]*")
			).yield((protocol,urlString,databaseNameDeclaration,database) ->
			{
				val url = toUrl(urlString).getOrElseThrow(e -> new IllegalStateException(e));
				return JdbcURL.builder()
						.host(url.getHost())
						.port(url.getPort() == -1 ? null : url.getPort())
						.database(database)
						.build();
			})
			.getOrElseThrow(() -> new IllegalStateException("Unable to parse JDBC URL " + jdbcURL));
		}
	}

	private static Try<URL> toUrl(String urlString)
	{
		return Try.of(() -> new URL("http://" + urlString));
	}

	private static Function1<String,Option<String>> createLineScanner(Scanner scanner)
	{
		return line -> Option.of(scanner.findInLine(line));
	}

}
