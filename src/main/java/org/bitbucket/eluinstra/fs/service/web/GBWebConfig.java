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
package org.bitbucket.eluinstra.fs.service.web;

import java.util.Collections;

import javax.xml.ws.Endpoint;
import javax.xml.ws.soap.SOAPBinding;

import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.jaxws.EndpointImpl;
import org.bitbucket.eluinstra.digikoppeling.gb.service.GBService;
import org.bitbucket.eluinstra.fs.core.service.FSAdminService;
import org.bitbucket.eluinstra.fs.core.service.FSService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.AccessLevel;
import lombok.val;
import lombok.experimental.FieldDefaults;

@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GBWebConfig
{
	@Value("${maxItemsPerPage}")
	Integer maxItemsPerPage;
	@Autowired
	FSAdminService fsAdminService;
	@Autowired
	FSService fsService;
	@Autowired
	GBService gbService;

	@Bean
	public WicketApplication wicketApplication()
	{
		return new WicketApplication(maxItemsPerPage);
	}

	@Bean
	public Endpoint fsAdminServiceEndpoint()
	{
		return publishEndpoint(fsAdminService,"/admin");
	}

	@Bean
	public Endpoint fsServiceEndpoint()
	{
		val result = publishEndpoint(fsService,"/file");
		((SOAPBinding)result.getBinding()).setMTOMEnabled(true);
		return result;
	}

	@Bean
	public Endpoint gbServiceEndpoint()
	{
		return publishEndpoint(gbService,"/gb");
	}

	@Bean
	public SpringBus cxf()
	{
		val result = new SpringBus();
		val f = new LoggingFeature();
		f.setPrettyLogging(true);
		result.setFeatures(Collections.singletonList(f));
		return result;
	}

	private Endpoint publishEndpoint(Object service, String address)
	{
		val result = new EndpointImpl(cxf(),service);
		result.setAddress(address);
		result.publish();
		return result;
	}
}
