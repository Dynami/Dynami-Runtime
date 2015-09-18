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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.dynami.core.assets.Asset;
import org.dynami.core.bus.IMsg;
import org.dynami.core.orders.MarketOrder;
import org.dynami.core.orders.OrderRequest;
import org.dynami.core.orders.OrderRequest.IOrderCondition;
import org.dynami.core.services.IOrderService;
import org.dynami.runtime.bus.Msg;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.impl.Service;
import org.dynami.runtime.models.ExecutedOrder;
import org.dynami.runtime.topics.Topics;

public class OrderService extends Service implements IOrderService {
	private final AtomicBoolean shutdown = new AtomicBoolean(false);
	private final List<OrderRequestWrapper> requests = new CopyOnWriteArrayList<>();
	private final List<Future<?>> fillers = new ArrayList<>();
	private final ExecutorService executor=  Executors.newCachedThreadPool();
	
	private IMsg msg = Msg.Broker;
	
	@Override
	public String id() {
		return IOrderService.ID;
	}
	
	@Override
	public boolean dispose() {
		shutdown.set(true);
		executor.shutdown();
		return super.dispose();
	}

	@Override
	public long send(OrderRequest order, IOrderHandler handler) {
		final OrderRequestWrapper _request = new OrderRequestWrapper(order);
//		System.out.println("OrderService.send() "+order);
		requests.add(_request);
		fillers.add(executor.submit(new OrderFiller(_request, handler)));
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
			OrderRequestWrapper.Status status = orderReq.getStatus();
			if(status.equals(OrderRequestWrapper.Status.Pending) || status.equals(OrderRequestWrapper.Status.PartiallyExecuted)){
				orderReq.setStatus(OrderRequestWrapper.Status.Cancelled);
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
				.filter(r->r.getStatus().equals(OrderRequestWrapper.Status.Pending))
				.map(OrderRequestWrapper::getRequest)
				.collect(Collectors.toList()));
	}

	@Override
	public boolean thereArePendingOrders() {
		return requests.stream()
				.anyMatch(r->r.getStatus().equals(OrderRequestWrapper.Status.Pending));
	}

	@Override
	public boolean thereArePendingOrders(String symbol) {
		return requests.stream()
				.allMatch(r-> r.getStatus().equals(OrderRequestWrapper.Status.Pending) && r.request.symbol.equals(symbol));
	}
	
	private static class OrderRequestWrapper {
		private final OrderRequest request;
		private final AtomicReference<Status> status = new AtomicReference<Status>(Status.Pending);
		private final AtomicLong remainingQuantity = new AtomicLong(0);
		
		public OrderRequestWrapper(OrderRequest request){
			this.request = request;
			remainingQuantity.set( (request.quantity>0)?request.quantity:-request.quantity);
		}
		
		public static enum Status {
			Pending, Executed, Rejected, PartiallyExecuted, Cancelled;
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
		
		public void setRemainingQuantity(long quantity){
			remainingQuantity.set(quantity);
		}
	}
	
	private class OrderFiller implements Runnable {
		private final OrderRequestWrapper orderReq;
		private final OrderRequest request;
		private final IOrderHandler handler;
		private final long price;
		private final Asset.Tradable trad;
		
		public OrderFiller(OrderRequestWrapper _request, IOrderHandler handler){
			this.orderReq = _request;
			this.request = _request.request;
			this.handler = handler;
			trad = (Asset.Tradable)Execution.Manager.dynami().assets().getBySymbol(request.symbol);
			if(orderReq.request instanceof MarketOrder){
				if(request.quantity>0){
					this.price = trad.book.ask(1).price;
				} else {
					this.price = trad.book.bid(1).price;
				}
			} else {
				price = request.price;
			}
		}
		
		@Override
		public void run() {
			while(!shutdown.get()){
				if(orderReq.getStatus().equals(OrderRequestWrapper.Status.Cancelled)){
					orderReq.setRemainingQuantity(0);
					handler.onOrderCancelled(Execution.Manager.dynami(), request);
					return;
				} else if((request.quantity > 0 
						&& trad.book.bid(1).price <= price 
						&& trad.book.bid(1).quantity >= orderReq.getRemainingQuantity())){
					
					msg.async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, orderReq.getRemainingQuantity(), request.time));
					orderReq.setStatus(OrderRequestWrapper.Status.Executed);
					orderReq.setRemainingQuantity(0);
					handler.onOrderExecuted(Execution.Manager.dynami(), request);
					break;
				} else if((request.quantity > 0 
						&& trad.book.bid(1).price <= price 
						&& trad.book.bid(1).quantity < orderReq.getRemainingQuantity())){
					
					orderReq.setStatus(OrderRequestWrapper.Status.PartiallyExecuted);
					orderReq.setRemainingQuantity(orderReq.getRemainingQuantity()-trad.book.bid(1).quantity);
					msg.async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, trad.book.bid(1).quantity, request.time));
					handler.onOrderPartiallyExecuted(Execution.Manager.dynami(), request);
				} else if((request.quantity < 0  
						&& trad.book.ask(1).price >= price 
						&& trad.book.ask(1).quantity >= orderReq.getRemainingQuantity())){
					
					msg.async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, -orderReq.getRemainingQuantity(), request.time));
					orderReq.setStatus(OrderRequestWrapper.Status.Executed);
					orderReq.setRemainingQuantity(0);
					handler.onOrderExecuted(Execution.Manager.dynami(), request);
					break;
				} else if((request.quantity < 0 
						&& trad.book.bid(1).price > price 
						&& trad.book.bid(1).quantity >= orderReq.getRemainingQuantity())){
					
					msg.async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, -trad.book.ask(1).quantity, request.time));
					orderReq.setStatus(OrderRequestWrapper.Status.PartiallyExecuted);
					orderReq.setRemainingQuantity(orderReq.getRemainingQuantity()-trad.book.ask(1).quantity);
					handler.onOrderPartiallyExecuted(Execution.Manager.dynami(), request);
				}
			}
			
			if(request.conditions().size() > 0){
				Collection<IOrderCondition> conditions = request.conditions();
				while(!shutdown.get()){
					for(IOrderCondition c: conditions){
						if(c.check(request.quantity, trad.book.bid(), trad.book.ask())){
							OrderService.this.send(new MarketOrder(request.symbol, -request.quantity, c.getClass().getSimpleName()));
							return;
						}
					}
				}
			}
		}
	}
}
