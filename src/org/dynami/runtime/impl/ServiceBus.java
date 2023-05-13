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

import java.util.Collection;

import org.dynami.runtime.Service;
import org.dynami.runtime.IServiceBus;
import org.dynami.runtime.utils.PrioritizedMap;

public class ServiceBus implements IServiceBus {

	private final PrioritizedMap<String, Service> services = new PrioritizedMap<>();

	@Override
	public Service registerService(Service service, int priority) {
		return services.put(priority, service.id(), service);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getService(Class<T> service, String ID) {
		return (T)services.get(ID);
	}

	@Override
	public Service getService(String ID) {
		return services.get(ID);
	}

	@Override
	public Collection<Service> getServices() {
		return services.prioritized();
	}
}
