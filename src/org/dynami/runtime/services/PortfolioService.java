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

import static java.lang.Math.abs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.dynami.core.assets.Asset;
import org.dynami.core.bus.IMsg;
import org.dynami.core.config.Config;
import org.dynami.core.portfolio.ClosedPosition;
import org.dynami.core.portfolio.ExecutedOrder;
import org.dynami.core.portfolio.OpenPosition;
import org.dynami.core.services.IAssetService;
import org.dynami.core.services.IPortfolioService;
import org.dynami.core.utils.DUtils;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.impl.Service;
import org.dynami.runtime.topics.Topics;

public class PortfolioService extends Service implements IPortfolioService {
	private final List<ExecutedOrder> ordersLog = new ArrayList<>();
	private final Map<String, OpenPosition> openPositions = new ConcurrentSkipListMap<>();
	private final List<ClosedPosition> closedPositions = new ArrayList<>();
	private double budget = 20_000.0;

	private IMsg msg = Execution.Manager.msg();

	private final AtomicReference<Double> realized = new AtomicReference<Double>(.0);

	@Override
	public String id() {
		return IPortfolioService.ID;
	}

	@Override
	public boolean init(Config config) throws Exception {
		msg.subscribe(Topics.EXECUTED_ORDER.topic, (last, _msg)->{
			ExecutedOrder p = (ExecutedOrder)_msg;
			ordersLog.add(p);

			OpenPosition e = openPositions.get(p.symbol);
			if(e == null){
				Asset.Tradable trad = (Asset.Tradable)Execution.Manager.dynami().assets().getBySymbol(p.symbol);
				openPositions.put(p.symbol, new OpenPosition(p.symbol, p.quantity, p.price, p.time, trad.pointValue, p.time));
			} else {
				// chiudo la posizione
				if(e.quantity + p.quantity == 0){
					ClosedPosition closed = new ClosedPosition(e, p.price, p.time);
					closedPositions.add(closed);

					realized.set(realized.get()+closed.roi());
					openPositions.remove(p.symbol);
					System.out.printf("PortfolioService.realized( Close %5.2f)\n", realized.get());
				} else if( abs(e.quantity + p.quantity) > abs(e.quantity)){
					// incremento la posizione e medio il prezzo
					double newPrice = (e.entryPrice*e.quantity+p.price*p.quantity)/(e.quantity+p.quantity);
					OpenPosition newPos = new OpenPosition(e.symbol, e.quantity+p.quantity, newPrice, p.time, e.pointValue, p.time);
					openPositions.put(newPos.symbol, newPos);
					System.out.println("PortfolioService.init() Increment "+newPos);
				} else if( Math.abs(e.quantity + p.quantity) < Math.abs(e.quantity)){
					// decremento la posizione
					OpenPosition newPos = new OpenPosition(e.symbol, e.quantity+p.quantity, e.entryPrice, p.time, e.pointValue, p.time);
					
					ClosedPosition closed = new ClosedPosition(e.symbol, -p.quantity, e.entryPrice, e.entryTime, p.price, p.time, e.pointValue);
					closedPositions.add(closed);

					realized.set(realized.get()+closed.roi());
					openPositions.put(newPos.symbol, newPos);
					System.out.printf("PortfolioService.realized( Close %5.2f) "+newPos+"\n", realized.get());
				}
			}
		});
		return true;
	}
	
	@Override
	public void setInitialBudget(double budget) {
		assert budget < 1000 : "The minumum amount for budget is 1000";
		this.budget = budget;
	}

	@Override
	public double getCurrentBudget() {
		return budget+realized()+unrealized();
	}

	@Override
	public boolean isOnMarket() {
		return openPositions.size() > 0;
	}

	@Override
	public boolean isOnMarket(String symbol) {
		return openPositions.get(symbol) != null;
	}

	@Override
	public boolean isLong(String symbol) {
		final OpenPosition pos = openPositions.get(symbol);
		return pos != null && pos.quantity > 0;
	}

	@Override
	public boolean isShort(String symbol) {
		final OpenPosition pos = openPositions.get(symbol);
		return pos != null && pos.quantity < 0;
	}

	@Override
	public Collection<OpenPosition> getOpenPosition() {
		return Collections.unmodifiableCollection(openPositions.values());
	}

	@Override
	public OpenPosition getPosition(String symbol) {
		return openPositions.get(symbol);
	}

	@Override
	public Collection<ClosedPosition> getClosedPosition() {
		return Collections.unmodifiableCollection(closedPositions);
	}

	@Override
	public Collection<ClosedPosition> getClosedPosition(String symbol) {
		List<ClosedPosition> pos = closedPositions.stream()
			.filter( p -> p.symbol.equals(symbol))
			.collect(Collectors.toList());
		return Collections.unmodifiableCollection(pos);
	}

	@Override
	public double realized() {
		return realized.get();
	}

	@Override
	public double unrealized() {
		final IAssetService assetService = Execution.Manager.getServiceBus().getService(IAssetService.class, IAssetService.ID);
		final AtomicLong unrealized = new AtomicLong(0);
		openPositions.values().stream().forEach(o->{
			final Asset.Tradable trad = (Asset.Tradable)assetService.getBySymbol(o.symbol);
			double currentPrice = (o.quantity > 0)?trad.book.bid().price:trad.book.ask().price;
			double value = ((currentPrice - o.entryPrice)*o.quantity*o.pointValue);
			unrealized.addAndGet(DUtils.d2l(value));
		});
		return DUtils.l2d(unrealized.get());
	}

	@Override
	public Collection<ExecutedOrder> executedOrdersLog() {
		return Collections.unmodifiableCollection(ordersLog);
	}
}
