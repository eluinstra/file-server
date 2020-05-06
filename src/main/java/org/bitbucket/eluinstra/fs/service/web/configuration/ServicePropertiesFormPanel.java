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

import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.util.io.IClusterable;
import org.bitbucket.eluinstra.fs.service.web.BootstrapFormComponentFeedbackBorder;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;


@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ServicePropertiesFormPanel extends Panel
{
	private static final long serialVersionUID = 1L;

	public ServicePropertiesFormPanel(String id, final IModel<ServiceProperties> model)
	{
		super(id,model);
		add(new ServicePropertiesForm("form",model));
	}

	public class ServicePropertiesForm extends Form<ServiceProperties>
	{
		private static final long serialVersionUID = 1L;

		public ServicePropertiesForm(String id, final IModel<ServiceProperties> model)
		{
			super(id,new CompoundPropertyModel<>(model));
			add(new BootstrapFormComponentFeedbackBorder("maxItemsPerPageFeedback",new TextField<Integer>("maxItemsPerPage").setLabel(new ResourceModel("lbl.maxItemsPerPage")).setRequired(true)));
			add(new BootstrapFormComponentFeedbackBorder("log4jPropertiesFileFeedback",new TextField<String>("log4jPropertiesFile").setLabel(new ResourceModel("lbl.log4jPropertiesFile"))));
			add(new DownloadLog4jFileLink("downloadLog4jFile"));
		}
	}

	@Data
	@FieldDefaults(level = AccessLevel.PRIVATE)
	@NoArgsConstructor
	public static class ServiceProperties implements IClusterable
	{
		private static final long serialVersionUID = 1L;
		int maxItemsPerPage = 20;
		String log4jPropertiesFile;
	}
}
