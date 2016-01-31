package org.dynami.runtime;

public interface IDataHandler {
	public static final String ID = "IDataHandler";

	public default String getName(){
		return this.getClass().getSimpleName();
	}

	public void reset();
}
