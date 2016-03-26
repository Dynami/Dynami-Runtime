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
package org.dynami.runtime.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

public class PrioritizedMap<K, V> extends ConcurrentSkipListMap<K, V> {
	private static final long serialVersionUID = -351108850280150703L;
	private List<PMEntry<K, V>> pairs = new ArrayList<PMEntry<K, V>>();

	public PrioritizedMap(){
	}

	public V put(int prioriry, K key, V value){
		final V out = super.put(key, value);
//		final PMEntry<K, V> entry = new PMEntry<K, V>(prioriry, key, value);
//		System.out.println("PrioritizedMap.put() remove previous "+key+"\t "+pairs.remove(entry));
		pairs.add(new PMEntry<K, V>(prioriry, key, value));
		return out;
	}

	public Collection<V> prioritized(){
		return pairs.stream().sorted().map(PMEntry::getValue).collect(Collectors.toList());
	}
}


class PMEntry<K, V> implements Comparable<PMEntry<K,V>> {
	final int priority;
	final K key;
	final V value;

	public PMEntry(final int priority, final K key, final V value) {
		this.priority = priority;
		this.key = key;
		this.value = value;
	}

	@Override
	public int compareTo(PMEntry<K, V> arg0) {
		return Integer.compare(priority, arg0.priority);
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof PMEntry){
			PMEntry<?, ?> p = (PMEntry<?, ?>)obj;
			return key.equals(p.key);
		} else {
			return super.equals(obj);
		}
	}

	public V getValue() {
		return value;
	}
}