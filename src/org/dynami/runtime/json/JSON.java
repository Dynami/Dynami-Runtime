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
import java.util.Date;

import flexjson.JSONDeserializer;
import flexjson.JSONSerializer;
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
		JSONDeserializer<T> deserializer =  new JSONDeserializer<>();
		deserializer.use(Date.class, new DateTransformer(DATE_FORMAT));
		try(FileReader reader = new FileReader(file)){
			return deserializer.deserialize(reader, clazz);
		}
	}
}
