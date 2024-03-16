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
package dev.luin.file.server.web;

import dev.luin.digikoppeling.gb.server.service.GBService;
import dev.luin.digikoppeling.gb.server.service.GBServiceImpl;
import jakarta.xml.ws.Endpoint;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.cxf.endpoint.Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GBWebConfig extends WebConfig
{
	@Autowired
	GBService gbService;

	@Bean
	public Endpoint gbServiceEndpoint()
	{
		return publishEndpoint(gbService, "/gb", "http://luin.dev/digikoppeling/gb/server/1.0", "GBService", "GBServicePort");
	}

	@Bean
	public Server createGBJAXRSServer()
	{
		return createJAXRSServer(GBServiceImpl.class, gbService, "/gb");
	}
}
