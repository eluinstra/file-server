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
import java.io.Writer;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.bitbucket.eluinstra.fs.service.web.configuration.ServicePropertiesFormPanel.ServiceProperties;
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
public class PropertiesWriter
{
	@NonNull
	Writer writer;
	boolean enableSslOverridePropeties;

	public void write(@NonNull final org.bitbucket.eluinstra.fs.service.web.configuration.PropertiesPage.Properties properties) throws IOException
	{
		val p = new Properties();
		write(p,properties.getServiceProperties());
		write(p,properties.getHttpProperties(),enableSslOverridePropeties);
		write(p,properties.getJdbcProperties());
		p.store(writer,"FS Service properties");
	}

  protected void write(final Properties properties, final ServiceProperties serviceProperties)
  {
		properties.setProperty("maxItemsPerPage",Integer.toString(serviceProperties.getMaxItemsPerPage()));
  }

	protected void write(final Properties properties, final HttpProperties httpProperties, final boolean enableSslOverridePropeties)
  {
		properties.setProperty("fs.host",httpProperties.getHost());
		properties.setProperty("fs.port",httpProperties.getPort() == null ? "" : httpProperties.getPort().toString());
		properties.setProperty("fs.path",httpProperties.getPath());
		properties.setProperty("fs.ssl",Boolean.toString(httpProperties.getSsl()));
		properties.setProperty("http.base64Writer",Boolean.toString(httpProperties.isBase64Writer()));
		if (httpProperties.getSsl())
			write(properties,httpProperties.getSslProperties(),enableSslOverridePropeties);
  }

	protected void write(final Properties properties, final SslProperties sslProperties, final boolean enableSslOverridePropeties)
  {
		if (enableSslOverridePropeties && sslProperties.isOverrideDefaultProtocols())
			properties.setProperty("https.protocols",StringUtils.join(sslProperties.getEnabledProtocols(),','));
		if (enableSslOverridePropeties && sslProperties.isOverrideDefaultCipherSuites())
			properties.setProperty("https.cipherSuites",StringUtils.join(sslProperties.getEnabledCipherSuites(),','));
		properties.setProperty("https.requireClientAuthentication",Boolean.toString(sslProperties.isRequireClientAuthentication()));
 		properties.setProperty("keystore.type",sslProperties.getKeystoreProperties().getType().name());
 		properties.setProperty("keystore.path",StringUtils.defaultString(sslProperties.getKeystoreProperties().getUri()));
 		properties.setProperty("keystore.password",StringUtils.defaultString(sslProperties.getKeystoreProperties().getPassword()));
 		properties.setProperty("truststore.type",sslProperties.getTruststoreProperties().getType().name());
 		properties.setProperty("truststore.path",StringUtils.defaultString(sslProperties.getTruststoreProperties().getUri()));
 		properties.setProperty("truststore.password",StringUtils.defaultString(sslProperties.getTruststoreProperties().getPassword()));
  }

	protected void write(final Properties properties, final JdbcProperties jdbcProperties)
  {
		properties.setProperty("fs.jdbc.driverClassName",jdbcProperties.getDriver().getDriverClassName());
		properties.setProperty("fs.jdbc.url",jdbcProperties.getUrl());
		properties.setProperty("fs.jdbc.username",jdbcProperties.getUsername());
		properties.setProperty("fs.jdbc.password",StringUtils.defaultString(jdbcProperties.getPassword()));
		properties.setProperty("fs.pool.preferredTestQuery",jdbcProperties.getDriver().getPreferredTestQuery());
  }
  
}
