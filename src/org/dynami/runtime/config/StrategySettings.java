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
	private ClassSettings strategy;
	private Map<String, ClassSettings> stages = new HashMap<>();
	
	public ClassSettings getStrategy() {
		return strategy;
	}
	
	public void setStrategy(ClassSettings strategy) {
		this.strategy = strategy;
	}
	
	public Map<String, ClassSettings> getStages() {
		return stages;
	}
	public void setSettings(Map<String, ClassSettings> stages) {
		this.stages = stages;
	}
	public ClassSettings getStageSettings(String clazz){
		return stages.get(clazz);
	}
	
	public Map<String, ClassSettings> getStagesSettings(){
		return stages;
	}
	
	public void merge(StrategySettings _settings){
		strategy.merge(_settings.getStrategy());
		stages.keySet().forEach(k->{
			final ClassSettings _cs = _settings.getStageSettings(k);
			if(_cs != null){
				stages.get(k).merge(_cs);
			}
		});
	}
}
