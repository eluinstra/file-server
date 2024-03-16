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

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import dev.luin.file.server.core.service.file.FileService;
import dev.luin.file.server.core.service.file.FileServiceImpl;
import dev.luin.file.server.core.service.user.UserService;
import dev.luin.file.server.core.service.user.UserServiceImpl;
import jakarta.xml.ws.Endpoint;
import jakarta.xml.ws.soap.SOAPBinding;
import java.util.Arrays;
import java.util.Collections;
import javax.xml.namespace.QName;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.val;
import org.apache.cxf.Bus;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.bus.spring.SpringBus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.jaxrs.openapi.OpenApiFeature;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WebConfig
{
	@Autowired
	UserService userService;
	@Autowired
	FileService fileService;

	@Bean
	public Endpoint userServiceEndpoint()
	{
		return publishEndpoint(userService, "/user", "http://luin.dev/file/server/1.0", "UserService", "UserServicePort");
	}

	@Bean
	public Endpoint fileServiceEndpoint()
	{
		val result = publishEndpoint(fileService, "/file", "http://luin.dev/file/server/1.0", "FileService", "FileServicePort");
		((SOAPBinding)result.getBinding()).setMTOMEnabled(true);
		return result;
	}

	@Bean(name = "cxf")
	public SpringBus springBus()
	{
		val result = new SpringBus();
		result.setFeatures(Collections.singletonList(createLoggingFeature()));
		return result;
	}

	private LoggingFeature createLoggingFeature()
	{
		val result = new LoggingFeature();
		result.setPrettyLogging(true);
		return result;
	}

	protected Endpoint publishEndpoint(Object service, String address, String namespaceUri, String serviceName, String endpointName)
	{
		val result = new EndpointImpl(springBus(), service);
		result.setAddress(address);
		result.setServiceName(new QName(namespaceUri, serviceName));
		result.setEndpointName(new QName(namespaceUri, endpointName));
		result.publish();
		return result;
	}

	@Bean
	public Server createUserJAXRSServer()
	{
		return createJAXRSServer(UserServiceImpl.class, userService, "/users");
	}

	@Bean
	public Server createFileJAXRSServer()
	{
		return createJAXRSServer(FileServiceImpl.class, fileService, "/files");
	}

	protected Server createJAXRSServer(Class<?> resourceClass, Object resourceObject, String path)
	{
		val sf = new JAXRSServerFactoryBean();
		sf.setBus(springBus());
		sf.setAddress("/rest/v1" + path);
		sf.setProvider(createJacksonJsonProvider());
		sf.setFeatures(Arrays.asList(createOpenApiFeature()));
		createResource(sf, resourceClass, resourceObject);
		registerBindingFactory(sf.getBus());
		return sf.create();
	}

	protected JacksonJsonProvider createJacksonJsonProvider()
	{
		val result = new JacksonJsonProvider();
		result.setMapper(createObjectMapper());
		return result;
	}

	protected ObjectMapper createObjectMapper()
	{
		val result = new ObjectMapper();
		result.registerModule(new JavaTimeModule());
		result.registerModule(new Jdk8Module());
		result.registerModule(new SimpleModule());
		result.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		result.setSerializationInclusion(Include.NON_NULL);
		return result;
	}

	protected OpenApiFeature createOpenApiFeature()
	{
		val result = new OpenApiFeature();
		result.setUseContextBasedConfig(true);
		result.setScan(false);
		result.setSupportSwaggerUi(false);
		return result;
	}

	protected void createResource(final JAXRSServerFactoryBean sf, Class<?> resourceClass, Object resourceObject)
	{
		sf.setResourceClasses(resourceClass);
		sf.setResourceProvider(resourceClass, new SingletonResourceProvider(resourceObject));
	}

	protected void registerBindingFactory(final Bus bus)
	{
		val manager = bus.getExtension(BindingFactoryManager.class);
		manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID, new JAXRSBindingFactory(bus));
	}

}
