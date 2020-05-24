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

import org.apache.cxf.common.logging.LogUtils;

import lombok.AccessLevel;
import lombok.val;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level=AccessLevel.PROTECTED, makeFinal=true)
public class StartGB extends Start
{
	public static void main(String[] args) throws Exception
	{
		LogUtils.setLoggerClass(org.apache.cxf.common.logging.Slf4jLogger.class);
		val app = new StartGB();
		app.startService(args);
	}
	
	@Override
	protected String[] getConfigLocations()
	{
		return new String[]{"classpath:org/bitbucket/eluinstra/fs/service/applicationContext.xml","classpath:org/bitbucket/eluinstra/digikoppeling/gb/processor.xml"};
	}
}
