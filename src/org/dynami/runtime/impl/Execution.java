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

import java.io.File;

import org.dynami.core.IDynami;
import org.dynami.core.IStrategy;
import org.dynami.core.bus.IMsg;
import org.dynami.core.config.Config;
import org.dynami.core.utils.StateMachine;
import org.dynami.core.utils.StateMachine.ChangeStateListener;
import org.dynami.runtime.IExecutionManager;
import org.dynami.runtime.IServiceBus;
import org.dynami.runtime.IStrategyExecutor;
import org.dynami.runtime.bus.Msg2;
import org.dynami.runtime.config.StrategySettings;
import org.dynami.runtime.json.JSON;
import org.dynami.runtime.topics.Topics;

public enum Execution implements IExecutionManager {
	Manager;

	private final IServiceBus serviceBus = new ServiceBus();
	private IStrategyExecutor engine = new StrategyExecutor();
	private StrategySettings strategySettings;
	private String strategyJarPath;
	private IDynami dynami = (IDynami)engine;


	private final StateMachine stateMachine = new StateMachine(()->{
		State.NonActive.addChildren(State.Selected, State.Initialized);
		State.Selected.addChildren(State.Initialized);
		State.Initialized.addChildren(State.Loaded);
		State.Loaded.addChildren(State.Running, State.Stopped);
		State.Running.addChildren(State.Paused, State.Stopped);
		State.Paused.addChildren(State.Running, State.Stopped);
		State.Stopped.addChildren(State.NonActive);
		return State.NonActive;
	});

	@Override
	public IDynami dynami() {
		return dynami;
	}

	@Override
	public IMsg msg() {
		return Msg2.Broker;
	}

	@Override
	public boolean setStrategyExecutor(Class<? extends IStrategyExecutor> engineClazz) {
		try {
			System.out.println("Execution.setStrategyExecutor()");
			this.engine = engineClazz.newInstance();
			this.dynami = (IDynami)engine;
		} catch (Exception e) {
			msg().async(Topics.INTERNAL_ERRORS.topic, e);
			return false;
		}
		return true;
	}

	@Override
	public boolean init(Config config) {

		if(stateMachine.canChangeState(State.Initialized)){
			if(serviceBus.initServices(config)){
				return stateMachine.changeState(State.Initialized);
			}
		}
		return false;
	}

	@Override
	public boolean select(StrategySettings settings, String strategyJarPath) {
		try {
			if(stateMachine.canChangeState(State.Selected)){
				this.strategySettings = settings;
				this.strategyJarPath = strategyJarPath;
				return stateMachine.changeState(State.Selected);
			} else {
				return false;
			}
		} catch (Exception e) {
			msg().async(Topics.INTERNAL_ERRORS.topic, e);
			return false;
		}
	}

	@Override
	public boolean select(String strategyInstanceFilePath, String strategyJarPath) {
		try {
			if(stateMachine.canChangeState(State.Selected)){
				if(strategyInstanceFilePath != null && !strategyInstanceFilePath.equals("")){
					strategySettings = JSON.Parser.deserialize(new File(strategyInstanceFilePath), StrategySettings.class);
				}
				this.strategyJarPath = strategyJarPath;
				return stateMachine.changeState(State.Selected);
			} else {
				return false;
			}
		} catch (Exception e) {
			msg().async(Topics.INTERNAL_ERRORS.topic, e);
			return false;
		}
	}

	@Override
	public boolean load() {
		if(stateMachine.canChangeState(State.Loaded)){
			try {
				try(StrategyClassLoader loader = new StrategyClassLoader(strategyJarPath, getClass().getClassLoader())){
					final IStrategy strategy = loader.getStrategy().newInstance();

					engine.setup(serviceBus);
					engine.load(strategy, strategySettings);
				}
				return stateMachine.changeState(State.Loaded);
			} catch (Exception e) {
				msg().async(Topics.INTERNAL_ERRORS.topic, e);
				return false;
			}
		} else {
			return false;
		}
	}

	@Override
	public boolean run() {
		if(stateMachine.canChangeState(State.Running)){
			if(serviceBus.startServices()){
				return stateMachine.changeState(State.Running);
			}
			return false;
		} else {
			return false;
		}
	}

	@Override
	public boolean pause() {
		if(stateMachine.canChangeState(State.Paused)){
			if(serviceBus.stopServices()){
				return stateMachine.changeState(State.Paused);
			}
			return false;
		} else {
			return false;
		}
	}

	@Override
	public boolean stop() {
		if(stateMachine.canChangeState(State.Stopped) && serviceBus.stopServices()){
			return stateMachine.changeState(State.Stopped);
		} else {
			return false;
		}
	}

	@Override
	public boolean dispose() {
		if(stateMachine.canChangeState(State.NonActive)){
			serviceBus.disposeServices();
			stateMachine.changeState(State.NonActive);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean isLoaded() {
		return stateMachine.getCurrentState().ordinal() >= State.Loaded.ordinal();
	}

	@Override
	public boolean isStarted() {
		return stateMachine.getCurrentState().in(State.Running, State.Paused);
	}

	@Override
	public boolean isRunning() {
		return stateMachine.getCurrentState().equals(State.Running);
	}

	@Override
	public boolean isSelected() {
		return stateMachine.getCurrentState().in(
				State.Initialized,
				State.Loaded,
				State.Running,
				State.Paused);
	}

	@Override
	public void addStateListener(ChangeStateListener listener) {
		stateMachine.addListener(listener);
	}

	@Override
	public boolean canMoveTo(State state) {
		return stateMachine.canChangeState(state);
	}

	@Override
	public IServiceBus getServiceBus() {
		return serviceBus;
	}
}
