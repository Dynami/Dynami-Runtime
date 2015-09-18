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
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import org.dynami.core.IStrategy;
import org.dynami.core.descriptors.StrategyDescriptor;
import org.dynami.runtime.json.JSON;

public class StrategyClassLoader extends URLClassLoader {

	private final JarFile jarFile;
	public StrategyClassLoader(String path, ClassLoader parent) throws Exception {
		super(new URL[] { new URL("file:" + path) }, parent);
		this.jarFile = new JarFile(path);
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
	public AddonDescriptor<IStrategy> getAddonDescriptor(){
		AddonDescriptor<IStrategy> addon = null;
		try {
			JarEntry entry;
			String className;
			Class<IStrategy> strategy = null;
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
							if (inf.equals(IStrategy.class) && !Modifier.isAbstract(c.getModifiers())) {
								strategy = (Class<IStrategy>) c;
								break;
							}
						}
					} catch (Error er) {
						er.printStackTrace();
					}
				}
			}

			if (strategy != null) {
				ZipEntry descriptorEntry = jarFile.getEntry("META-INF/"+ StrategyDescriptor.FILE_NAME);
				if (descriptorEntry == null) {
					return addon;
				}
				addon = new AddonDescriptor<IStrategy>(strategy);
				addon.setDescriptor(loadStrategyDescriptor(jarFile, descriptorEntry));
				return addon;
			}
		} catch(Exception e){
			e.printStackTrace();
		}
		return addon;
	}
	
	private StrategyDescriptor loadStrategyDescriptor(final JarFile jarFile, final ZipEntry descriptorEntry) throws Exception{
		try(InputStream inputStream = jarFile.getInputStream(descriptorEntry)){
			return JSON.Parser.deserialize(inputStream);
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
	
	public static class AddonDescriptor<T> {
		private Class<T> clazz;
		private StrategyDescriptor descriptor;
		
		public AddonDescriptor(Class<T> clazz) {
			this.clazz = clazz;
		}

		public Class<T> getClazz() {
			return clazz;
		}

		public void setClazz(Class<T> clazz) {
			this.clazz = clazz;
		}

		public StrategyDescriptor getDescriptor() {
			return descriptor;
		}

		public void setDescriptor(StrategyDescriptor descriptor) {
			this.descriptor = descriptor;
		}
	}
}
