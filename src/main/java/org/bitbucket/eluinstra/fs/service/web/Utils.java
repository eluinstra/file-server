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

import java.net.URLConnection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.resource.loader.IStringResourceLoader;

public class Utils
{
	public static String getResourceString(Class<?> clazz, String propertyName)
	{
		List<IStringResourceLoader> loaders = WicketApplication.get().getResourceSettings().getStringResourceLoaders();
		return loaders.stream().map(l -> l.loadStringResource(clazz,propertyName,null,null,null)).filter(s -> StringUtils.isNotBlank(s)).findFirst().orElse(propertyName);
	}

	public static String getContentType(String pathInfo)
	{
		String result = URLConnection.guessContentTypeFromName(pathInfo);
		//String result = new MimetypesFileTypeMap().getContentType(pathInfo);
		//String result = URLConnection.getFileNameMap().getContentTypeFor(pathInfo);
		return result == null ? "application/octet-stream" : result;
	}

	public static String getFileExtension(String contentType)
	{
		if (StringUtils.isEmpty(contentType))
			return "";
		return "." + (contentType.contains("text") ? "txt" : contentType.split("/")[1]);
	}

	public static String getErrorList(String content)
	{
		return content.replaceFirst("(?ms)^.*(<[^<>]*:?ErrorList.*ErrorList>).*$","$1");
	}

}
