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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.wicket.util.io.IClusterable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
public class MenuItem implements IClusterable
{
	private static final long serialVersionUID = 1L;
	@NonNull
	String id;
	@NonNull
	String name;
	@NonFinal
	MenuItem parent;
	List<MenuItem> children = new ArrayList<>();

	public MenuItem(final MenuItem parent, final String id, final String name)
	{
		this.id = parent.getId() + "." + id;
		this.name = name;
		this.parent = parent;
		this.parent.children.add(this);
	}

	public List<MenuItem> getChildren()
	{
		return Collections.unmodifiableList(children);
	}

	@Override
	public String toString()
	{
		return id;
	}

}
