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

public interface IService {
	public String id();

	public default <T extends Config> boolean init(T config) throws Exception {return true;};

	public default boolean start(){return true;};

	public default boolean stop(){return true;};

	public default boolean resume(){return true;};

	public default boolean dispose(){return true;};

	public default boolean reset(){return true;};

	public default boolean isDisposed(){return true;};

//	public static enum ServiceStatus implements StateMachine.IState {
//		Inactive, Running, Stopped, Disposed;
//
//		private final List<StateMachine.IState> children = new ArrayList<>();
//		@Override
//		public List<StateMachine.IState> children() {
//			return children;
//		}
//	};
}
