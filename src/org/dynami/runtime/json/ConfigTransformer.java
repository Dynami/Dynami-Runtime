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
package org.dynami.runtime.json;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Map;

import org.dynami.core.config.Config;

import flexjson.ChainedSet;
import flexjson.JSONContext;
import flexjson.ObjectBinder;
import flexjson.ObjectFactory;
import flexjson.transformer.AbstractTransformer;

public class ConfigTransformer extends AbstractTransformer implements ObjectFactory {

	@Override
	public void transform(final Object obj) {
		JSONContext context = getContext();
		ChainedSet visits = context.getVisits();
		try {
			if (!visits.contains(obj)) {
				context.setVisits(new ChainedSet(visits));
                context.getVisits().add(obj);
                context.writeOpenObject();
                
                Field[] fields = obj.getClass().getDeclaredFields();
                boolean first = true;
                for(Field f: fields){
                	if(f.getAnnotation(Config.Param.class) != null){
                		if(!first){
                			context.writeComma();
                		}
                		context.writeName(f.getName());
                		f.setAccessible(true);
                		
                		Object p = f.get(obj);
                		context.getTransformer(p).transform(p);
                		first = false;
                	}
                }
                context.writeCloseObject();
                context.setVisits((ChainedSet) context.getVisits().getParent());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked"})
	public Object instantiate(ObjectBinder context, Object value, Type targetType, Class targetClass) {
		try {
			final Map<String, Object> data = (Map<String, Object>)value;
			Object out = targetClass.newInstance();
			
			Field[] fields = targetClass.getDeclaredFields();
			for(Field f:fields){
				if(f.getAnnotation(Config.Param.class) != null){
					f.setAccessible(true);
					f.set(out, data.get(f.getName()));
//					final Method m = targetClass.getDeclaredMethod(setter(f.getName()), f.getType());
//					if(m != null){
//						m.invoke(out, data.get(f.getName()));
//					}
				}
			}
			return out;
			
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
//	private static String getter(String fieldName, boolean isBoolean){
//		char[] cs =fieldName.toCharArray();
//		cs[0] = Character.toUpperCase(cs[0]);
//		return  ((isBoolean)?"is":"get")+ (new String(cs));
//	}
	
//	private static String setter(String input){
//		char[] tmp = input.toCharArray();
//		tmp[0] = Character.toUpperCase(tmp[0]);
//		return "set".concat(new String(tmp));
//	}

}
