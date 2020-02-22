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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.util.io.IClusterable;
import org.bitbucket.eluinstra.fs.core.KeyStoreManager.KeyStoreType;
import org.bitbucket.eluinstra.fs.service.web.BootstrapFormComponentFeedbackBorder;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.val;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JavaKeyStorePropertiesFormPanel extends Panel
{
	private static final long serialVersionUID = 1L;
	protected transient Log logger = LogFactory.getLog(this.getClass());
	boolean required;

	public JavaKeyStorePropertiesFormPanel(final String id, final IModel<JavaKeyStorePropertiesFormModel> model)
	{
		this(id,model,true);
	}

	public JavaKeyStorePropertiesFormPanel(final String id, final IModel<JavaKeyStorePropertiesFormModel> model, final boolean required)
	{
		super(id,model);
		this.required = required;
		add(new JavaKeyStorePropertiesForm("form",model));
	}

	public class JavaKeyStorePropertiesForm extends Form<JavaKeyStorePropertiesFormModel>
	{
		private static final long serialVersionUID = 1L;

		public JavaKeyStorePropertiesForm(final String id, final IModel<JavaKeyStorePropertiesFormModel> model)
		{
			super(id,new CompoundPropertyModel<>(model));
			add(new BootstrapFormComponentFeedbackBorder("typeFeedback",new DropDownChoice<KeyStoreType>("type",Arrays.asList(KeyStoreType.values())).setLabel(new ResourceModel("lbl.type")).setRequired(required)));
			add(new BootstrapFormComponentFeedbackBorder("uriFeedback",new TextField<String>("uri").setLabel(new ResourceModel("lbl.uri")).setRequired(required)));
			add(new BootstrapFormComponentFeedbackBorder("passwordFeedback",new PasswordTextField("password").setResetPassword(false).setLabel(new ResourceModel("lbl.password")).setRequired(required)));
			add(createTestButton("test",model));
		}

		private Button createTestButton(final String id, final IModel<JavaKeyStorePropertiesFormModel> model)
		{
			return new Button(id,new ResourceModel("cmd.test"))
			{
				private static final long serialVersionUID = 1L;

				@Override
				public void onSubmit()
				{
					try
					{
						val o = model.getObject();
						Utils.testKeystore(o.getType(),o.getUri(),o.getPassword());
						info(JavaKeyStorePropertiesForm.this.getString("test.ok"));
					}
					catch (Exception e)
					{
						logger .error("",e);
						error(new StringResourceModel("test.nok",JavaKeyStorePropertiesForm.this,Model.of(e)).getString());
					}
				}
			};
		}
	}

	@AllArgsConstructor
	@FieldDefaults(level = AccessLevel.PRIVATE)
	@Data
	public static class JavaKeyStorePropertiesFormModel implements IClusterable
	{
		private static final long serialVersionUID = 1L;
		@NonNull
		KeyStoreType type;
		@NonNull
		String uri;
		@NonNull
		String password;
	}
}
