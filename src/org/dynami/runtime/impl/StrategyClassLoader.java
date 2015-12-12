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
package org.dynami.runtime.impl;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.dynami.core.IStage;
import org.dynami.core.IStrategy;
import org.dynami.core.config.Config;
import org.dynami.runtime.models.StrategyComponents;

public class StrategyClassLoader extends URLClassLoader {

	private final JarFile jarFile;
	private Class<IStrategy> strategy;
	private List<Class<IStage>> stages = new ArrayList<>();
	private List<Class<?>> configs = new ArrayList<>();
	
	public StrategyClassLoader(String path, ClassLoader parent) throws Exception {
		super(new URL[] { new URL("file:" + path) }, parent);
		this.jarFile = new JarFile(path);
		loadDynamiComponents();
	}
	
	public StrategyComponents getStrategyComponents(){
		return new StrategyComponents(jarFile.getName(), strategy, Collections.unmodifiableList(stages), Collections.unmodifiableList(configs));
	}
	
	public Class<IStrategy> getStrategy() {
		return strategy;
	}
	
	public List<Class<IStage>> getStages() {
		return stages;
	}
	
	public Manifest getManifest() throws Exception{
		URL url = findResource("META-INF/MANIFEST.MF");
		if(url != null)
			return new Manifest(url.openStream());
		else 
			return null;
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		try {
			JarEntry entry = jarFile.getJarEntry(name);
			if (entry == null) {
				return null;
			} else {
				// remember opened file
				return jarFile.getInputStream(entry);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	private void loadDynamiComponents() {
		try {
			JarEntry entry;
			String className;
			
			int length;
			for (Enumeration<JarEntry> _enum = jarFile.entries(); _enum.hasMoreElements();) {
				entry = _enum.nextElement();
				if (entry.getName().endsWith(".class")) {
					className = entry.getName();
					length = className.length();
					className = className.substring(0, length - 6).replace('/','.');
					try {
						Class<?> c = loadClass(className);
						Class<?>[] inter = c.getInterfaces();
						for (Class<?> inf : inter) {
							if (this.strategy == null && inf.equals(IStrategy.class) && !Modifier.isAbstract(c.getModifiers())) {
								this.strategy = (Class<IStrategy>) c;
								if(c.isAnnotationPresent(Config.Settings.class)){
									configs.add(c);
								}
							}
							if(inf.equals(IStage.class) && !Modifier.isAbstract(c.getModifiers())){
								stages.add((Class<IStage>)c);
								if(c.isAnnotationPresent(Config.Settings.class)){
									configs.add(c);
								}
							}
						}
					} catch (Error er) {
						er.printStackTrace();
					}
				}
			}
		} catch(Exception e){
			e.printStackTrace();
		}
	}
		
	/** Close references to opened zip files (via getResourceAsStream) */
	public void close() {
		try {
			jarFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
