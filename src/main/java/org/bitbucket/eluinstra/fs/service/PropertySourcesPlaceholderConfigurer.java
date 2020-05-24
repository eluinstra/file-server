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
package org.bitbucket.eluinstra.fs.service;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.val;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level=AccessLevel.PRIVATE)
@Getter
public class PropertySourcesPlaceholderConfigurer extends org.springframework.context.support.PropertySourcesPlaceholderConfigurer
{
	Resource overridePropertiesFile;

	@Override
	public void setLocations(Resource...locations)
	{
		overridePropertiesFile = locations[locations.length - 1];
		super.setLocations(locations);
	}
	
	public Resource getOverridePropertiesFile()
	{
		return overridePropertiesFile;
	}
	
	public Map<String,String> getProperties() throws IOException
	{
		val properties = mergeProperties();
		return properties.entrySet().stream()
				.collect(Collectors.toMap(e -> (String)e.getKey(), e -> (String)e.getValue()));
	}
}
