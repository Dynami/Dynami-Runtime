/*
 * Copyright 2015 Alessandro Atria - a.atria@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dynami.runtime.config;

import java.util.HashMap;
import java.util.Map;

public class ClassSettings {
	private String settingsClassName;
	private String name;
	private String description;
	private final Map<String, ParamSettings> params = new HashMap<>();
	
	public String getSettingsClassName() {
		return settingsClassName;
	}
	public void setSettingsClassName(String settingsClassName) {
		this.settingsClassName = settingsClassName;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	
	public Map<String, ParamSettings> getParams() {
		return params;
	}
//	public void setParams(Map<String, ParamSettings> params) {
//		this.params = params;
//	}
}
