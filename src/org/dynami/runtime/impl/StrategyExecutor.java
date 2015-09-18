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
package org.dynami.runtime.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.dynami.core.Event;
import org.dynami.core.IDynami;
import org.dynami.core.IStage;
import org.dynami.core.IStrategy;
import org.dynami.core.ITechnicalIndicator;
import org.dynami.core.bus.IMsg;
import org.dynami.core.services.IAssetService;
import org.dynami.core.services.IDataService;
import org.dynami.core.services.IOrderService;
import org.dynami.core.services.IPortfolioService;
import org.dynami.core.services.ITraceService;
import org.dynami.runtime.IServiceBus;
import org.dynami.runtime.IStrategyExecutor;
import org.dynami.runtime.bus.Msg;
import org.dynami.runtime.topics.Topics;

public class StrategyExecutor implements IStrategyExecutor, IDynami {
	private final List<ITechnicalIndicator> technicalIndicators = new ArrayList<>();
	private final AtomicReference<Event> lastIncomingEvent = new AtomicReference<Event>(null);
	private final AtomicReference<Event> lastExecutedEvent = new AtomicReference<Event>(null);
	private final AtomicBoolean endStrategy = new AtomicBoolean(false);
	private IServiceBus serviceBus;
	private IStrategy strategy;
	private IStage stage, previousStage;
	
	private IMsg msg = Msg.Broker;
	
	@Override
	public void setup(IServiceBus serviceBus) {
		this.serviceBus = serviceBus;
	}

	@Override
	public void load(final IStrategy strategy) throws Exception{
		this.strategy = strategy;
		this.stage = strategy.startsWith();
		
		msg.subscribe(Topics.STRATEGY_EVENT.topic, (last, msg)->{
			if(last){
				Event event = (Event)msg;
				lastIncomingEvent.set(event);
				lastExecutedEvent.set( exec(lastIncomingEvent.get()));
			}
		});
		
		this.strategy.onStrategyStart(this);
	}
	
	private synchronized Event exec(Event event){
		
		if(stage != previousStage){
			runOncePerStage(stage);
			previousStage = stage;
		}
		
		stage.process(this, event);
		
		if(endStrategy.get()){
			try {
				msg.unsubscribeAllFor(Topics.STRATEGY_EVENT.topic);
				lastIncomingEvent.set(null);
				lastExecutedEvent.set(null);
				endStrategy.set(false);
				stage = null;
				previousStage = null;
				strategy.onStrategyFinish(this);
			} catch (Exception e) {
				msg.async(Topics.STRATEGY_ERRORS.topic, e);
			}
		}
		return event;
	}
	
	private void runOncePerStage(IStage stage) {
//		tradingSystems.clear();
		technicalIndicators.clear();
		try {
			extractUserUtilities(stage, technicalIndicators);
			stage.setup(this);
		} catch (Exception e) {
			msg.async(Topics.STRATEGY_ERRORS.topic, e);
		}
	}

	private static void extractUserUtilities(Object stage, List<ITechnicalIndicator> techIndicators) throws Exception {
		Field[] fields = stage.getClass().getDeclaredFields();
		Object obj = null;
		for(final Field f:fields){
			f.setAccessible(true);
			obj = f.get(stage);
			if(obj != null && obj instanceof ITechnicalIndicator ){
				techIndicators.add( (ITechnicalIndicator)obj);
			}
		}
	}

	@Override
	public void dispose() {

	}

	@Override
	public IStage getActiveStage() {
		return stage;
	}

	@Override
	public void gotoNextStage(IStage nextStage) throws Exception {
		previousStage = stage;
		stage = nextStage;
	}

	@Override
	public void gotoNextStageNow(IStage nextStage) throws Exception {
		gotoNextStage(nextStage);
		exec(lastExecutedEvent.get());
	}

	@Override
	public void gotoEnd() {
		endStrategy.set(true);
	}

	@Override
	public IOrderService orders() {
		return serviceBus.getService(IOrderService.class, IOrderService.ID);
	}

	@Override
	public IDataService data() {
		return serviceBus.getService(IDataService.class, IDataService.ID);
	}

	@Override
	public IPortfolioService portfolio() {
		return serviceBus.getService(IPortfolioService.class, IPortfolioService.ID);
	}

	@Override
	public ITraceService trace() {
		return serviceBus.getService(ITraceService.class, ITraceService.ID);
	}

	@Override
	public IAssetService assets() {
		return serviceBus.getService(IAssetService.class, IAssetService.ID);
	}
}
