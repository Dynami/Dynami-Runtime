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

import org.dynami.core.data.Bar;

class BarBuilder {
	String symbol= null;
	boolean empty = true;
	final long NONE = -1L;
	long open, high, low, close;
	long volume, openInterest, time;
	
	public BarBuilder() {
		clear();
	}
		
	public void clear(){
		symbol = null;
		open = NONE;
		high = 0; 
		low = Long.MAX_VALUE; 
		close = NONE;
		volume = 0;
		openInterest = 0;
		time = 0;
		empty = true;
	}
		
	public void pop(final Bar bar){
		if(open == NONE){
			symbol = bar.symbol;
			open = bar.open;
			empty = false;
		}
		if(bar.high > high) high = bar.high;
		if(bar.low < low) low = bar.low;
		close = bar.close;
		volume += bar.volume;
		openInterest = bar.openInterest;
		time = bar.time;
	}
	
	public Bar close(){
		try {
			return new Bar(symbol, open, high, low, close, volume, openInterest, time);
		} finally {
			clear();
		}
	}
}
