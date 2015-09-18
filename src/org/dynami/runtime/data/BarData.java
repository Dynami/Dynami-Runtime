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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.dynami.core.data.Bar;
import org.dynami.core.data.IData;
import org.dynami.core.data.Series;

public class BarData implements IData {
	
	private final int BUFFER_SIZE = 1024;
	private List<Bar> data = new ArrayList<>(BUFFER_SIZE);
	private long max = 0L, min = Long.MAX_VALUE;
	private long compression = COMPRESSION_UNIT.MINUTE;
	
	public BarData(){};
	
	private BarData(Collection<Bar> bars, long compression){
		data.addAll(bars);
		max = data.stream()
				.mapToLong(Bar::getHigh)
				.max().orElse(0L);
		min = data.stream()
				.mapToLong(Bar::getLow)
				.min().orElse(Long.MAX_VALUE);
		this.compression = compression;
	}
	
	public void append(final Bar bar){
		Lock lock = new ReentrantLock();
		try {
			lock.lock();
			data.add(bar);
			if(max < bar.high) max = bar.high;
			if(min > bar.low) min = bar.low;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Series open() {
		return new Series(
				data.stream()
				.mapToLong(Bar::getOpen)
				.toArray());
	}

	@Override
	public Series high() {
		return new Series(
				data.stream()
				.mapToLong(Bar::getHigh)
				.toArray());
	}

	@Override
	public Series low() {
		return new Series(
				data.stream()
				.mapToLong(Bar::getLow)
				.toArray());
	}

	@Override
	public Series close() {
		return new Series(
				data.stream()
				.mapToLong(Bar::getClose)
				.toArray());
	}

	@Override
	public Series volume() {
		return new Series(
				data.stream()
				.mapToLong(Bar::getVolume)
				.toArray());
	}

	@Override
	public Series openInterest() {
		return new Series(
				data.stream()
				.mapToLong(Bar::getOpenInterest)
				.toArray());
	}

	@Override
	public long getMax() {
		return max;
	}

	@Override
	public long getMin() {
		return min;
	}

	@Override
	public long getBegin() {
		return data.get(0).time;
	}

	@Override
	public long getEnd() {
		return data.get(data.size()-1).time;
	}

	@Override
	public Bar last() {
		return data.get(data.size()-1);
	}

	@Override
	public Bar get(int idx) {
		return data.get(idx);
	}

	@Override
	public Bar[] toArray() {
		Bar[] out = new Bar[data.size()];
		out = data.toArray(out);
		return out;
	}

	@Override
	public int size() {
		return data.size();
	}

	@Override
	public Bar getByTime(long time) {
		return data.stream()
				.filter(b-> b.time == time)
				.findFirst().get();
	}

	@Override
	public IData getPeriod(long begin, long end) {
		List<Bar> bars = data.stream()
				.filter(b-> b.time >= begin)
				.filter(b-> b.time <= end)
				.collect(Collectors.toList());
		
		return new BarData(bars, getCompression());
	}
	
	@Override
	public long getCompression() {
		return compression;
	}
	
	
	public IData changeCompression(long compression){
		final long currentCompression = this.compression;
		final long newCompression = compression;
		if(newCompression%currentCompression == 0 && newCompression > currentCompression){
			final BarBuilder composer = new BarBuilder();
			long _currentUnits = 0, _previousUnits = 0;
			BarData out = new BarData();
			out.compression = newCompression;
			
			for(Bar bar : data){
				_currentUnits = (bar.time/newCompression);
				// at the first iteration set previous units with the same value of current to avoid a fake bar; 
				if(_previousUnits == 0) _previousUnits = _currentUnits;
				
				if(_currentUnits == _previousUnits ){
					composer.pop(bar);
				} else {
					_previousUnits = _currentUnits;
					out.append(composer.close());
					composer.pop(bar);
				}
			}
			
			if(!composer.empty)out.append(composer.close());
			return out;
		
		} else {
			return null;
		}
	}
}
