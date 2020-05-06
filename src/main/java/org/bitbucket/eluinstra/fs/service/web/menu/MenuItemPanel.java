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

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.bitbucket.eluinstra.fs.service.web.Utils;
import org.bitbucket.eluinstra.fs.service.web.WebMarkupContainer;
import org.bitbucket.eluinstra.fs.service.web.menu.MenuPanel.MenuItems;

import lombok.NonNull;
import lombok.val;

public class MenuItemPanel extends Panel
{
	private static final long serialVersionUID = 1L;

	public MenuItemPanel(@NonNull final String id, @NonNull final MenuItem menuItem, final int level)
	{
		this(id,Model.of(menuItem),level);
	}
	
	public MenuItemPanel(@NonNull final String id, @NonNull final IModel<MenuItem> model, int level)
	{
		super(id,model);
		val menuItem = new WebMarkupContainer("menuListItem");
		menuItem.add(new AttributeModifier("class",Model.of(level < 1 ? "dropdown" : "dropdown-submenu")));
		add(menuItem);
		menuItem.add(new Label("name",Utils.getResourceString(this.getClass(),model.getObject().getName())));
		menuItem.add(WebMarkupContainer.builder()
				.id("menuItemCaret")
				.isVisible(() -> level < 1)
				.build());
		menuItem.add(new MenuItems("menuItems",model.getObject().getChildren(),level + 1));
	}
}
