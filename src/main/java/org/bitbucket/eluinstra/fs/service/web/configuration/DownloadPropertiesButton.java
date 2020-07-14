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
import java.io.StringWriter;

import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.request.handler.resource.ResourceStreamRequestHandler;
import org.apache.wicket.request.resource.ContentDisposition;
import org.apache.wicket.util.resource.IResourceStream;
import org.bitbucket.eluinstra.fs.service.web.configuration.PropertiesPage.Properties;

import lombok.AccessLevel;
import lombok.val;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DownloadPropertiesButton extends Button
{
	private static final long serialVersionUID = 1L;
	Properties properties;
	PropertiesType propertiesType;

	public DownloadPropertiesButton(final String id, final ResourceModel resourceModel, final Properties properties, final PropertiesType propertiesType)
	{
		super(id,resourceModel);
		this.properties = properties;
		this.propertiesType = propertiesType;
	}

	@Override
	public void onSubmit()
	{
		try
		{
			val writer = new StringWriter();
			new PropertiesWriter(writer,true).write(properties);
			val resourceStream = new StringWriterResourceStream(writer,"plain/text");
			getRequestCycle().scheduleRequestHandlerAfterCurrent(createRequestHandler(resourceStream));
		}
		catch (IOException e)
		{
			log.error("",e);
			error(e.getMessage());
		}
	}

	private ResourceStreamRequestHandler createRequestHandler(final IResourceStream resourceStream)
	{
		return new ResourceStreamRequestHandler(resourceStream)
				.setFileName(propertiesType.getPropertiesFile())
				.setContentDisposition(ContentDisposition.ATTACHMENT);
	}

}
