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

import java.util.Collections;

import javax.xml.ws.Endpoint;
import javax.xml.ws.soap.SOAPBinding;

import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.luin.digikoppeling.gb.server.service.GBService;
import dev.luin.file.server.core.service.FileService;
import dev.luin.file.server.core.service.UserService;
import lombok.AccessLevel;
import lombok.val;
import lombok.experimental.FieldDefaults;

@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GBWebConfig extends WebConfig
{
	@Autowired
	UserService userService;
	@Autowired
	FileService fileService;
	@Autowired
	GBService gbService;

	@Bean
	public Endpoint userServiceEndpoint()
	{
		return publishEndpoint(userService,"/user","http://luin.dev/file/server/1.0","UserService","UserServicePort");
	}

	@Bean
	public Endpoint fileServiceEndpoint()
	{
		val result = publishEndpoint(fileService,"/file","http://luin.dev/file/server/1.0","FileService","FileServicePort");
		val binding = (SOAPBinding)result.getBinding();
		binding.setMTOMEnabled(true);
		return result;
	}

	@Bean
	public Endpoint gbServiceEndpoint()
	{
		return publishEndpoint(gbService,"/gb","http://luin.dev/digikoppeling/gb/server/1.0","GBService","GBServicePort");
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
}
