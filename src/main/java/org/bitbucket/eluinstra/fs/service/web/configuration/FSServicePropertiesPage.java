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
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.io.IClusterable;
import org.bitbucket.eluinstra.fs.service.PropertyPlaceholderConfigurer;
import org.bitbucket.eluinstra.fs.service.web.BasePage;
import org.bitbucket.eluinstra.fs.service.web.BootstrapFeedbackPanel;
import org.bitbucket.eluinstra.fs.service.web.BootstrapPanelBorder;
import org.bitbucket.eluinstra.fs.service.web.ResetButton;
import org.bitbucket.eluinstra.fs.service.web.configuration.ServicePropertiesFormPanel.ServicePropertiesFormModel;
import org.bitbucket.eluinstra.fs.service.web.configuration.HttpPropertiesFormPanel.HttpPropertiesFormModel;
import org.bitbucket.eluinstra.fs.service.web.configuration.JdbcPropertiesFormPanel.JdbcPropertiesFormModel;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.val;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FSServicePropertiesPage extends BasePage
{
	private class ComponentsListView extends ListView<BootstrapPanelBorder>
	{
		private static final long serialVersionUID = 1L;

		public ComponentsListView(final String id, final List<BootstrapPanelBorder> list)
		{
			super(id,list);
			setReuseItems(true);
		}

		@Override
		protected void populateItem(final ListItem<BootstrapPanelBorder> item)
		{
			item.add((BootstrapPanelBorder)item.getModelObject()); 
		}
	}

	private static final long serialVersionUID = 1L;
	protected transient Log logger = LogFactory.getLog(this.getClass());
	@SpringBean(name="propertyConfigurer")
	@NonFinal
	PropertyPlaceholderConfigurer propertyPlaceholderConfigurer;
	PropertiesType propertiesType;

	public FSServicePropertiesPage() throws IOException
	{
		this(null);
	}
	public FSServicePropertiesPage(FSServicePropertiesFormModel fsServicePropertiesFormModel) throws IOException
	{
		propertiesType = PropertiesType.getPropertiesType(propertyPlaceholderConfigurer.getOverridePropertiesFile().getFilename());
		add(new BootstrapFeedbackPanel("feedback"));
		if (fsServicePropertiesFormModel == null)
		{
			fsServicePropertiesFormModel = new FSServicePropertiesFormModel();
			try
			{
				val file = new File(propertiesType.getPropertiesFile());
				val reader = new FileReader(file);
				new FSServicePropertiesReader(reader).read(fsServicePropertiesFormModel,propertiesType);
				this.info(new StringResourceModel("properties.loaded",this,Model.of(file)).getString());
			}
			catch (IOException e)
			{
				logger.error("",e);
				error(e.getMessage());
			}
		}
		add(new FSServicePropertiesForm("form",fsServicePropertiesFormModel));
	}
	
	@Override
	public String getPageTitle()
	{
		return getLocalizer().getString("fsServiceProperties",this);
	}
	
	public class FSServicePropertiesForm extends Form<FSServicePropertiesFormModel>
	{
		private static final long serialVersionUID = 1L;

		public FSServicePropertiesForm(String id, FSServicePropertiesFormModel model)
		{
			super(id,new CompoundPropertyModel<>(model));
			val components = new ArrayList<BootstrapPanelBorder>();
			components.add(new BootstrapPanelBorder("panelBorder",FSServicePropertiesPage.this.getString("serviceProperties"),new ServicePropertiesFormPanel("component",new PropertyModel<>(getModelObject(),"serviceProperties"))));
			components.add(new BootstrapPanelBorder("panelBorder",FSServicePropertiesPage.this.getString("httpProperties"),new HttpPropertiesFormPanel("component",new PropertyModel<>(getModelObject(),"httpProperties"),true)));  
			components.add(new BootstrapPanelBorder("panelBorder",FSServicePropertiesPage.this.getString("jdbcProperties"),new JdbcPropertiesFormPanel("component",new PropertyModel<>(getModelObject(),"jdbcProperties"))));
			add(new ComponentsListView("components",components));
			add(createValidateButton("validate"));
			add(new DownloadFSServicePropertiesButton("download",new ResourceModel("cmd.download"),getModelObject(),propertiesType));
			add(new SaveFSServicePropertiesButton("save",new ResourceModel("cmd.save"),getModelObject(),propertiesType));
			add(new ResetButton("reset",new ResourceModel("cmd.reset"),FSServicePropertiesPage.class));
		}

		private Button createValidateButton(String id)
		{
			return new Button(id)
			{
				private static final long serialVersionUID = 1L;

				@Override
				public void onSubmit()
				{
					info(FSServicePropertiesPage.this.getString("validate.ok"));
				}
			};
		}
	}

	@NoArgsConstructor
	@FieldDefaults(level = AccessLevel.PRIVATE)
	@Data
	public static class FSServicePropertiesFormModel implements IClusterable
	{
		private static final long serialVersionUID = 1L;
		ServicePropertiesFormModel serviceProperties = new ServicePropertiesFormModel();
		HttpPropertiesFormModel httpProperties = new HttpPropertiesFormModel();
		JdbcPropertiesFormModel jdbcProperties = new JdbcPropertiesFormModel();
	}
}
