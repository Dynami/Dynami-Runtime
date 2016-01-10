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
package org.dynami.runtime;

import java.util.ArrayList;
import java.util.List;

import org.dynami.core.IDynami;
import org.dynami.core.bus.IMsg;
import org.dynami.core.config.Config;
import org.dynami.core.utils.StateMachine;
import org.dynami.runtime.config.StrategySettings;
import org.dynami.runtime.impl.StrategyExecutor;
import org.dynami.runtime.topics.Topics;

/**
 * Execution manager is the service delegated to manage the whole Dynami platform, starting and stopping services and user strategy.
 * There can be only one instance of IExecutionManager per java process.
 * @author Atria
 */
public interface IExecutionManager {
	/**
	 * It returns {@link IDynami} initialized interface.
	 * @return {@link IDynami}
	 */
	public IDynami dynami();
	
	/**
	 * Set a custom IStrategyExecutor engine, instead of the default one {@link StrategyExecutor}.
	 * If you want using a different one, invoke this method before invoking IExecutionManager::load();
	 * <pre>Errors are propagated through {@link Topics.ERRORS}<pre>
	 * @param engine
	 * @return true it is all ok, false otherwise
	 * @see IStrategyExecutor
	 * @see StrategyExecutor
	 */
	public boolean setStrategyExecutor(final Class<? extends IStrategyExecutor> engine);


	public boolean init(Config config);

	/**
	 * Load and parse Strategy instance file and set strategyJarBasePath for deploying the jar dynamically.
	 * <br>Refer to {@link IExecutionManager.State} for the proper sequence in invoking methods
	 * <pre>Errors are propagated through {@link MsgTopics.ERRORS}.<pre>
	 * @param strategyInstanceFilePath
	 * @param strategyJarPath
	 * @return true if all is ok, false otherwise.
	 */
	public boolean select(String strategyInstanceFilePath, String strategyJarPath);
	
	/**
	 * 
	 * @param settings
	 * @param strategyJarPath
	 * @return
	 */
	public boolean select(StrategySettings settings, String strategyJarPath);

	/**
	 * Dynamically load user strategy
	 * <br>Refer to {@link IExecutionManager.State} for the proper sequence in invoking methods
	 * <pre>Errors are propagated through {@link Topics.ERRORS}.<pre>
	 * @return
	 */
	public boolean load();

	/**
	 * Starts strategy execution
	 * <br>Refer to {@link IExecutionManager.State} for the proper sequence in invoking methods
	 * <pre>Errors are propagated through {@link Topics.ERRORS}.<pre>
	 * @return
	 */
	public boolean run();

	/**
	 * Pauses strategy execution
	 * <br>Refer to {@link IExecutionManager.State} for the proper sequence in invoking methods
	 * <pre>Errors are propagated through {@link Topics.ERRORS}.<pre>
	 * @return
	 */
	public boolean pause();

	/**
	 * Stops strategy execution. Stop status is a final point. If you want to start again the strategy, you have to start from the beginning.
	 * <br>Refer to {@link IExecutionManager.State} for the proper sequence in invoking methods
	 * <pre>Errors are propagated through {@link Topics.ERRORS}.<pre>
	 * @return
	 */
	public boolean stop();

	/**
	 * This function indicates whether the strategy has been loaded or not.
	 * E.g. If the strategy is running, isLoaded() return true, because has been already loaded.
	 * <br>Refer to {@link IExecutionManager.State} for the proper sequence in invoking methods
	 * @return true whether the strategy has been loaded, false otherwise
	 */
	public boolean isLoaded();

	/**
	 * This function return true if the strategy has been started, also if it is currently paused.
	 * <br>Refer to {@link IExecutionManager.State} for the proper sequence in invoking methods
	 * @return true whether the stategy has been started, false otherwise
	 */
	public boolean isStarted();

	/**
	 * This function returns true if and only if it is currently running.
	 * <br>Refer to {@link IExecutionManager.State} for the proper sequence in invoking methods
	 * @return return true if the ExecutionManager is in status Running, false otherwise
	 */
	public boolean isRunning();

	/**
	 * This function returns true if the strategy has been already initialized
	 * <br>Refer to {@link IExecutionManager.State} for the proper sequence in invoking methods
	 * @return
	 */
	public boolean isSelected();

	/**
	 * Dynamically adds ChangeStateListeners on IExecutionManager.
	 * @param listener
	 * @see StateMachine.ChangeStateListener
	 */
	public void addStateListener(final StateMachine.ChangeStateListener listener);


	public boolean canMoveTo(State state);
	
	public void dispose();

	/**
	 * Retrieves the service bus, which handles Dynami's services life cycle.
	 * @return {@link IServiceBus}
	 */
	public IServiceBus getServiceBus();

	public IMsg msg();

	/**
	 * Describes Dynami's ExecutionManager status.
	 * Status are linked in a work-flow in {@link StateMachine} constructor.
	 * @author Atria
	 */
	public static enum State implements StateMachine.IState {
		NonActive,
		Selected,
		Initialized,
		Loaded,
		Running,
		Paused,
		Stopped
		;

		private final List<StateMachine.IState> children = new ArrayList<>();
		@Override
		public List<StateMachine.IState> children() {
			return children;
		}
	}
}
