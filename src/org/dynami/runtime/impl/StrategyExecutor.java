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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.dynami.core.Event;
import org.dynami.core.IDynami;
import org.dynami.core.IStage;
import org.dynami.core.IStrategy;
import org.dynami.core.ITechnicalIndicator;
import org.dynami.core.bus.IMsg.Handler;
import org.dynami.core.config.Config;
import org.dynami.core.services.IAssetService;
import org.dynami.core.services.IDataService;
import org.dynami.core.services.IOrderService;
import org.dynami.core.services.IPortfolioService;
import org.dynami.core.services.ITraceService;
import org.dynami.core.utils.DUtils;
import org.dynami.runtime.IServiceBus;
import org.dynami.runtime.IStrategyExecutor;
import org.dynami.runtime.config.ClassSettings;
import org.dynami.runtime.config.StrategySettings;
import org.dynami.runtime.topics.Topics;

public class StrategyExecutor implements IStrategyExecutor, IDynami, Handler {
	private final List<ITechnicalIndicator> technicalIndicators = new ArrayList<>();
	private final AtomicReference<Event> lastIncomingEvent = new AtomicReference<Event>(null);
	private final AtomicReference<Event> lastExecutedEvent = new AtomicReference<Event>(null);
	private final AtomicBoolean endStrategy = new AtomicBoolean(false);
	private final Map<Class<? extends IStage>, Event.Type[]> eventFilters = new ConcurrentHashMap<>();
	private final Map<Class<? extends IStage>, String[]> symbolFilters = new ConcurrentHashMap<>();

//	private Event.Type[] eventFilter = {};
//	private String[] symbolFilter = {};

	private IServiceBus serviceBus;
	private IStrategy strategy;
	private StrategySettings strategySettings;
	private IStage stage, previousStage = null;

	@Override
	public void setup(IServiceBus serviceBus) {
		this.serviceBus = serviceBus;
	}

	@Override
	public void load(final IStrategy strategy, final StrategySettings strategySettings) throws Exception{
		this.strategy = strategy;
		this.stage = strategy.startsWith();
		this.strategySettings = (strategySettings != null)?strategySettings:new StrategySettings();
		Execution.Manager.msg().subscribe(Topics.STRATEGY_EVENT.topic, this);
		ClassSettings classSettings = this.strategySettings.getStrategy();
		if(classSettings != null){
			applySettings(strategy, classSettings);
		}
		this.strategy.onStrategyStart(this);
	}

	@Override
	public void update(boolean last, Object msg) {
		if(last){
			Event event = (Event)msg;
			lastIncomingEvent.set(event);
			lastExecutedEvent.set(exec(lastIncomingEvent.get()));
		}
	}

	private synchronized Event exec(Event event){
		if(stage != previousStage){
			runOncePerStage(stage);
			previousStage = stage;
		}
		try {
			final Event.Type[] eventFilter = eventFilters.get(stage.getClass());
			final String[] symbolFilter = symbolFilters.get(stage.getClass());

			if(event.isOneOfThese(eventFilter)){
				if(symbolFilter.length > 0 ){
					if(DUtils.in(event.symbol, symbolFilter)){
						stage.process(this, event);
					}
				} else {
					stage.process(this, event);
				}
			}
		} catch (Exception e) {
			Execution.Manager.msg().async(Topics.STRATEGY_ERRORS.topic, e);
		}

		if(endStrategy.get()){
			try {
				Execution.Manager.msg().unsubscribeAllFor(Topics.STRATEGY_EVENT.topic);
				lastIncomingEvent.set(null);
				lastExecutedEvent.set(null);
				endStrategy.set(false);
				stage = null;
				previousStage = null;
				strategy.onStrategyFinish(this);
			} catch (Exception e) {
				Execution.Manager.msg().async(Topics.STRATEGY_ERRORS.topic, e);
			}
		}
		return event;
	}

	private void runOncePerStage(IStage stage) {
		technicalIndicators.clear();
		try {
			extractUserUtilities(stage, technicalIndicators, eventFilters, symbolFilters);
			ClassSettings classSettings = strategySettings.getStageSettings(stage.getClass().getName());
			if(classSettings != null){
				applySettings(stage, classSettings);
			}
			stage.setup(this);
		} catch (Exception e) {
			Execution.Manager.msg().async(Topics.STRATEGY_ERRORS.topic, e);
		}
	}

	private static void extractUserUtilities(final IStage stage,
			final List<ITechnicalIndicator> techIndicators,
			final Map<Class<? extends IStage>, Event.Type[]> eventFilters,
			final Map<Class<? extends IStage>, String[]> symbolFilters) throws Exception {

		final Class<? extends IStage> clazz= stage.getClass();
		final Field[] fields = clazz.getDeclaredFields();
		Object obj = null;
		for(final Field f:fields){
			f.setAccessible(true);
			obj = f.get(stage);
			if(obj != null && obj instanceof ITechnicalIndicator ){
				techIndicators.add( (ITechnicalIndicator)obj);
			}
		}

		Method[] methods = clazz.getDeclaredMethods();
		for(final Method m : methods){
			IStage.Filter f = m.getAnnotation(IStage.Filter.class);
			if(m.getName().equals("process")){
				if(f != null){
					eventFilters.put(clazz, f.event());
					symbolFilters.put(clazz, f.symbol());
				} else {
					eventFilters.put(clazz, Event.Type.values());
					symbolFilters.put(clazz, new String[0]);
				}
			}
		}
	}

	@Override
	public void dispose() {
		Execution.Manager.msg().unsubscribe(Topics.STRATEGY_EVENT.topic, this);
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

	private static void applySettings(final Object parent, final ClassSettings classSettings) throws Exception{
		final Field[] fields = parent.getClass().getDeclaredFields();
		for(Field f:fields){
			if(f.isAnnotationPresent(Config.Param.class)){
				Object value = classSettings.getParams().get(f.getName()).getParamValue().getValue();
				f.setAccessible(true);
				f.set(parent, value);
			}
		}
	}
}
