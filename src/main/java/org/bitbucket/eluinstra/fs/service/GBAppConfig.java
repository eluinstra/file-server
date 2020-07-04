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
package org.bitbucket.eluinstra.fs.service;

import org.bitbucket.eluinstra.digikoppeling.gb.service.GBServiceConfig;
import org.bitbucket.eluinstra.fs.core.dao.DAOConfig;
import org.bitbucket.eluinstra.fs.core.datasource.DataSourceConfig;
import org.bitbucket.eluinstra.fs.core.datasource.QueryDSLConfig;
import org.bitbucket.eluinstra.fs.core.file.FileSystemConfig;
import org.bitbucket.eluinstra.fs.core.server.download.DownloadServerConfig;
import org.bitbucket.eluinstra.fs.core.service.ServiceConfig;
import org.bitbucket.eluinstra.fs.core.transaction.TransactionManagerConfig;
import org.bitbucket.eluinstra.fs.service.web.GBWebConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import lombok.AccessLevel;
import lombok.val;
import lombok.experimental.FieldDefaults;

@Configuration
@Import({
	DAOConfig.class,
	DataSourceConfig.class,
	DownloadServerConfig.class,
	FileSystemConfig.class,
	GBServiceConfig.class,
	GBWebConfig.class,
	QueryDSLConfig.class,
	ServiceConfig.class,
	TransactionManagerConfig.class
})
@PropertySource(value = {
		"classpath:org/bitbucket/eluinstra/fs/core/default.properties",
		"classpath:org/bitbucket/eluinstra/digikoppeling/gb/default.properties",
		"classpath:org/bitbucket/eluinstra/fs/service/default.properties",
		"file:${fs.configDir}fs-service.advanced.properties",
		"file:${fs.configDir}fs-service.properties"},
		ignoreResourceNotFound = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GBAppConfig
{
	public static PropertySourcesPlaceholderConfigurer PROPERTY_SOURCE = propertySourcesPlaceholderConfigurer();
	
	private static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer()
	{
		val result = new PropertySourcesPlaceholderConfigurer();
		val configDir = System.getProperty("fs.configDir");
		val resources = new Resource[]{
				new ClassPathResource("org/bitbucket/eluinstra/fs/core/default.properties"),
				new ClassPathResource("org/bitbucket/eluinstra/digikoppeling/gb/default.properties"),
				new ClassPathResource("classpath:org/bitbucket/eluinstra/fs/service/default.properties"),
				new FileSystemResource(configDir + "fs-service.advanced.properties"),
				new FileSystemResource(configDir + "fs-service.properties")};
		result.setLocations(resources);
		result.setIgnoreResourceNotFound(true);
		return result;
	}
}
