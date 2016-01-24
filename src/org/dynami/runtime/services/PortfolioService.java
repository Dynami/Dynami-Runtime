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

import org.dynami.core.Event;
import org.dynami.core.assets.Asset;
import org.dynami.core.bus.IMsg;
import org.dynami.core.config.Config;
import org.dynami.core.portfolio.ClosedPosition;
import org.dynami.core.portfolio.ExecutedOrder;
import org.dynami.core.portfolio.OpenPosition;
import org.dynami.core.services.IPortfolioService;
import org.dynami.core.utils.DTime;
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
	private final AtomicReference<Double> margination = new AtomicReference<Double>(.0);

	@Override
	public String id() {
		return IPortfolioService.ID;
	}

	@Override
	public boolean init(Config config) throws Exception {

		msg.subscribe(Topics.STRATEGY_EVENT.topic, (last, msg)->{
			Event e =(Event)msg;
			if(e.is(Event.Type.OnDayClose)){
				final long closingTime = DTime.Clock.getTime()+1;
				final List<OpenPosition> to_remove = openPositions.values()
					.stream()
					.filter((o)-> o.asset instanceof Asset.ExpiringInstr)
					.filter(o -> ((Asset.ExpiringInstr)o.asset).isExpired(closingTime)) // in this way the expired option in the date are checked
					.collect(Collectors.toList());

				to_remove.forEach(o->{
					ClosedPosition closed = new ClosedPosition(o, o.asset.lastPrice(), closingTime);
					closedPositions.add(closed);

					realized.set(realized.get()+closed.roi());
					openPositions.remove(o.asset.symbol);
				});
			}

			final Asset.Tradable.Margin margin = new Asset.Tradable.Margin();
			openPositions.values().forEach(o->{
				Asset.Tradable.Margin m;
				if(o.asset instanceof Asset.Option){
					Asset.Option opt = (Asset.Option)o.asset;
					m = opt.margination(opt.underlyingAsset.asTradable().lastPrice(), o.quantity);
				} else {
					m = o.asset.margination(o.asset.lastPrice(), o.quantity);
				}
				margin.merge(m);
			});
			margination.set(margin.required());
		});

		msg.subscribe(Topics.EXECUTED_ORDER.topic, (last, _msg)->{
			ExecutedOrder p = (ExecutedOrder)_msg;
			ordersLog.add(p);

			OpenPosition e = openPositions.get(p.symbol);
			if(e == null){
				Asset.Tradable trad = (Asset.Tradable)Execution.Manager.dynami().assets().getBySymbol(p.symbol);
				openPositions.put(p.symbol, new OpenPosition(trad, p.quantity, p.price, p.time, p.time));
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
					OpenPosition newPos = new OpenPosition(e.asset, e.quantity+p.quantity, newPrice, p.time, p.time);
					openPositions.put(newPos.asset.symbol, newPos);
					System.out.println("PortfolioService.init() Increment "+newPos);
				} else if( Math.abs(e.quantity + p.quantity) < Math.abs(e.quantity)){
					// decremento la posizione
					OpenPosition newPos = new OpenPosition(e.asset, e.quantity+p.quantity, e.entryPrice, p.time, p.time);

					ClosedPosition closed = new ClosedPosition(e.asset.family, e.asset.symbol, -p.quantity, e.entryPrice, e.entryTime, p.price, p.time, e.asset.pointValue);
					closedPositions.add(closed);

					realized.set(realized.get()+closed.roi());
					openPositions.put(newPos.asset.symbol, newPos);
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
	public double getInitialBudget() {
		return budget;
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
	public boolean isFlat() {
		return !isOnMarket();
	}

	@Override
	public boolean isOnMarket(String symbol) {
		return openPositions.get(symbol) != null;
	}

	@Override
	public boolean isFlat(String symbol) {
		return !isOnMarket(symbol);
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
	public List<OpenPosition> getOpenPosition() {
		return Collections.unmodifiableList(new ArrayList<OpenPosition>(openPositions.values()));
	}

	@Override
	public OpenPosition getPosition(String symbol) {
		return openPositions.get(symbol);
	}

	@Override
	public List<ClosedPosition> getClosedPosition() {
		return Collections.unmodifiableList(closedPositions);
	}

	@Override
	public List<ClosedPosition> getClosedPosition(String symbol) {
		List<ClosedPosition> pos = closedPositions.stream()
			.filter( p -> p.symbol.equals(symbol))
			.collect(Collectors.toList());
		return Collections.unmodifiableList(pos);
	}

	@Override
	public double realized() {
		return realized.get();
	}

	@Override
	public double requiredMargin() {
		return margination.get();
	}

	@Override
	public double unrealized(String symbol){
		final OpenPosition o = openPositions.get(symbol);
		if(o == null) {
			return 0.0;
		} else {
			double currentPrice = o.getCurrentPrice();
			double value = ((currentPrice - o.entryPrice)*o.quantity*o.asset.pointValue);
			return value;
		}
	}

	@Override
	public double unrealized() {
		final AtomicLong unrealized = new AtomicLong(0);
		openPositions.values().stream().forEach(o->{
			double currentPrice = o.getCurrentPrice();
			double value = ((currentPrice - o.entryPrice)*o.quantity*o.asset.pointValue);
			unrealized.addAndGet(DUtils.d2l(value));
		});
		return DUtils.l2d(unrealized.get());
	}

	@Override
	public Collection<ExecutedOrder> executedOrdersLog() {
		return Collections.unmodifiableCollection(ordersLog);
	}
}
