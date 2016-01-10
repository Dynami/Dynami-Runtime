package org.dynami.runtime.config;

public class ParamValue {
	private Class<?> type;
	private Object value;
	
	public ParamValue() {}
	
	public ParamValue(Class<?> type, Object value){
		this.type = type;
		this.value = value;
	}
	
	public Class<?> getType() {
		return type;
	}
	public void setType(Class<?> type) {
		this.type = type;
	}
	public Object getValue() {
		return value;
	}
	public void setValue(Object value) {
		this.value = value;
	}
}
