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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.request.handler.resource.ResourceStreamRequestHandler;
import org.apache.wicket.request.resource.ContentDisposition;
import org.apache.wicket.util.resource.IResourceStream;
import org.bitbucket.eluinstra.fs.service.web.configuration.FSServicePropertiesPage.FSServiceProperties;

import lombok.AccessLevel;
import lombok.val;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DownloadFSServicePropertiesButton extends Button
{
	private static final long serialVersionUID = 1L;
	protected transient Log logger = LogFactory.getLog(this.getClass());
	FSServiceProperties fsServiceProperties;
	PropertiesType propertiesType;

	public DownloadFSServicePropertiesButton(final String id, final ResourceModel resourceModel, final FSServiceProperties fsServiceProperties, final PropertiesType propertiesType)
	{
		super(id,resourceModel);
		this.fsServiceProperties = fsServiceProperties;
		this.propertiesType = propertiesType;
	}

	@Override
	public void onSubmit()
	{
		try
		{
			val writer = new StringWriter();
			new FSServicePropertiesWriter(writer,true).write(fsServiceProperties);
			val resourceStream = new StringWriterResourceStream(writer,"plain/text");
			getRequestCycle().scheduleRequestHandlerAfterCurrent(createRequestHandler(resourceStream));
		}
		catch (IOException e)
		{
			logger.error("",e);
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
