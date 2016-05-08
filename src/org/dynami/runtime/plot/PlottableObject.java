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

import java.util.ArrayList;
import java.util.List;

import org.dynami.core.ITechnicalIndicator;
import org.dynami.core.plot.Plot;

public class PlottableObject {
	public final Plot meta;
	public final String name;
	private final List<String> keys = new ArrayList<>();
	public final Object source;
	
	public PlottableObject(Plot meta, Object source, String _name){
		if(meta.name().equals("")){
			this.name = _name;
		} else {
			this.name = meta.name();
		}			
		if(source instanceof ITechnicalIndicator){
			String[] seriesNames = ((ITechnicalIndicator)source).seriesNames();
			for(int i = 0; i < seriesNames.length; i++){
				this.keys.add(meta.on()+"."+name+"."+seriesNames[i]);
			}
		} else {
			this.keys.add(meta.on()+"."+name);
		}
		this.meta = meta;
		this.source = source;
	}
	
	public String on(){
		return meta.on();
	}
	
	public List<String> keys() {
		return keys;
	}
}
