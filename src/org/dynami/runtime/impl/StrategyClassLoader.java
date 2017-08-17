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
import java.lang.reflect.Field;
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
import org.dynami.runtime.config.ClassSettings;
import org.dynami.runtime.config.ParamSettings;
import org.dynami.runtime.config.ParamValue;
import org.dynami.runtime.config.StrategySettings;
import org.dynami.runtime.models.StrategyComponents;

public class StrategyClassLoader extends URLClassLoader {

	private final JarFile jarFile;
	private Class<IStrategy> strategy;
	private List<Class<IStage>> stages = new ArrayList<>();
	private StrategySettings strategySettings = new StrategySettings();

	public StrategyClassLoader(String path, ClassLoader parent) throws Exception {
		super(new URL[] { new URL("file:" + path) }, parent);
		this.jarFile = new JarFile(path);
		loadDynamiComponents();
	}

	public StrategyComponents getStrategyComponents(){
		return new StrategyComponents(jarFile.getName(), strategy, Collections.unmodifiableList(stages), strategySettings);
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

	private static ClassSettings extractClassSettings(Class<?> clazz) throws Exception {
		ClassSettings classSettings = new ClassSettings();
		Config.Settings settings = clazz.getAnnotation(Config.Settings.class);
		if(settings != null){
			String name = !settings.name().equals("")?settings.name():clazz.getSimpleName();
			String description = settings.description();
			classSettings.setName(name);
			classSettings.setDescription(description);
			classSettings.setType(clazz.getName());
		}
		Field[] fields = clazz.getDeclaredFields();
		Object obj = clazz.newInstance();
		for(Field f:fields){
			Config.Param p = f.getAnnotation(Config.Param.class);
			if(p != null){
				ParamSettings paramSettings = new ParamSettings();
				f.setAccessible(true);
//				paramSettings.setParamType(f.getType());
				paramSettings.setParamName(p.name());
				paramSettings.setFieldName(f.getName());
				paramSettings.setParamValue(new ParamValue(f.getType(), f.get(obj)));
				paramSettings.setDescription(p.description());
				paramSettings.setMax(p.max());
				paramSettings.setMin(p.min());
				paramSettings.setStep(p.step());
				paramSettings.setInnerType(p.type());
				paramSettings.setPossibileValues(p.values());
				classSettings.getParams().put(f.getName(), paramSettings);
			}
		}
		return classSettings;
	}

	@SuppressWarnings("unchecked")
	private void loadDynamiComponents() throws Exception {
		JarEntry entry;
		String className;

		int length;
		for (Enumeration<JarEntry> _enum = jarFile.entries(); _enum.hasMoreElements();) {
			entry = _enum.nextElement();
			if (entry.getName().endsWith(".class")) {
				className = entry.getName();
//				System.out.println("StrategyClassLoader.loadDynamiComponents()	"+className);
				length = className.length();
				className = className.substring(0, length - 6).replace('/','.');
				Class<?> c = loadClass(className);
				Class<?>[] inter = c.getInterfaces();
				for (Class<?> inf : inter) {
					if (this.strategy == null && inf.equals(IStrategy.class) && !Modifier.isAbstract(c.getModifiers())) {
						this.strategy = (Class<IStrategy>) c;
						ClassSettings classSettings = extractClassSettings(c);
						strategySettings.setStrategy(classSettings);
						break;
					} else if(inf.equals(IStage.class) && !Modifier.isAbstract(c.getModifiers())){
						stages.add((Class<IStage>)c);
						ClassSettings classSettings = extractClassSettings(c);
						strategySettings.getStagesSettings().put(c.getName(), classSettings);
					}
				}
			}
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
