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

import org.dynami.core.config.Config;
import org.dynami.core.utils.StateMachine;

import java.util.ArrayList;
import java.util.List;

public abstract class Service implements IService {
	public static enum State implements StateMachine.IState {
		Inactive,
		Initialized,
		Started,
		Paused,
		Stopped
		;

		private final List<StateMachine.IState> children = new ArrayList<>();
		@Override
		public List<StateMachine.IState> children() {
			return children;
		}

		public String toString(){
			return this.name();
		}
	}

	final StateMachine stateMachine = new StateMachine(()->{
		State.Inactive.addChildren(State.Initialized);
		State.Initialized.addChildren(State.Started, State.Stopped);
		State.Started.addChildren(State.Paused, State.Stopped);
		State.Paused.addChildren(State.Started, State.Stopped);

		return State.Inactive;
	});

	@Override
	public <T extends Config> boolean init(T config) throws Exception {
		//System.out.println("IService::init() status "+stateMachine.getCurrentState());
		return stateMachine.changeState(State.Initialized);
	};

	@Override
	public boolean start(){
		return stateMachine.changeState(State.Started);
	};

	@Override
	public boolean stop(){
		return stateMachine.changeState(State.Paused);
	};

	@Override
	public boolean resume(){
		return stateMachine.changeState(State.Started);
	};

	@Override
	public boolean dispose(){
		System.out.println("IService.dispose()");
		return stateMachine.changeState(State.Stopped);
	};

	@Override
	public boolean reset(){return true;};

	@Override
	public boolean isDisposed(){return stateMachine.getCurrentState().equals(State.Stopped);};
}
