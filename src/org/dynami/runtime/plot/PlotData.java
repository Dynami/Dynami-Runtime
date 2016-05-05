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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.dynami.core.data.Bar;

public class PlotData {
	public final Bar bar;
	private final Set<Item> data = new HashSet<>();
	
	public PlotData(Bar bar){
		this.bar = bar;
	}
	
	public void addData(Item item){
		data.add(item);
	}
	
	public Set<Item> data(){
		return Collections.unmodifiableSet(data);
	}
	
	public static final class Item implements Comparable<Item> {
		
		public final String key;
		public final double value;
		
		public Item(String key, double data){
			this.key = key;
			this.value = data;
		}
		
		@Override
		public int compareTo(Item o) {
			return key.compareTo(o.key);
		}

		@Override
		public int hashCode() {
			return key.hashCode();
		}
		
		

		@Override
		public String toString() {
			return "{key:" + key + ", value:" + value + "}";
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Item other = (Item) obj;
			if (key == null) {
				if (other.key != null)
					return false;
			} else if (!key.equals(other.key))
				return false;
			return true;
		}
		
	}
}
