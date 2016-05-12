package org.dynami.runtime.plot;

import java.lang.reflect.Field;

public class PlottableRef {
	private final PlottableObject po;
	private final Field field;
	
	public PlottableRef(PlottableObject po, Field field){
		this.po = po;
		this.field = field;
	}
	
	public PlottableObject getPO(){
		return po;
	}
	
	public Field getField(){
		return field;
	}
}
