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
package org.bitbucket.eluinstra.fs.service.web.menu;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.bitbucket.eluinstra.fs.service.web.BasePage;
import org.bitbucket.eluinstra.fs.service.web.Utils;

public class MenuLinkItemPanel extends Panel
{
	private static final long serialVersionUID = 1L;

	public MenuLinkItemPanel(String id, MenuLinkItem menuItem)
	{
		this(id,Model.of(menuItem));
	}
	
	public MenuLinkItemPanel(String id, IModel<MenuLinkItem> model)
	{
		super(id,model);
		BookmarkablePageLink<BasePage> link = new BookmarkablePageLink<>("link",model.getObject().getPageClass());
		link.add(new Label("name",Utils.getResourceString(this.getClass(),model.getObject().getName())));
		add(link);
		//add(AttributeModifier.replace("class",Model.of("active"));
	}

}