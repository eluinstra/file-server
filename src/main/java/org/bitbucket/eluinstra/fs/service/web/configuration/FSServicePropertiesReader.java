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
package org.bitbucket.eluinstra.fs.service.web.configuration;

import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.bitbucket.eluinstra.fs.core.KeyStoreManager.KeyStoreType;
import org.bitbucket.eluinstra.fs.service.web.configuration.ServicePropertiesFormPanel.ServiceProperties;
import org.bitbucket.eluinstra.fs.service.web.configuration.FSServicePropertiesPage.FSServiceProperties;
import org.bitbucket.eluinstra.fs.service.web.configuration.HttpPropertiesFormPanel.HttpProperties;
import org.bitbucket.eluinstra.fs.service.web.configuration.JdbcPropertiesFormPanel.JdbcProperties;
import org.bitbucket.eluinstra.fs.service.web.configuration.SslPropertiesFormPanel.SslProperties;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.experimental.FieldDefaults;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class FSServicePropertiesReader
{
	@NonNull
	Reader reader;

	public void read(@NonNull final FSServiceProperties fsServiceProperties, @NonNull final PropertiesType propertiesType) throws IOException
	{
		val properties = new Properties();
		properties.load(reader);
		read(properties,fsServiceProperties.getServiceProperties());
		read(properties,fsServiceProperties.getHttpProperties());
		read(properties,fsServiceProperties.getJdbcProperties());
	}
	
	protected void read(final Properties properties, final ServiceProperties serviceProperties) throws MalformedURLException
	{
		serviceProperties.setMaxItemsPerPage(Integer.parseInt(properties.getProperty("maxItemsPerPage")));
		serviceProperties.setLog4jPropertiesFile(StringUtils.defaultString(properties.getProperty("log4j.file")).replaceFirst("file:",""));
	}

	protected void read(final Properties properties, final HttpProperties httpProperties) throws MalformedURLException
	{
		httpProperties.setHost(properties.getProperty("fs.host"));
		httpProperties.setPort(properties.getProperty("fs.port") == null ? null : new Integer(properties.getProperty("fs.port")));
		httpProperties.setPath(properties.getProperty("fs.path"));
		httpProperties.setSsl(new Boolean(properties.getProperty("fs.ssl")));
		httpProperties.setBase64Writer(new Boolean(properties.getProperty("http.base64Writer")));
		if (httpProperties.getSsl())
			read(properties,httpProperties.getSslProperties());
	}

	protected void read(final Properties properties, final SslProperties sslProperties) throws MalformedURLException
	{
		sslProperties.setOverrideDefaultProtocols(!StringUtils.isEmpty(properties.getProperty("https.protocols")));
		sslProperties.setEnabledProtocols(Arrays.asList(StringUtils.stripAll(StringUtils.split(properties.getProperty("https.protocols",""),','))));
		sslProperties.setOverrideDefaultCipherSuites(!StringUtils.isEmpty(properties.getProperty("https.cipherSuites")));
		sslProperties.setEnabledCipherSuites(Arrays.asList(StringUtils.stripAll(StringUtils.split(properties.getProperty("https.cipherSuites",""),','))));
		sslProperties.setRequireClientAuthentication(new Boolean(properties.getProperty("https.requireClientAuthentication")));
		sslProperties.getKeystoreProperties().setType(KeyStoreType.valueOf(properties.getProperty("keystore.type","JKS").toUpperCase()));
		sslProperties.getKeystoreProperties().setUri(properties.getProperty("keystore.path"));
		sslProperties.getKeystoreProperties().setPassword(properties.getProperty("keystore.password"));
		sslProperties.getTruststoreProperties().setType(KeyStoreType.valueOf(properties.getProperty("truststore.type","JKS").toUpperCase()));
		sslProperties.getTruststoreProperties().setUri(properties.getProperty("truststore.path"));
		sslProperties.getTruststoreProperties().setPassword(properties.getProperty("truststore.password"));
	}

	protected void read(final Properties properties, final JdbcProperties jdbcProperties) throws MalformedURLException
	{
		jdbcProperties.setDriver(JdbcDriver.getJdbcDriver(properties.getProperty("fs.jdbc.driverClassName")));
		//jdbcProperties.setJdbcURL(properties.getProperty("fs.jdbc.url"));
		Utils.parseJdbcURL(properties.getProperty("fs.jdbc.url"),jdbcProperties);
		jdbcProperties.setUsername(properties.getProperty("fs.jdbc.username"));
		jdbcProperties.setPassword(properties.getProperty("fs.jdbc.password"));
		//jdbcProperties.setPreferredTestQuery(properties.getProperty("fs.pool.preferredTestQuery"));
	}

}
