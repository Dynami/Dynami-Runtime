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
package org.dynami.runtime.services;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.dynami.core.assets.Asset;
import org.dynami.core.assets.Book;
import org.dynami.core.bus.IMsg;
import org.dynami.core.orders.MarketOrder;
import org.dynami.core.orders.OrderRequest;
import org.dynami.core.portfolio.ExecutedOrder;
import org.dynami.core.services.IOrderService;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.impl.Service;
import org.dynami.runtime.topics.Topics;

public class OrderService extends Service implements IOrderService {
	private final AtomicBoolean shutdown = new AtomicBoolean(false);
	private final List<OrderRequestWrapper> requests = new CopyOnWriteArrayList<>();

	private final IMsg msg = Execution.Manager.msg();
	
	private final List<PendingConditions> pendingConditions = new CopyOnWriteArrayList<>();
	
	public static enum Status {
		Pending, Executed, Rejected, PartiallyExecuted, Cancelled;
	};

	@Override
	public String id() {
		return IOrderService.ID;
	}

	@Override
	public boolean dispose() {
		shutdown.set(true);
		return super.dispose();
	}

	@Override
	public long send(OrderRequest order, IOrderHandler handler) {
		requests.add(new OrderRequestWrapper(order, handler));
		return order.id;
	}

	@Override
	public long send(OrderRequest order) {
		return send(order, new IOrderHandler(){});
	}

	@Override
	public OrderRequest getOrderById(long id) {
		Optional<OrderRequestWrapper> opt = requests.stream()
				.filter(r->r.request.id == id)
				.findFirst();

		return (opt.isPresent())? opt.get().request:null;
	}

	@Override
	public boolean cancellOrder(long id) {
		Optional<OrderRequestWrapper> opt = requests.stream().filter(r->r.getRequest().id == id).findFirst();
		if(opt.isPresent()){
			OrderRequestWrapper orderReq = opt.get();
			Status status = orderReq.getStatus();
			if(status.equals(Status.Pending) || status.equals(Status.PartiallyExecuted)){
				orderReq.setStatus(Status.Cancelled);
				orderReq.cancelOrderRequest();
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	@Override
	public List<OrderRequest> getPendingOrders() {
		return Collections.unmodifiableList(
				requests.stream()
				.filter(r->r.getStatus().equals(Status.Pending))
				.map(OrderRequestWrapper::getRequest)
				.collect(Collectors.toList()));
	}

	@Override
	public boolean thereArePendingOrders() {
		return requests.stream()
				.anyMatch(r->r.getStatus().equals(Status.Pending));
	}

	@Override
	public boolean thereArePendingOrders(String symbol) {
		return requests.stream()
				.allMatch(r-> r.getStatus().equals(Status.Pending) && r.request.symbol.equals(symbol));
	}
	
	private class PendingConditions {
		private final OrderRequest request;
		private final List<?> parent;
		
		public PendingConditions(OrderRequest request, List<?> parent){
			this.request = request;
			this.parent = parent;
			if(request.quantity > 0){
				Execution.Manager.msg().subscribe(Topics.BID_ORDERS_BOOK_PREFIX.topic+request.symbol, bidHandler);
			} else {
				Execution.Manager.msg().subscribe(Topics.ASK_ORDERS_BOOK_PREFIX.topic+request.symbol, askHandler);
			}
		}
		
		private final IMsg.Handler bidHandler = new IMsg.Handler(){
			boolean invalidateMe = false;
			public void update(boolean last, Object msg) {
				if(!invalidateMe){
					request.conditions().forEach(cond->{
						if(cond.check(request.quantity, (Book.Orders)msg, null)){
							Execution.Manager.msg().unsubscribe(Topics.BID_ORDERS_BOOK_PREFIX.topic+request.symbol, this);
							OrderService.this.send(new MarketOrder(request.symbol, -request.quantity, cond.toString()));
							parent.remove(PendingConditions.this);
							invalidateMe = true;
							return;
						}
					});
				}
			};
		};
		

		private final IMsg.Handler askHandler = new IMsg.Handler(){
			boolean invalidateMe = false;
			public void update(boolean last, Object msg) {
				if(!invalidateMe){
					request.conditions().forEach(cond->{
						if(cond.check(request.quantity, null, (Book.Orders)msg)){
							Execution.Manager.msg().unsubscribe(Topics.ASK_ORDERS_BOOK_PREFIX.topic+request.symbol, this);
							OrderService.this.send(new MarketOrder(request.symbol, -request.quantity, cond.toString()));
							parent.remove(PendingConditions.this);
							invalidateMe = true;
							return;
						}
					});
				}
			};
		}; 
	}

	private class OrderRequestWrapper {
		private final OrderRequest request;
		private final IOrderHandler handler;
		private final AtomicReference<Status> status = new AtomicReference<Status>(Status.Pending);
		private final AtomicLong remainingQuantity = new AtomicLong(0);
		private final double price;
		
		public OrderRequestWrapper(OrderRequest request, IOrderHandler handler){
			this.request = request;
			this.handler = handler;
			remainingQuantity.set(Math.abs(request.quantity));
			
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
			if(request.quantity>0){
				msg.subscribe(Topics.ASK_ORDERS_BOOK_PREFIX.topic+request.symbol, askHandler);
			} else if(request.quantity<0){
				msg.subscribe(Topics.BID_ORDERS_BOOK_PREFIX.topic+request.symbol, bidHandler);
			}
		}
		
		public boolean cancelOrderRequest(){
			if(status.equals(Status.Pending) 
				|| status.equals(Status.PartiallyExecuted)){
				
				msg.unsubscribe(Topics.ASK_ORDERS_BOOK_PREFIX.topic+request.symbol, askHandler);
				msg.unsubscribe(Topics.BID_ORDERS_BOOK_PREFIX.topic+request.symbol, bidHandler);
				setRemainingQuantity(0);
				handler.onOrderCancelled(Execution.Manager.dynami(), request);
				return true;
			}
			return false;
		}
		
		private final IMsg.Handler askHandler = new IMsg.Handler(){
			boolean invalidateMe = false;
			public void update(boolean last, Object _msg) {
				if(!invalidateMe){
					Book.Orders orders = (Book.Orders)_msg;
					if(price >= orders.price && orders.quantity >= getRemainingQuantity()){
						msg.async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, getRemainingQuantity(), request.time));
						status.set(Status.Executed);
						setRemainingQuantity(0);
						handler.onOrderExecuted(Execution.Manager.dynami(), request);
						if(request.conditions().size() > 0){
							pendingConditions.add(new PendingConditions(request, pendingConditions));
						}
						msg.unsubscribe(Topics.ASK_ORDERS_BOOK_PREFIX.topic+request.symbol, this);
						invalidateMe = true;
					} else if(price >= orders.price && orders.quantity < getRemainingQuantity()){
						status.set(Status.PartiallyExecuted);
						setRemainingQuantity(getRemainingQuantity()-orders.quantity);
						msg.async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, orders.quantity, request.time));
						handler.onOrderPartiallyExecuted(Execution.Manager.dynami(), request);
					}
				}
			};
		};
		
		private final IMsg.Handler bidHandler = new IMsg.Handler(){
			boolean invalidateMe = false;
			public void update(boolean last, Object _msg) {
				if(!invalidateMe){
					Book.Orders orders = (Book.Orders)_msg;
					if(price <= orders.price && orders.quantity >= getRemainingQuantity()){
						msg.async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, -getRemainingQuantity(), request.time));
						status.set(Status.Executed);
						setRemainingQuantity(0);
						handler.onOrderExecuted(Execution.Manager.dynami(), request);
						if(request.conditions().size() > 0){
							pendingConditions.add(new PendingConditions(request, pendingConditions));
						}
						msg.unsubscribe(Topics.BID_ORDERS_BOOK_PREFIX.topic+request.symbol, this);
						invalidateMe = true;
					} else if(price <= orders.price && orders.quantity > getRemainingQuantity()){
						status.set(Status.PartiallyExecuted);
						setRemainingQuantity(getRemainingQuantity()-orders.quantity);
						msg.async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, -orders.quantity, request.time));
						handler.onOrderPartiallyExecuted(Execution.Manager.dynami(), request);
					}
				}
			};
		};
		
		public OrderRequest getRequest() {
			return request;
		}

		public Status getStatus() {
			return status.get();
		}

		private void setStatus(Status status){
			this.status.set(status);
		}

		public long getRemainingQuantity() {
			return remainingQuantity.get();
		}

		private void setRemainingQuantity(long quantity){
			remainingQuantity.set(quantity);
		}
	}
}
