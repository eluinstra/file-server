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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.bitbucket.eluinstra.fs.service.web.configuration.FSServicePropertiesPage.FSServicePropertiesFormModel;

import lombok.AccessLevel;
import lombok.val;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SaveFSServicePropertiesButton extends Button
{
	private static final long serialVersionUID = 1L;
	protected transient Log logger = LogFactory.getLog(this.getClass());
	FSServicePropertiesFormModel fsServicePropertiesFormModel;
	PropertiesType propertiesType;

	public SaveFSServicePropertiesButton(final String id, final ResourceModel resourceModel, final FSServicePropertiesFormModel fsServicePropertiesFormModel, final PropertiesType propertiesType)
	{
		super(id,resourceModel);
		this.fsServicePropertiesFormModel = fsServicePropertiesFormModel;
		this.propertiesType = propertiesType;
	}

	@Override
	public void onSubmit()
	{
		try
		{
			val file = new File(propertiesType.getPropertiesFile());
			val writer = new FileWriter(file);
			new FSServicePropertiesWriter(writer,true).write(fsServicePropertiesFormModel);
			info(new StringResourceModel("properties.saved",getPage(),Model.of(file)).getString());
			error(new StringResourceModel("restart",getPage(),null).getString());
		}
		catch (IOException e)
		{
			logger.error("",e);
			error(e.getMessage());
		}
	}

}
