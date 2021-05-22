package dev.luin.file.server;

import java.io.IOException;
import java.net.MalformedURLException;

import org.eclipse.jetty.util.resource.Resource;

import lombok.val;

public interface Config
{
	static final String NONE = "<none>";

	default String getHost(String host)
	{
		return "0.0.0.0".equals(host) ? "localhost" : host;
	}

	default Resource getResource(String path) throws MalformedURLException, IOException
	{
		val result = Resource.newResource(path);
		return result.exists() ? result : Resource.newClassPathResource(path);
	}
}
