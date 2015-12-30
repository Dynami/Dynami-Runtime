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
import java.util.stream.Collectors;

import org.dynami.core.orders.OrderRequest;
import org.dynami.core.services.IOrderService;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.impl.Service;
import org.dynami.runtime.orders.OrderRequestWrapper;
import org.dynami.runtime.topics.Topics;

public class OrderService extends Service implements IOrderService {
	private final AtomicBoolean shutdown = new AtomicBoolean(false);
	private final List<OrderRequestWrapper> requests = new CopyOnWriteArrayList<>();

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
		System.out.println("OrderService.send() "+order.toString());
		OrderRequestWrapper request = new OrderRequestWrapper(order, handler); 
		requests.add(request);
		Execution.Manager.msg().async(Topics.ORDER_REQUESTS.topic, request);
		return order.id;
	}

	@Override
	public long send(OrderRequest order) {
		return send(order, new IOrderHandler(){});
	}
	
	private OrderRequestWrapper getOrderWrapperById(long id) {
		Optional<OrderRequestWrapper> opt = requests.stream()
				.filter(r->r.getRequest().id == id)
				.findFirst();
		return opt.get();
	}

	@Override
	public OrderRequest getOrderById(long id) {
		OrderRequestWrapper w = getOrderWrapperById(id);
		return (w != null)? w.getRequest():null;
	}
	
	@Override
	public Status getOrderStatus(long id) {
		OrderRequestWrapper w = getOrderWrapperById(id);
		return (w != null)?w.getStatus():null;
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
	
	public void removePendings(){
		getPendingOrders().forEach(r->{
			cancellOrder(r.id);
		});
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
				.allMatch(r-> r.getStatus().equals(Status.Pending) && r.getRequest().symbol.equals(symbol));
	}
}


