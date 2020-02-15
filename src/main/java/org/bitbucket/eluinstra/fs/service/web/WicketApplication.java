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
package org.bitbucket.eluinstra.fs.service.web;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.wicket.core.request.handler.PageProvider;
import org.apache.wicket.core.request.handler.RenderPageRequestHandler;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.cycle.IRequestCycleListener;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.resource.JavaScriptResourceReference;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.bitbucket.eluinstra.fs.service.web.menu.MenuItem;
import org.bitbucket.eluinstra.fs.service.web.menu.MenuLinkItem;

public class WicketApplication extends WebApplication
{
	public List<MenuItem> menuItems = new ArrayList<>();
	
	public WicketApplication()
	{
		MenuItem home = new MenuLinkItem("0","home",getHomePage());
		menuItems.add(home);
		
		MenuItem client = new MenuItem("1","clientService");
		menuItems.add(client);

		MenuItem file = new MenuItem("2","fileService");
		menuItems.add(file);

		MenuItem configuration = new MenuItem("3","configuration");
		//new MenuLinkItem(configuration,"1","fsServiceProperties",org.bitbucket.eluinstra.fs.service.web.configuration.FSServicePropertiesPage.class);
		menuItems.add(configuration);

		List<ExtensionProvider> extensionProviders = ExtensionProvider.get();
		if (extensionProviders.size() > 0)
		{
			MenuItem extensions = new MenuItem("5","extensions");
			menuItems.add(extensions);
			AtomicInteger i = new AtomicInteger(1);
			extensionProviders.forEach(p ->
			{
				MenuItem epmi = new MenuItem("" + i.getAndIncrement(),p.getName());
				extensions.addChild(epmi);
				p.getMenuItems().forEach(m -> epmi.addChild(m));
			});
		}

		MenuItem about = new MenuLinkItem("6","about",org.bitbucket.eluinstra.fs.service.web.AboutPage.class);
		menuItems.add(about);
	}
	
	/**
	 * @see org.apache.wicket.Application#getHomePage()
	 */
	@Override
	public Class<? extends WebPage> getHomePage()
	{
		return HomePage.class;
	}

	/**
	 * @see org.apache.wicket.Application#init()
	 */
	@Override
	public void init()
	{
		super.init();
		getDebugSettings().setDevelopmentUtilitiesEnabled(true);
		getComponentInstantiationListeners().add(new SpringComponentInjector(this));
		getJavaScriptLibrarySettings().setJQueryReference(new JavaScriptResourceReference(HomePage.class,"../../../../../js/jquery-min.js"));
		getRequestCycleListeners().add(new IRequestCycleListener()
		{
			@Override
			public IRequestHandler onException(RequestCycle cycle, Exception e)
			{
				return new RenderPageRequestHandler(new PageProvider(new ErrorPage(e)));
			}
		});
		mountPage("/404",PageNotFoundPage.class); 
	}
	
	public List<MenuItem> getMenuItems()
	{
		return menuItems;
	}

	public static WicketApplication get()
	{
		return (WicketApplication)WebApplication.get();
	}
}
