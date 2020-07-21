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
package dev.luin.fs.web.configuration;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.io.IClusterable;

import dev.luin.fs.PropertySourcesPlaceholderConfigurer;
import dev.luin.fs.web.BasePage;
import dev.luin.fs.web.BootstrapFeedbackPanel;
import dev.luin.fs.web.BootstrapPanelBorder;
import dev.luin.fs.web.Button;
import dev.luin.fs.web.ResetButton;
import dev.luin.fs.web.configuration.HttpPropertiesFormPanel.HttpProperties;
import dev.luin.fs.web.configuration.JdbcPropertiesFormPanel.JdbcProperties;
import dev.luin.fs.web.configuration.ServicePropertiesFormPanel.ServiceProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.val;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PropertiesPage extends BasePage
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
	@SpringBean(name="propertyConfigurer")
	PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer;
	final PropertiesType propertiesType;

	public PropertiesPage() throws IOException
	{
		this(null);
	}
	public PropertiesPage(final Properties properties) throws IOException
	{
		propertiesType = PropertiesType.getPropertiesType(propertySourcesPlaceholderConfigurer.getOverridePropertiesFile().getFilename());
		add(new BootstrapFeedbackPanel("feedback"));
		val model = properties == null ? createProperties() : properties;
		add(new PropertiesForm("form",model));
	}

	private Properties createProperties()
	{
		val result = new Properties();
		try
		{
			val file = new File(propertiesType.getPropertiesFile());
			val reader = new FileReader(file);
			new PropertiesReader(reader).read(result,propertiesType);
			this.info(new StringResourceModel("properties.loaded",this,Model.of(file)).getString());
		}
		catch (IOException e)
		{
			log.error("",e);
			error(e.getMessage());
		}
		return result;
	}
	
	@Override
	public String getPageTitle()
	{
		return getLocalizer().getString("properties",this);
	}
	
	public class PropertiesForm extends Form<Properties>
	{
		private static final long serialVersionUID = 1L;

		public PropertiesForm(final String id, final Properties properties)
		{
			super(id,CompoundPropertyModel.of(properties));
			val components = new ArrayList<BootstrapPanelBorder>();
			components.add(new BootstrapPanelBorder("panelBorder",PropertiesPage.this.getString("serviceProperties"),new ServicePropertiesFormPanel("component",getModel().map(m -> m.getServiceProperties()))));
			components.add(new BootstrapPanelBorder("panelBorder",PropertiesPage.this.getString("httpProperties"),new HttpPropertiesFormPanel("component",getModel().map(m -> m.getHttpProperties()),true)));  
			components.add(new BootstrapPanelBorder("panelBorder",PropertiesPage.this.getString("jdbcProperties"),new JdbcPropertiesFormPanel("component",getModel().map(m -> m.getJdbcProperties()))));
			add(new ComponentsListView("components",components));
			add(createValidateButton("validate"));
			add(new DownloadPropertiesButton("download",new ResourceModel("cmd.download"),getModelObject(),propertiesType));
			add(new SavePropertiesButton("save",new ResourceModel("cmd.save"),getModelObject(),propertiesType));
			add(new ResetButton("reset",new ResourceModel("cmd.reset"),PropertiesPage.class));
		}

		private Button createValidateButton(final String id)
		{
			return Button.builder()
					.id(id)
					.onSubmit(() -> info(PropertiesPage.this.getString("validate.ok")))
					.build();
		}
	}

	@Data
	@FieldDefaults(level = AccessLevel.PRIVATE)
	@NoArgsConstructor
	public static class Properties implements IClusterable
	{
		private static final long serialVersionUID = 1L;
		ServiceProperties serviceProperties = new ServiceProperties();
		HttpProperties httpProperties = new HttpProperties();
		JdbcProperties jdbcProperties = new JdbcProperties();
	}
}