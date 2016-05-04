/*
 * Copyright 2016 Alessandro Atria - a.atria@gmail.com
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
package org.dynami.runtime.plot;

import org.dynami.core.plot.Plot;

public class PlottableObject {
	public final Plot meta;
	public final String name;
	public final Object source;
	
	public PlottableObject(String name, Plot meta, Object source){
		if(meta.name().equals("")){
			this.name = name;
		} else {
			this.name = meta.name();
		}
		this.meta = meta;
		this.source = source;
	}
	
	public String key() {
		return meta.on()+"."+name;
	}
}
