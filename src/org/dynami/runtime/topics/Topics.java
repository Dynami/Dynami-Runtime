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

import javax.swing.text.Position;

import org.dynami.core.Event;
import org.dynami.core.assets.Asset;
import org.dynami.core.assets.Book;
import org.dynami.core.orders.OrderRequest;
import org.dynami.core.services.ITraceService;
import org.dynami.runtime.IServiceBus.ServiceStatus;

public enum Topics {
	TRACE("a", ITraceService.Trace.class),
	TICK("b", Book.Orders.class),
//	BAR("c", Bar.class),
	INSTRUMENT("d", Asset.class),
	ASK_ORDERS_BOOK_PREFIX("ea/", Book.Orders.class),
	BID_ORDERS_BOOK_PREFIX("eb/", Book.Orders.class),
	INTERNAL_ERRORS("f", Throwable.class),
	STRATEGY_ERRORS("g", Throwable.class),
	UI_ERRORS("p", Throwable.class),
	STRATEGY_EVENT("h", Event.class),
	SERVICE_STATUS("i", ServiceStatus.class),
	EXECUTED_ORDER("j", Position.class),
	ORDER_REQUESTS("o", OrderRequest.class),
	CANCEL_REQUESTS("c", long.class), //order request id
	;

	public final String topic;
	public final Class<?> msgClass;

	private Topics(String topic, Class<?> msgClass){
		this.topic = topic;
		this.msgClass = msgClass;
	}
}
