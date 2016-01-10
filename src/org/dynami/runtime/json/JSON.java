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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.Map;

import org.dynami.runtime.config.ParamValue;

import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
import flexjson.ObjectBinder;
import flexjson.ObjectFactory;
import flexjson.transformer.AbstractTransformer;
import flexjson.transformer.DateTransformer;

public enum JSON {
	Parser;
	
	private static final String DATE_FORMAT = "dd-MM-yyyy";
//	private static final String TIME_FORMAT = "HH:mm:ss";
	
	private final JSONSerializer serializer = new JSONSerializer();
	
	public void serialize(File file, Object obj) throws Exception {
		try(FileWriter writer = new FileWriter(file)){
			serializer.prettyPrint(true);
			serializer.deepSerialize(obj, writer);
			writer.flush();
		}
	}
	
	public <T> T deserialize(File file, Class<T> clazz) throws Exception {
		final JSONDeserializer<T> deserializer =  new JSONDeserializer<>();
		deserializer.use(Date.class, new DateTransformer(DATE_FORMAT));
		deserializer.use(Class.class, new JSON.ClassTrasformer());
		deserializer.use(ParamValue.class, new JSON.ParamValueFactory());
		try(FileReader reader = new FileReader(file)){
			return deserializer.deserialize(reader, clazz);
		}
	}
	
	static class ParamValueFactory implements ObjectFactory {
	    public Object instantiate(ObjectBinder context, Object value, Type targetType, Class targetClass) {
	    	ParamValue p = new ParamValue();
	    	Map<String, ?> values = (Map<String, ?>)value;
	    	String t = (String)values.get("type");
	    	Object v = values.get("value");
	    	p.setType(getTypeByString(t));
	    	p.setValue(context.bind(v, p.getType()));
	        return p;
	    }
	}
	
	private static Class<?> getTypeByString(final String className){
		try {
			if("int".equals(className)){
				return int.class;
			} else if("short".equals(className)){
				return short.class;
			} else if("float".equals(className)){
				return float.class;
			} else if("long".equals(className)){
				return long.class;
			} else if("double".equals(className)){
				return double.class;
			} else if("boolean".equals(className)){
				return boolean.class;
			} else if("byte".equals(className)){
				return byte.class;
			} else {
				return Class.forName(className);
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@SuppressWarnings("rawtypes")
	static class ClassTrasformer extends AbstractTransformer implements ObjectFactory {
		@Override
		public Object instantiate(ObjectBinder context, Object value, Type targetType, Class targetClass) {
			return getTypeByString((String)value);
		}
		
		@Override
		public void transform(Object value) {
			if( value == null ) {
	            getContext().write("null");
	            return;
	        }
	        getContext().writeQuoted( ((Class<?>)value).getName() );
		}
	}
}
