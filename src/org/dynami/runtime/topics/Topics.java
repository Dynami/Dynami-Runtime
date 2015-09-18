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
package org.dynami.runtime.topics;

import org.dynami.core.services.ITraceService;
import org.dynami.runtime.IServiceBus.ServiceStatus;

import javax.swing.text.Position;

import org.dynami.core.Event;
import org.dynami.core.assets.Asset;
import org.dynami.core.assets.Book;
import org.dynami.core.data.Bar;

public enum Topics {
	TRACE("a", ITraceService.Trace.class),
	TICK("b", Book.Orders.class),
	BAR("c", Bar.class),
	INSTRUMENT("d", Asset.class),
	ORDERS_BOOK_PREFIX("e/", Book.Orders.class),
	ERRORS("f", String.class), 
	STRATEGY_ERRORS("g", String.class), 
	STRATEGY_EVENT("h", Event.class),
	SERVICE_STATUS("i", ServiceStatus.class),
	EXECUTED_ORDER("j", Position.class)
	;
	
	public final String topic;
	public final Class<?> msgClass;
	
	private Topics(String topic, Class<?> msgClass){
		this.topic = topic;
		this.msgClass = msgClass;
	}
}
