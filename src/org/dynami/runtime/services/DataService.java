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
package org.dynami.runtime.services;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import org.dynami.core.Event;
import org.dynami.core.assets.Market;
import org.dynami.core.bus.IMsg;
import org.dynami.core.config.Config;
import org.dynami.core.data.IData;
import org.dynami.core.data.IVolatilityEngine;
import org.dynami.core.data.vola.ParkinsonVolatilityEngine;
import org.dynami.core.services.IDataService;
import org.dynami.core.utils.DTime;
import org.dynami.runtime.Service;
import org.dynami.runtime.data.BarData;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.topics.Topics;

public class DataService extends Service implements IDataService  {
	private final Map<String, BarData> data = new ConcurrentSkipListMap<>();
	private final IMsg msg = Execution.Manager.msg();
	private boolean initialized = false;
	private final IVolatilityEngine engine = new ParkinsonVolatilityEngine();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public boolean reset() {
		data.clear();
		return true;
	}

	@Override
	public boolean dispose() {
		return reset();
	}

	@Override
	public boolean init(Config config) throws Exception {
		System.out.println("DataService.init()");
		data.clear();
		if(!initialized){
			msg.subscribe(Topics.STRATEGY_EVENT.topic, (last, _msg)->{
				Event e = (Event)_msg;
				if(e.is(Event.Type.OnBarClose)){
					data.putIfAbsent(e.bar.symbol, new BarData());
					data.get(e.bar.symbol).append(e.bar);
				}
			});
			initialized = true;
		}
		//return initialized;
		return super.init(config);
	}
	
	public double histVola(String symbol, int units){
		Market market = Execution.Manager.dynami().assets().getMarketBySymbol(symbol);
		return data.get(symbol).getVolatility(engine, units)
				*engine.annualizationFactor(data.get(symbol).getCompression(), units, market);
	}
	
	public double histVola(IVolatilityEngine engine, String symbol, int units){
		Market market = Execution.Manager.dynami().assets().getMarketBySymbol(symbol);
		return data.get(symbol).getVolatility(engine, units)
				*engine.annualizationFactor(data.get(symbol).getCompression(), units, market);
	}

	@Override
	public IData history(String symbol) {
		IData tmp = data.get(symbol);
		assert tmp != null : "No, historical data for symbol "+symbol;
		return tmp;
	}

	@Override
	public IData history(String symbol, long timeFrame, int units) {
		IData tmp = history(symbol);
		assert tmp != null : "No, historical data for symbol "+symbol;
		return tmp.changeCompression(timeFrame*units);
	}

	@Override
	public IData history(String symbol, Date from, Date to) {
		IData tmp = history(symbol);
		assert tmp != null : "No, historical data for symbol "+symbol;
		return tmp.getPeriod(from.getTime(), (to != null)?to.getTime():DTime.Clock.getTime());
	}

	@Override
	public IData history(String symbol, Date from, Date to, long timeFrame, int units) {
		IData tmp = history(symbol);
		assert tmp != null : "No, historical data for symbol "+symbol;
		return tmp
				.getPeriod(from.getTime(), (to != null)?to.getTime():DTime.Clock.getTime())
				.changeCompression(timeFrame*units);
	}
	
	@Override
	public IData history(String symbol, int last) {
		return history(symbol).getLastBars(last);
	}
}
