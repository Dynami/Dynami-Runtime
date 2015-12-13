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
package org.dynami.runtime.models;

import java.util.List;

import org.dynami.core.IStage;
import org.dynami.core.IStrategy;
import org.dynami.runtime.config.StrategySettings;

public class StrategyComponents {
	public final String jarName;
	public final Class<IStrategy> strategyClass;
	public final List<Class<IStage>> stageClasses;
	public final StrategySettings strategySettings;
	
	public StrategyComponents(String jarName, Class<IStrategy> strategyClass, List<Class<IStage>> stageClasses, StrategySettings strategySettings){
		this.jarName = jarName;
		this.strategyClass = strategyClass;
		this.stageClasses = stageClasses;
		this.strategySettings = strategySettings;
	}
	
	@Override
	public String toString() {
		return strategyClass.getSimpleName();
	}
}
