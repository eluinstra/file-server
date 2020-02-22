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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.CheckBoxMultipleChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.util.io.IClusterable;
import org.bitbucket.eluinstra.fs.core.KeyStoreManager.KeyStoreType;
import org.bitbucket.eluinstra.fs.service.web.WebMarkupContainer;
import org.bitbucket.eluinstra.fs.service.web.configuration.JavaKeyStorePropertiesFormPanel.JavaKeyStorePropertiesFormModel;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.val;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SslPropertiesFormPanel extends Panel
{
	private static final long serialVersionUID = 1L;
	protected transient Log logger = LogFactory.getLog(this.getClass());

	public SslPropertiesFormPanel(final String id, final IModel<SslPropertiesFormModel> model, final boolean enableSslOverridePropeties)
	{
		super(id,model);
		add(new SslPropertiesForm("form",model,enableSslOverridePropeties));
	}

	public class SslPropertiesForm extends Form<SslPropertiesFormModel>
	{
		private static final long serialVersionUID = 1L;

		public SslPropertiesForm(final String id, final IModel<SslPropertiesFormModel> model, final boolean enableSslOverridePropeties)
		{
			super(id,new CompoundPropertyModel<>(model));
			add(createOverrideDefaultProtocolsContainer("overrideDefaultProtocolsContainer",enableSslOverridePropeties));
			add(createEnabledProtocolsContainer("enabledProtocolsContainer",enableSslOverridePropeties));
			add(createOverrideDefaultCipherSuitesContainer("overrideDefaultCipherSuitesContainer",enableSslOverridePropeties));
			add(createEnabledCipherSuitesContainer("enabledCipherSuitesContainer",enableSslOverridePropeties));
			add(createClientAuthenticationRequiredCheckBox("requireClientAuthentication"));
			add(new KeystorePropertiesFormPanel("keystoreProperties",new PropertyModel<>(getModelObject(),"keystoreProperties")));
			add(new TruststorePropertiesFormPanel("truststoreProperties",new PropertyModel<>(getModelObject(),"truststoreProperties")));
		}

		private WebMarkupContainer createOverrideDefaultProtocolsContainer(final String id, final boolean enableSslOverridePropeties)
		{
			val result = new WebMarkupContainer(id);
			result.setVisible(enableSslOverridePropeties);
			val checkBox = new CheckBox("overrideDefaultProtocols");
			checkBox.setLabel(new ResourceModel("lbl.overrideDefaultProtocols"));
			checkBox.add(new AjaxFormComponentUpdatingBehavior("change")
			{
				private static final long serialVersionUID = 1L;

				@Override
				protected void onUpdate(AjaxRequestTarget target)
				{
					target.add(SslPropertiesForm.this);
				}
			});
			result.add(checkBox);
			return result;
		}

		private WebMarkupContainer createEnabledProtocolsContainer(final String id, final boolean enableSslOverridePropeties)
		{
			val result = new WebMarkupContainer(id)
			{
				private static final long serialVersionUID = 1L;

				@Override
				public boolean isVisible()
				{
					return enableSslOverridePropeties && getModelObject().isOverrideDefaultProtocols();
				}
			};
			result.add(
				new CheckBoxMultipleChoice<String>("enabledProtocols",getModelObject().getSupportedProtocols())
					.setSuffix("<br/>")
					.setLabel(new ResourceModel("lbl.enabledProtocols"))
			);
			return result;
		}

		private WebMarkupContainer createOverrideDefaultCipherSuitesContainer(final String id, boolean enableSslOverridePropeties)
		{
			val result = new WebMarkupContainer(id);
			result.setVisible(enableSslOverridePropeties);
			val checkBox = new CheckBox("overrideDefaultCipherSuites");
			checkBox.setLabel(new ResourceModel("lbl.overrideDefaultCipherSuites"));
			checkBox.add(new AjaxFormComponentUpdatingBehavior("change")
			{
				private static final long serialVersionUID = 1L;

				@Override
				protected void onUpdate(AjaxRequestTarget target)
				{
					target.add(SslPropertiesForm.this);
				}
			});
			result.add(checkBox);
			return result;
		}

		private WebMarkupContainer createEnabledCipherSuitesContainer(final String id, final boolean enableSslOverridePropeties)
		{
			val result = new WebMarkupContainer(id)
			{
				private static final long serialVersionUID = 1L;

				@Override
				public boolean isVisible()
				{
					return enableSslOverridePropeties && getModelObject().isOverrideDefaultCipherSuites();
				}
			};
			result.add(
				new CheckBoxMultipleChoice<String>("enabledCipherSuites",getModelObject().getSupportedCipherSuites())
					.setSuffix("<br/>")
					.setLabel(new ResourceModel("lbl.enabledCipherSuites"))
			);
			return result;
		}

		private FormComponent<Boolean> createClientAuthenticationRequiredCheckBox(final String id)
		{
			val result = new CheckBox(id);
			result.setLabel(new ResourceModel("lbl.requireClientAuthentication"));
			result.add(new AjaxFormComponentUpdatingBehavior("change")
			{
				private static final long serialVersionUID = 1L;

				@Override
				protected void onUpdate(AjaxRequestTarget target)
				{
					target.add(SslPropertiesForm.this);
				}
			});
			return result;
		}

	}

	@NoArgsConstructor
	@FieldDefaults(level = AccessLevel.PRIVATE)
	@Data
	public static class SslPropertiesFormModel implements IClusterable
	{
		private static final long serialVersionUID = 1L;
		boolean overrideDefaultProtocols = true;
		final List<String> supportedProtocols = Arrays.asList(Utils.getSupportedSSLProtocols());
		List<String> enabledProtocols = Arrays.asList("TLSv1.2");
		boolean overrideDefaultCipherSuites = false;
		final List<String> supportedCipherSuites = Arrays.asList(Utils.getSupportedSSLCipherSuites());
		List<String> enabledCipherSuites = new ArrayList<>();
		boolean requireClientAuthentication = true;
		JavaKeyStorePropertiesFormModel keystoreProperties = new JavaKeyStorePropertiesFormModel(KeyStoreType.PKCS12,"keystore.p12","password");
		JavaKeyStorePropertiesFormModel truststoreProperties = new JavaKeyStorePropertiesFormModel(KeyStoreType.PKCS12,"truststore.p12","password");
	}
}
