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
package org.dynami.runtime.data;

public abstract class IDataReciver {
//	private final IMsg msg = Msg.Broker;
	{
		// acquires data from an external source
		
		// sends data using IMsg services
		
	}
	
	/**
	 * Acquire new financial instrument to be registered on dynami
	 * @param family
	 * @param market
	 * @param symbol
	 * @param isin
	 * @param name
	 * @param pointValue
	 * @param tick
	 * @param requiredMargin
	 * @param parentSymbol
	 * @param expire
	 * @param strike
	 * @param type
	 */
	public abstract void onInstr(
			int family, 
			String market, 
			String symbol, 
			String isin, 
			String name, 
			long pointValue, 
			long tick, 
			long requiredMargin,
			String parentSymbol,
			long expire,
			long strike,
			int type);
	
	public abstract void onBar();
	
	public abstract void onTick(String symbolId, long time, int side, int level, long price);
	
	public final static class Side {
		public static final int Ask = 0;
		public static final int Bid = 1;
	}
}
