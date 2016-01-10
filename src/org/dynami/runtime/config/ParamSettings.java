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
package org.dynami.runtime.config;

import org.dynami.core.config.Config.Type;

public class ParamSettings {
	private String paramName;
//	private Class<?> paramType;
	private String fieldName;
	private ParamValue paramValue;
	private String description;
	private double min = 0;
	private double max =  Double.MAX_VALUE;
	private double step = 0.1;
	private Type innerType = Type.JavaType;
	private String[] possibileValues = {};
	
	public ParamSettings() {}
	
	public String getName(){
		return (paramName != null && !paramName.equals(""))?paramName:fieldName;
	}

	public String getParamName() {
		return paramName;
	}

	public void setParamName(String paramName) {
		this.paramName = paramName;
	}

	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	public ParamValue getParamValue() {
		return paramValue;
	}

	public void setParamValue(ParamValue paramValue) {
		this.paramValue = paramValue;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public double getMin() {
		return min;
	}

	public void setMin(double min) {
		this.min = min;
	}

	public double getMax() {
		return max;
	}

	public void setMax(double max) {
		this.max = max;
	}

	public double getStep() {
		return step;
	}

	public void setStep(double step) {
		this.step = step;
	}

	public Type getInnerType() {
		return innerType;
	}

	public void setInnerType(Type innerType) {
		this.innerType = innerType;
	}

	public String[] getPossibileValues() {
		return possibileValues;
	}

	public void setPossibileValues(String[] possibileValues) {
		this.possibileValues = possibileValues;
	}

//	public Class<?> getParamType() {
//		return paramType;
//	}
//	public void setParamType(Class<?> paramType) {
//		this.paramType = paramType;
//	}
}
