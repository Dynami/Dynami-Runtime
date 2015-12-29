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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.dynami.core.assets.Asset;
import org.dynami.core.assets.Book;
import org.dynami.core.bus.IMsg;
import org.dynami.core.orders.MarketOrder;
import org.dynami.core.orders.OrderRequest;
import org.dynami.core.portfolio.ExecutedOrder;
import org.dynami.core.services.IOrderService.IOrderHandler;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.services.OrderService.Status;
import org.dynami.runtime.topics.Topics;

public class OrderRequestWrapper {
	private final OrderRequest request;
	private final IOrderHandler handler;
	private final AtomicReference<Status> status = new AtomicReference<Status>(Status.Pending);
	private final AtomicLong remainingQuantity = new AtomicLong(0);
	private final List<PendingConditions> pendingConditions = new CopyOnWriteArrayList<>();
	private final double price;
	
	
	public OrderRequestWrapper(OrderRequest request, IOrderHandler handler){
		this.request = request;
		this.handler = handler;
		remainingQuantity.set(request.quantity);
		
		final Asset.Tradable trad = Execution.Manager.dynami().assets().getBySymbol(request.symbol).asTradable();
		if(request instanceof MarketOrder){
			if(request.quantity>0){
				this.price = trad.book.ask().price;
			} else {
				this.price = trad.book.bid().price;
			}
		} else {
			price = request.price;
		}
		// check whether the order can be immediately executed or not
		if(request.quantity > 0 && checkAskSide(trad.book.ask(), new AtomicBoolean())){
			return;
		}
		if(request.quantity < 0 && checkBidSide(trad.book.ask(), new AtomicBoolean())){
			return;
		}
		
		if(request.quantity>0){
			Execution.Manager.msg().subscribe(Topics.ASK_ORDERS_BOOK_PREFIX.topic+request.symbol, askHandler);
		} else if(request.quantity<0){
			Execution.Manager.msg().subscribe(Topics.BID_ORDERS_BOOK_PREFIX.topic+request.symbol, bidHandler);
		}
	}
	
	public boolean cancelOrderRequest(){
		if(status.equals(Status.Pending) 
				|| status.equals(Status.PartiallyExecuted)){
			
			Execution.Manager.msg().unsubscribe(Topics.ASK_ORDERS_BOOK_PREFIX.topic+request.symbol, askHandler);
			Execution.Manager.msg().unsubscribe(Topics.BID_ORDERS_BOOK_PREFIX.topic+request.symbol, bidHandler);
			setRemainingQuantity(0);
			handler.onOrderCancelled(Execution.Manager.dynami(), request);
			return true;
		}
		return false;
	}
	
	private boolean checkBidSide(final Book.Orders orders, final AtomicBoolean invalidateMe){
		if(price <= orders.price && orders.quantity >= -getRemainingQuantity()){
			Execution.Manager.msg().async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, getRemainingQuantity(), request.time));
			status.set(Status.Executed);
			setRemainingQuantity(0);
			handler.onOrderExecuted(Execution.Manager.dynami(), request);
			if(request.conditions().size() > 0){
				pendingConditions.add(new PendingConditions(request, pendingConditions));
			}
			Execution.Manager.msg().unsubscribe(Topics.BID_ORDERS_BOOK_PREFIX.topic+request.symbol, bidHandler);
			invalidateMe.set(true);
			return true;
		} else if(price <= orders.price && orders.quantity < -getRemainingQuantity()){
			status.set(Status.PartiallyExecuted);
			setRemainingQuantity(getRemainingQuantity()+orders.quantity);
			Execution.Manager.msg().async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, -orders.quantity, request.time));
			handler.onOrderPartiallyExecuted(Execution.Manager.dynami(), request);
		}
		return false;
	}
	
	private boolean checkAskSide(final Book.Orders orders, final AtomicBoolean invalidateMe){
		if(price >= orders.price && orders.quantity >= getRemainingQuantity()){
			Execution.Manager.msg().async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, getRemainingQuantity(), request.time));
			status.set(Status.Executed);
			setRemainingQuantity(0);
			handler.onOrderExecuted(Execution.Manager.dynami(), request);
			if(request.conditions().size() > 0){
				pendingConditions.add(new PendingConditions(request, pendingConditions));
			}
			Execution.Manager.msg().unsubscribe(Topics.ASK_ORDERS_BOOK_PREFIX.topic+request.symbol, askHandler);
			invalidateMe.set(true);
			return true;
		} else if(price >= orders.price && orders.quantity < getRemainingQuantity()){
			status.set(Status.PartiallyExecuted);
			setRemainingQuantity(getRemainingQuantity()-orders.quantity);
			Execution.Manager.msg().async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, orders.quantity, request.time));
			handler.onOrderPartiallyExecuted(Execution.Manager.dynami(), request);
		}
		return false;
	}
	
	/**
	 * Check whether the buy price is lower or equals than ask price
	 */
	private final IMsg.Handler askHandler = new IMsg.Handler(){
		AtomicBoolean invalidateMe = new AtomicBoolean(false);
		public void update(boolean last, Object _msg) {
			if(!invalidateMe.get()){
				Book.Orders orders = (Book.Orders)_msg;
				checkAskSide(orders, invalidateMe);
//				if(price >= orders.price && orders.quantity >= getRemainingQuantity()){
//					Execution.Manager.msg().async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, getRemainingQuantity(), request.time));
//					status.set(Status.Executed);
//					setRemainingQuantity(0);
//					handler.onOrderExecuted(Execution.Manager.dynami(), request);
//					if(request.conditions().size() > 0){
//						pendingConditions.add(new PendingConditions(request, pendingConditions));
//					}
//					Execution.Manager.msg().unsubscribe(Topics.ASK_ORDERS_BOOK_PREFIX.topic+request.symbol, askHandler);
//					invalidateMe = true;
//				} else if(price >= orders.price && orders.quantity < getRemainingQuantity()){
//					status.set(Status.PartiallyExecuted);
//					setRemainingQuantity(getRemainingQuantity()-orders.quantity);
//					Execution.Manager.msg().async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, orders.quantity, request.time));
//					handler.onOrderPartiallyExecuted(Execution.Manager.dynami(), request);
//				}
			}
		};
	};
	
	/**
	 * Check whether the sell price is greater or equals than bid price 
	 */
	private final IMsg.Handler bidHandler = new IMsg.Handler(){
		AtomicBoolean invalidateMe = new AtomicBoolean(false);
		public void update(boolean last, Object _msg) {
			if(!invalidateMe.get()){
				Book.Orders orders = (Book.Orders)_msg;
				checkBidSide(orders, invalidateMe);
	//			if(orders.symbol.equals("MIBOC22000201405")){
	//				System.out.print("");
	//			}
//				if(price <= orders.price && orders.quantity >= -getRemainingQuantity()){
//					Execution.Manager.msg().async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, getRemainingQuantity(), request.time));
//					status.set(Status.Executed);
//					setRemainingQuantity(0);
//					handler.onOrderExecuted(Execution.Manager.dynami(), request);
//					if(request.conditions().size() > 0){
//						pendingConditions.add(new PendingConditions(request, pendingConditions));
//					}
//					Execution.Manager.msg().unsubscribe(Topics.BID_ORDERS_BOOK_PREFIX.topic+request.symbol, bidHandler);
//					invalidateMe = true;
//				} else if(price <= orders.price && orders.quantity < -getRemainingQuantity()){
//					status.set(Status.PartiallyExecuted);
//					setRemainingQuantity(getRemainingQuantity()+orders.quantity);
//					Execution.Manager.msg().async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, -orders.quantity, request.time));
//					handler.onOrderPartiallyExecuted(Execution.Manager.dynami(), request);
//				}
			}
		};
	};
	
	public OrderRequest getRequest() {
		return request;
	}
	
	public Status getStatus() {
		return status.get();
	}
	
	public void setStatus(Status status){
		this.status.set(status);
	}
	
	public long getRemainingQuantity() {
		return remainingQuantity.get();
	}
	
	private void setRemainingQuantity(long quantity){
		remainingQuantity.set(quantity);
	}
}
