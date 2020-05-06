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

import java.util.Arrays;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.feedback.ContainerFeedbackMessageFilter;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.util.io.IClusterable;
import org.bitbucket.eluinstra.fs.service.web.BootstrapFeedbackPanel;
import org.bitbucket.eluinstra.fs.service.web.BootstrapFormComponentFeedbackBorder;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.val;
import lombok.experimental.FieldDefaults;
import lombok.extern.apachecommons.CommonsLog;

@CommonsLog
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JdbcPropertiesFormPanel extends Panel
{
	private static final long serialVersionUID = 1L;

	public JdbcPropertiesFormPanel(final String id, final IModel<JdbcProperties> model)
	{
		super(id,model);
		JdbcPropertiesForm jdbcPropertiesForm = new JdbcPropertiesForm("form",model);
		add(new BootstrapFeedbackPanel("feedback",new ContainerFeedbackMessageFilter(jdbcPropertiesForm)).setOutputMarkupId(true));
		add(jdbcPropertiesForm);
	}

	public class JdbcPropertiesForm extends Form<JdbcProperties>
	{
		private static final long serialVersionUID = 1L;

		public JdbcPropertiesForm(final String id, final IModel<JdbcProperties> model)
		{
			super(id,new CompoundPropertyModel<>(model));
			add(new BootstrapFormComponentFeedbackBorder("driverFeedback",createDriverChoice("driver",model)));
			add(new BootstrapFormComponentFeedbackBorder("hostFeedback",createHostsField("host")));
			add(new BootstrapFormComponentFeedbackBorder("portFeedback",createPortField("port")));
			add(new BootstrapFormComponentFeedbackBorder("databaseFeedback",createDatabaseField("database")));
			add(new TextField<String>("url").setLabel(new ResourceModel("lbl.url")).setOutputMarkupId(true).setEnabled(false));
			add(createTestButton("test",model));
			add(new BootstrapFormComponentFeedbackBorder("usernameFeedback",new TextField<String>("username").setLabel(new ResourceModel("lbl.username")).setRequired(true)));
			add(new BootstrapFormComponentFeedbackBorder("passwordFeedback",new PasswordTextField("password").setResetPassword(false).setLabel(new ResourceModel("lbl.password")).setRequired(false)));
		}

		private DropDownChoice<JdbcDriver> createDriverChoice(final String id, final IModel<JdbcProperties> model)
		{
			val result = new DropDownChoice<JdbcDriver>(id,new PropertyModel<List<JdbcDriver>>(model.getObject(),"drivers"));
			result.setLabel(new ResourceModel("lbl.driver"));
			result.setRequired(true);
			result.add(new OnChangeAjaxBehavior()
			{
				private static final long serialVersionUID = 1L;

				@Override
				protected void onUpdate(AjaxRequestTarget target)
				{
					if (!model.getObject().getDriver().getDriverClassName().equals(JdbcDriver.HSQLDB.getDriverClassName()) && !classExists(model.getObject().getDriver().getDriverClassName()))
						error(JdbcPropertiesForm.this.getString("driver.jdbc.missing",model));
					target.add(JdbcPropertiesFormPanel.this.get("feedback"));
					target.add(getURLComponent());
				}
			});
			return result;
		}

		private boolean classExists(final String className)
		{
			try
			{
				Class.forName(className);
				return true;
			}
			catch (ClassNotFoundException e)
			{
				return false;
			}
		}

		private TextField<String> createHostsField(final String id)
		{
			val result = new TextField<String>(id);
			result.setLabel(new ResourceModel("lbl.host"));
			result.setRequired(true);
			result.add(OnChangeAjaxBehavior.onUpdate("change",t -> t.add(getURLComponent())));
			return result;
		}

		private TextField<Integer> createPortField(final String id)
		{
			val result = new TextField<Integer>(id);
			result.setLabel(new ResourceModel("lbl.port"));
			result.add(OnChangeAjaxBehavior.onUpdate("change",t -> t.add(getURLComponent())));
			return result;
		}

		private TextField<String> createDatabaseField(final String id)
		{
			val result = new TextField<String>(id);
			result.setLabel(new ResourceModel("lbl.database"));
			result.setRequired(true);
			result.add(OnChangeAjaxBehavior.onUpdate("change",t -> t.add(getURLComponent())));
			return result;
		}

		private Button createTestButton(final String id, final IModel<JdbcProperties> model)
		{
			val result = new Button(id,new ResourceModel("cmd.test"))
			{
				private static final long serialVersionUID = 1L;

				@Override
				public void onSubmit()
				{
					try
					{
						val o = model.getObject();
						Utils.testJdbcConnection(o.getDriver().getDriverClassName(),o.getUrl(),o.getUsername(),o.getPassword());
						info(JdbcPropertiesForm.this.getString("test.ok"));
					}
					catch (Exception e)
					{
						log.error("",e);
						error(new StringResourceModel("test.nok",JdbcPropertiesForm.this,Model.of(e)).getString());
					}
				}

			};
			return result;
		}

		private Component getURLComponent()
		{
			return this.get("url");
		}
	}

	@Data
	@FieldDefaults(level = AccessLevel.PRIVATE)
	@NoArgsConstructor
	@EqualsAndHashCode(callSuper = true)
	public static class JdbcProperties extends JdbcURL implements IClusterable
	{
		private static final long serialVersionUID = 1L;
		final List<JdbcDriver> drivers = Arrays.asList(JdbcDriver.values());
		JdbcDriver driver = JdbcDriver.HSQLDB;
		String username = "sa";
		String password = null;

		public String getUrl()
		{
			//return driver.createJdbcURL(getHost(),getPort(),getDatabase());
			return JdbcDriver.createJdbcURL(driver.getUrlExpr(),getHost(),getPort(),getDatabase());
		}
	}
}
