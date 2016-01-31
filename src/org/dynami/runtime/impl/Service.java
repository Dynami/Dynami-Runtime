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

import org.dynami.core.config.Config;
import org.dynami.runtime.IService;

public abstract class Service implements IService {

	@Override
	public boolean init(Config config) throws Exception {
		return true;
	}

	@Override
	public boolean start() {
		return true;
	}

	@Override
	public boolean stop() {
		return true;
	}

	@Override
	public boolean resume() {
		return true;
	}

	@Override
	public boolean dispose() {
		return true;
	}

	@Override
	public boolean isDisposed() {
		return false;
	}

	@Override
	public ServiceStatus getStatus() {
		return null;
	}
}
