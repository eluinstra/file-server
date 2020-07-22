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
package dev.luin.fs;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import dev.luin.fs.core.datasource.DataSourceConfig;
import dev.luin.fs.core.file.FileSystemConfig;
import dev.luin.fs.core.querydsl.QueryDSLConfig;
import dev.luin.fs.core.server.download.DownloadServerConfig;
import dev.luin.fs.core.server.upload.UploadServerConfig;
import dev.luin.fs.core.service.ServiceConfig;
import dev.luin.fs.core.transaction.TransactionManagerConfig;
import dev.luin.fs.core.user.UserManagerConfig;
import dev.luin.fs.web.WebConfig;
import lombok.AccessLevel;
import lombok.val;
import lombok.experimental.FieldDefaults;

@Configuration
@Import({
	UserManagerConfig.class,
	DataSourceConfig.class,
	DownloadServerConfig.class,
	FileSystemConfig.class,
	QueryDSLConfig.class,
	ServiceConfig.class,
	TransactionManagerConfig.class,
	UploadServerConfig.class,
	WebConfig.class
})
@PropertySource(value = {
		"classpath:dev/luin/fs/core/default.properties",
		"classpath:dev/luin/fs/default.properties",
		"file:${fs.configDir}fs-service.advanced.properties",
		"file:${fs.configDir}fs-service.properties"},
		ignoreResourceNotFound = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AppConfig
{
	public static PropertySourcesPlaceholderConfigurer PROPERTY_SOURCE = propertySourcesPlaceholderConfigurer();
	
	private static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer()
	{
		val result = new PropertySourcesPlaceholderConfigurer();
		val configDir = System.getProperty("fs.configDir");
		val resources = new Resource[]{
				new ClassPathResource("dev/luin/fs/core/default.properties"),
				new ClassPathResource("dev/luin/fs/default.properties"),
				new FileSystemResource(configDir + "fs-service.advanced.properties"),
				new FileSystemResource(configDir + "fs-service.properties")};
		result.setLocations(resources);
		result.setIgnoreResourceNotFound(true);
		return result;
	}
}
