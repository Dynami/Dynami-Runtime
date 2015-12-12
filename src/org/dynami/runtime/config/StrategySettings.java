/*
 * Copyright 2013 Alessandro Atria
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

public class StrategySettings {
	private String name;	
	private String description;
	private Map<Class<?>, ClassSettings> settings = new HashMap<>();
	
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
	public Map<Class<?>, ClassSettings> getSettings() {
		return settings;
	}
	public void setSettings(Map<Class<?>, ClassSettings> settings) {
		this.settings = settings;
	}
	public ClassSettings getClassSettings(Class<?> clazz){
		return settings.get(clazz);
	}
}
