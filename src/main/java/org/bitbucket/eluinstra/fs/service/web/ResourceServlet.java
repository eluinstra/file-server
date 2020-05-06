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

import java.io.IOException;

import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.wicket.util.io.IOUtils;

import lombok.val;

public class ResourceServlet extends GenericServlet
{
	private static final long serialVersionUID = 1L;
	
	@Override
	public void init() throws ServletException
	{
		super.init();
	}

	@Override
	public void service(final ServletRequest request, final ServletResponse response) throws ServletException, IOException
	{
		val input = this.getClass().getResourceAsStream(((HttpServletRequest)request).getServletPath() + ((HttpServletRequest)request).getPathInfo());
		if (input == null)
			((HttpServletResponse)response).setStatus(204);
		else
		{
			((HttpServletResponse)response).setStatus(200);
			((HttpServletResponse)response).setContentType(getContentType(((HttpServletRequest)request).getPathInfo()));
			IOUtils.copy(input,response.getOutputStream());
		}
	}

	private String getContentType(final String pathInfo)
	{
		if (pathInfo.endsWith(".css"))
			return "text/css";
		else if (pathInfo.endsWith(".js"))
			return "text/javascript";
		else if (pathInfo.endsWith(".gif"))
			return "image/gif";
		else if (pathInfo.endsWith(".eot"))
			return "application/vnd.ms-fontobject";
		else if (pathInfo.endsWith(".svg"))
			return "image/svg+xml";
		else if (pathInfo.endsWith(".ttf"))
			return "font/ttf";
		else if (pathInfo.endsWith(".woff"))
			return "application/font-woff";
		else
			return null;
	}
}
