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

import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.util.convert.IConverter;
import org.apache.wicket.util.convert.converter.AbstractConverter;
import org.apache.wicket.util.io.IClusterable;
import org.bitbucket.eluinstra.fs.service.web.BootstrapFormComponentFeedbackBorder;
import org.bitbucket.eluinstra.fs.service.web.configuration.SslPropertiesFormPanel.SslPropertiesFormModel;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.val;
import lombok.experimental.FieldDefaults;

public class HttpPropertiesFormPanel extends Panel
{
	private static final long serialVersionUID = 1L;
	protected transient Log logger = LogFactory.getLog(this.getClass());

	public HttpPropertiesFormPanel(String id, final IModel<HttpPropertiesFormModel> model, boolean enableSslOverridePropeties)
	{
		super(id,model);
		add(new HttpPropertiesForm("form",model,enableSslOverridePropeties));
	}

	public class HttpPropertiesForm extends Form<HttpPropertiesFormModel>
	{
		private static final long serialVersionUID = 1L;

		public HttpPropertiesForm(String id, final IModel<HttpPropertiesFormModel> model, boolean enableSslOverridePropeties)
		{
			super(id,new CompoundPropertyModel<>(model));
			add(new BootstrapFormComponentFeedbackBorder("hostFeedback",createHostField("host")).add(new Label("protocol")));
			add(new BootstrapFormComponentFeedbackBorder("portFeedback",createPortField("port")));
			add(new BootstrapFormComponentFeedbackBorder("pathFeedback",createPathField("path")));
			add(new TextField<String>("url").setLabel(new ResourceModel("lbl.url")).setOutputMarkupId(true).setEnabled(false));
			add(new CheckBox("base64Writer").setLabel(new ResourceModel("lbl.base64Writer")));
			add(CreateSslCheckBox("ssl"));
			add(createSslPropertiesPanel("sslProperties",enableSslOverridePropeties));
		}

		private FormComponent<String> createHostField(String id)
		{
			TextField<String> result = new TextField<>(id);
			result.setLabel(new ResourceModel("lbl.host"));
			result.add(new OnChangeAjaxBehavior()
	    {
				private static final long serialVersionUID = 1L;

				@Override
				protected void onUpdate(AjaxRequestTarget target)
				{
					target.add(HttpPropertiesForm.this.get("url"));
				}
	    });
			result.setRequired(true);
			return result;
		}

		private TextField<Integer> createPortField(String id)
		{
			TextField<Integer> result = new TextField<>(id);
			result.setLabel(new ResourceModel("lbl.port"));
			result.add(new OnChangeAjaxBehavior()
	    {
				private static final long serialVersionUID = 1L;

				@Override
				protected void onUpdate(AjaxRequestTarget target)
				{
					target.add(HttpPropertiesForm.this.get("url"));
				}
	    });
			return result;
		}

		private TextField<String> createPathField(String id)
		{
			TextField<String> result = new TextField<String>(id)
			{
				private static final long serialVersionUID = 1L;

				@SuppressWarnings("unchecked")
				@Override
				public <C> IConverter<C> getConverter(Class<C> type)
				{
					return (IConverter<C>)new PathConverter();
				}
			};
			result.setLabel(new ResourceModel("lbl.path"));
			result.setRequired(true);
			result.add(new OnChangeAjaxBehavior()
	    {
				private static final long serialVersionUID = 1L;

				@Override
				protected void onUpdate(AjaxRequestTarget target)
				{
					target.add(HttpPropertiesForm.this.get("url"));
				}
	    });
			return result;
		}

		private CheckBox CreateSslCheckBox(String id)
		{
			CheckBox result = new CheckBox(id);
			result.setLabel(new ResourceModel("lbl.ssl"));
			result.add(new AjaxFormComponentUpdatingBehavior("change")
			{
				private static final long serialVersionUID = 1L;

				@Override
				protected void onUpdate(AjaxRequestTarget target)
				{
					target.add(HttpPropertiesForm.this);
				}
			});
			return result;
		}

		private SslPropertiesFormPanel createSslPropertiesPanel(String id, boolean enableSslOverridePropeties)
		{
			val result = new SslPropertiesFormPanel(id,new PropertyModel<>(getModelObject(),"sslProperties"),enableSslOverridePropeties)
			{
				private static final long serialVersionUID = 1L;

				@Override
				public boolean isVisible()
				{
					return getModelObject().getSsl();
				}
			};
			return result;
		}

	}

	@NoArgsConstructor
	@FieldDefaults(level = AccessLevel.PRIVATE)
	@Data()
	public static class HttpPropertiesFormModel implements IClusterable
	{
		private static final long serialVersionUID = 1L;
		String host = "0.0.0.0";
		Integer port = 8443;
		String path = "/fs";
		boolean base64Writer = false;
		boolean ssl = true;
		SslPropertiesFormModel sslProperties = new SslPropertiesFormModel();

		public String getProtocol()
		{
			return ssl ? "https://" : "http://";
		}
		public String getUrl()
		{
			return getProtocol() + host + (port == null ? "" : ":" + port.toString()) + path;
		}
		public boolean getSsl()
		{
			return ssl;
		}
	}
	
	public class PathConverter extends AbstractConverter<String>
	{
		private static final long serialVersionUID = 1L;

		@Override
		public String convertToObject(String value, Locale locale)
		{
			return "/" + value;
		}

		@Override
		public String convertToString(String value, Locale locale)
		{
			return value.substring(1);
		}

		@Override
		protected Class<String> getTargetType()
		{
			return String.class;
		}
	}
}
