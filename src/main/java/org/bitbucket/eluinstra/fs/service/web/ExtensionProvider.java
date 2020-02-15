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
import java.util.ServiceLoader;

import org.bitbucket.eluinstra.fs.service.web.menu.MenuItem;

public abstract class ExtensionProvider
{
	public static List<ExtensionProvider> get()
	{
		ServiceLoader<ExtensionProvider> providers = ServiceLoader.load(ExtensionProvider.class);
		List<ExtensionProvider> result = new ArrayList<>();
		for (ExtensionProvider provider : providers)
			result.add(provider);
		return result;
	}

	public abstract String getSpringConfigurationFile();
	public abstract String getHSQLDBFile();
	public abstract String getName();
	public abstract List<MenuItem> getMenuItems();

}
