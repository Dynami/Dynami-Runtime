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
package org.dynami.runtime.orders;

import java.util.List;

import org.dynami.core.assets.Book;
import org.dynami.core.bus.IMsg;
import org.dynami.core.orders.MarketOrder;
import org.dynami.core.orders.OrderRequest;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.topics.Topics;

public class PendingConditions {
	private final OrderRequest request;
	private final List<?> parent;
	private boolean invalidate = false;
	
	public PendingConditions(OrderRequest request, List<?> parent){
		this.request = request;
		this.parent = parent;
		if(request.quantity > 0){
			Execution.Manager.msg().subscribe(Topics.ASK_ORDERS_BOOK_PREFIX.topic+request.symbol, askHandler);
		} else {
			Execution.Manager.msg().subscribe(Topics.BID_ORDERS_BOOK_PREFIX.topic+request.symbol, bidHandler);
		}
	}
	
	public void unsubscribeMe(){
		invalidate = true;
		Execution.Manager.msg().unsubscribe(Topics.BID_ORDERS_BOOK_PREFIX.topic+request.symbol, bidHandler);
		Execution.Manager.msg().unsubscribe(Topics.ASK_ORDERS_BOOK_PREFIX.topic+request.symbol, askHandler);
	}
	
	private final IMsg.Handler bidHandler = new IMsg.Handler(){
		public void update(boolean last, Object msg) {
			if(!invalidate){
				request.conditions().forEach(cond->{
					if(cond.check(request.quantity, (Book.Orders)msg, null)){
						Execution.Manager.msg().unsubscribe(Topics.BID_ORDERS_BOOK_PREFIX.topic+request.symbol, this);
						Execution.Manager.dynami().orders().send(new MarketOrder(request.symbol, -request.quantity, cond.toString()));
						parent.remove(PendingConditions.this);
						invalidate = true;
						return;
					}
				});
			}
		};
	};
	
	
	private final IMsg.Handler askHandler = new IMsg.Handler(){
		public void update(boolean last, Object msg) {
			if(!invalidate){
				request.conditions().forEach(cond->{
					if(cond.check(request.quantity, null, (Book.Orders)msg)){
						Execution.Manager.msg().unsubscribe(Topics.ASK_ORDERS_BOOK_PREFIX.topic+request.symbol, this);
						Execution.Manager.dynami().orders().send(new MarketOrder(request.symbol, -request.quantity, cond.toString()));
						parent.remove(PendingConditions.this);
						invalidate = true;
						return;
					}
				});
			}
		};
	}; 
}
