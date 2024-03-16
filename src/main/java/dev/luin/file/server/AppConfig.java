/*
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

import dev.luin.file.server.core.datasource.DataSourceConfig;
import dev.luin.file.server.core.file.FileSystemConfig;
import dev.luin.file.server.core.querydsl.QueryDSLConfig;
import dev.luin.file.server.core.server.download.DownloadServerConfig;
import dev.luin.file.server.core.server.upload.UploadServerConfig;
import dev.luin.file.server.core.service.file.FileServiceConfig;
import dev.luin.file.server.core.service.user.UserServiceConfig;
import dev.luin.file.server.core.transaction.TransactionManagerConfig;
import dev.luin.file.server.web.WebConfig;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

@Configuration
@Import({UserServiceConfig.class, DataSourceConfig.class, DownloadServerConfig.class, FileSystemConfig.class, QueryDSLConfig.class, FileServiceConfig.class,
		TransactionManagerConfig.class, UploadServerConfig.class, WebConfig.class})
@PropertySource(
		value = {"classpath:dev/luin/file/server/core/default.properties", "classpath:dev/luin/file/server/default.properties",
				"file:${server.configDir}file-server.advanced.properties", "file:${server.configDir}file-server.properties"},
		ignoreResourceNotFound = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class AppConfig
{
	public static final PropertySourcesPlaceholderConfigurer PROPERTY_SOURCE = propertySourcesPlaceholderConfigurer();

	private static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer()
	{
		val result = new PropertySourcesPlaceholderConfigurer();
		val configDir = System.getProperty("server.configDir");
		val resources =
				new Resource[]{new ClassPathResource("dev/luin/file/server/core/default.properties"), new ClassPathResource("dev/luin/file/server/default.properties"),
						new FileSystemResource(configDir + "file-server.advanced.properties"), new FileSystemResource(configDir + "file-server.properties")};
		result.setLocations(resources);
		result.setIgnoreResourceNotFound(true);
		return result;
	}
}
