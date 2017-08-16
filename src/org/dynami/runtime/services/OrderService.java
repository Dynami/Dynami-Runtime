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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.dynami.core.assets.Asset;
import org.dynami.core.assets.Asset.Tradable;
import org.dynami.core.assets.Book;
import org.dynami.core.config.Config;
import org.dynami.core.orders.OrderRequest;
import org.dynami.core.portfolio.ExecutedOrder;
import org.dynami.core.services.IOrderService;
import org.dynami.core.utils.DTime;
import org.dynami.runtime.IService;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.topics.Topics;

public class OrderService implements IService, IOrderService {
	private final AtomicLong ids = new AtomicLong(0);
	private final List<OrderRequest> requests = new CopyOnWriteArrayList<OrderRequest>();
	private boolean initialized = false;
//	private final List<OrderRequest> executed = new CopyOnWriteArrayList<OrderRequest>();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public boolean dispose() {
		requests.clear();
//		executed.clear();
		ids.set(0);
		return true;
	}

	@Override
	public <T extends Config> boolean init(T config) throws Exception {
		if(initialized) return true;
		initialized = true;
		Execution.Manager.msg().subscribe(Topics.INSTRUMENT.topic, (_last, _msg)->{
			final Asset asset = (Asset)_msg;

			Execution.Manager.msg().subscribe(Topics.ASK_ORDERS_BOOK_PREFIX.topic+asset.symbol, (last, msg)->{
				final Book.Orders book = (Book.Orders)msg;
				requests.stream()
					.filter(o->o.getStatus().equals(IOrderService.Status.Pending))
					.filter(o->o.symbol.equals(book.symbol))
					.filter(o->o.quantity>0)
					.filter(o->o.price >= book.price)
					.peek((o)->{
						System.out.println("OrderService-> executed "+o.id);
						o.updateStatus(IOrderService.Status.Executed);
						o.setExecutionTime(DTime.Clock.getTime());
						Execution.Manager.msg().async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(o.id, o.symbol, book.price, o.quantity, DTime.Clock.getTime()));
						o.handler.onOrderExecuted(Execution.Manager.dynami(), o);
					})
					.count();
			});
			Execution.Manager.msg().subscribe(Topics.BID_ORDERS_BOOK_PREFIX.topic+asset.symbol, (last, msg)->{
				final Book.Orders book = (Book.Orders)msg;
				requests.stream()
					.filter(o->o.getStatus().equals(IOrderService.Status.Pending))
					.filter(o->o.symbol.equals(book.symbol))
					.filter(o->o.quantity<0)
					.filter(o->o.price <= book.price)
					.peek((o)->{
						System.out.println("OrderService-> executed "+o.id);
						o.updateStatus(IOrderService.Status.Executed);
						o.setExecutionTime(DTime.Clock.getTime());
						Execution.Manager.msg().async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(o.id, o.symbol, book.price, o.quantity, DTime.Clock.getTime()));
						o.handler.onOrderExecuted(Execution.Manager.dynami(), o);
					})
					.count();
			});
		});
		return IService.super.init(config);
	}

	@Override
	public long limitOrder(String symbol, double price, long quantity, String note, IOrderHandler handler) {
		final long id = ids.getAndIncrement();
		System.out.println("OrderService.limitOrder() "+id+" "+symbol+" "+quantity+" at "+price);
		final OrderRequest request = new OrderRequest(
				id,
				DTime.Clock.getTime(),
				symbol,
				quantity,
				price,
				note,
				handler);
		requests.add(request);
		Execution.Manager.msg().async(Topics.ORDER_REQUESTS.topic, request);
		return id;
	}

	@Override
	public long limitOrder(String symbol, double price, long quantity, String note) {
		return limitOrder(symbol, price, quantity, note, new IOrderService.IOrderHandler() {});
	}

	@Override
	public long limitOrder(String symbol, double price, long quantity) {
		return limitOrder(symbol, price, quantity, "", new IOrderService.IOrderHandler() {});
	}

	@Override
	public long marketOrder(String symbol, long quantity) {
		return marketOrder(symbol, quantity, "", new IOrderService.IOrderHandler() {});
	}

	@Override
	public long marketOrder(String symbol, long quantity, String note) {
		return marketOrder(symbol, quantity, note, new IOrderService.IOrderHandler() {});
	}

	@Override
	public long marketOrder(String symbol, long quantity, String note, IOrderHandler handler) {
		final Tradable trad = (Tradable)Execution.Manager.dynami().assets().getBySymbol(symbol);
		double price = (quantity>0)?trad.book.ask().price:trad.book.bid().price;
		
		final long id = ids.getAndIncrement();
		System.out.println("OrderService.marketOrder() "+id+" "+symbol+" "+quantity+" at "+price);
		final OrderRequest request = new OrderRequest(
				id,
				DTime.Clock.getTime(),
				symbol,
				quantity,
				price,
				note,
				handler);
		System.out.println("OrderService-> executed "+id);
		request.updateStatus(Status.Executed);
		request.updateStatus(IOrderService.Status.Executed);
		request.setExecutionTime(DTime.Clock.getTime());
		Execution.Manager.msg().async(Topics.ORDER_REQUESTS.topic, request);
		Execution.Manager.msg().async(Topics.EXECUTED_ORDER.topic, new ExecutedOrder(request.id, request.symbol, price, request.quantity, request.getExecutionTime()));
		request.handler.onOrderExecuted(Execution.Manager.dynami(), request);
		requests.add(request);
		return id;
		//return limitOrder(symbol, price, quantity, note, handler);
	}

	@Override
	public void removePendings() {
		requests.clear();
	}

	@Override
	public OrderRequest getOrderById(long id) {
		return requests.stream()
				.filter(o->o.id == id)
				.findFirst()
				.get();
	}

	@Override
	public Status getOrderStatus(long id) {
		OrderRequest req = getOrderById(id);
		if(req != null)
			return req.getStatus();
		else
			return null;
	}

	@Override
	public boolean cancelOrder(long id) {
		OrderRequest req = getOrderById(id);
		if(req != null){
			req.updateStatus(Status.Cancelled);
			return true;
		}
		return false;
	}

	@Override
	public List<OrderRequest> getPendingOrders() {
		return requests
				.stream()
				.filter(o->o.getStatus().equals(Status.Pending))
				.collect(Collectors.toList());
	}

	@Override
	public boolean thereArePendingOrders() {
		return requests.stream()
				.filter(o->o.getStatus().equals(Status.Pending))
				.count()>0;
	}

	@Override
	public boolean thereArePendingOrders(String symbol) {
		return requests.stream()
				.filter(o->o.symbol.equals(symbol))
				.filter(o->o.status.get().equals(Status.Pending))
				.count()>0;
	}
}


