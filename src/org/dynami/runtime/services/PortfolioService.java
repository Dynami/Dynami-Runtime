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
import org.dynami.runtime.IService;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.topics.Topics;

public class PortfolioService implements IService, IPortfolioService {
	private final List<ExecutedOrder> ordersLog = new ArrayList<>();
	private final Map<String, OpenPosition> openPositions = new ConcurrentSkipListMap<>();
	private final List<ClosedPosition> closedPositions = new ArrayList<>();
	private double budget = 20_000.0;
	private IMsg msg = Execution.Manager.msg();


	private boolean initialized = false;
	private final AtomicReference<Double> realised = new AtomicReference<Double>(.0);
	private final AtomicReference<Double> margination = new AtomicReference<Double>(.0);
	private final AtomicReference<Double> commissions = new AtomicReference<Double>(.0);

	@Override
	public String id() {
		return IPortfolioService.ID;
	}

	@Override
	public boolean dispose() {
		System.out.println("PortfolioService.dispose()");
		ordersLog.clear();
		openPositions.clear();
		closedPositions.clear();
		realised.set(0.);
		margination.set(0.);
		commissions.set(0.);
		budget = 20_000.0;
		initialized = false;
		msg.unsubscribe(Topics.STRATEGY_EVENT.topic, strategyEventHandler);
		msg.unsubscribe(Topics.EXECUTED_ORDER.topic, orderExecutedEventHandler);
		return true;
	}

	private final IMsg.Handler strategyEventHandler = new IMsg.Handler() {
		@Override
		public void update(boolean last, Object msg) {
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

					realised.set(realised.get()+closed.roi());
					openPositions.remove(o.asset.symbol);
				});
			}

			final Asset.Tradable.Margin margin = new Asset.Tradable.Margin();
			openPositions.values().forEach(o->{
				Asset.Tradable.Margin m;
				if(o.asset instanceof Asset.Option){
					Asset.Option opt = (Asset.Option)o.asset;
					m = opt.margination(opt.underlyingAsset.asTradable().lastPrice(), o.quantity, o.entryPrice);
				} else {
					m = o.asset.margination(o.asset.lastPrice(), o.quantity);
				}
				margin.merge(m);
			});
			margination.set(margin.required());
		}
	};

	private final IMsg.Handler orderExecutedEventHandler = new IMsg.Handler() {
		public void update(boolean last, Object msg) {
			ExecutedOrder p = (ExecutedOrder)msg;
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

					realised.set(realised.get()+closed.roi());
					openPositions.remove(p.symbol);
					System.out.printf("PortfolioService.realized( Close %5.2f)\n", realised.get());
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

					realised.set(realised.get()+closed.roi());
					openPositions.put(newPos.asset.symbol, newPos);
					System.out.printf("PortfolioService.realized( Close %5.2f) "+newPos+"\n", realised.get());
				}
			}
		};
	};

	@Override
	public boolean init(Config config) throws Exception {
		System.out.println("PortfolioService.init()");
		ordersLog.clear();
		openPositions.clear();
		closedPositions.clear();
		realised.set(0.);
		margination.set(0.);
		commissions.set(0.);

		if(!initialized){
			msg.subscribe(Topics.STRATEGY_EVENT.topic, strategyEventHandler);
			msg.subscribe(Topics.EXECUTED_ORDER.topic, orderExecutedEventHandler);
			initialized = true;
		}
		return initialized;
	}

	@Override
	public Greeks getPortfolioGreeks() {
		final AtomicReference<Double> delta = new AtomicReference<>(0.);
		final AtomicReference<Double> gamma = new AtomicReference<>(0.);
		final AtomicReference<Double> vega = new AtomicReference<>(0.);
		final AtomicReference<Double> theta = new AtomicReference<>(0.);
		final AtomicReference<Double> price = new AtomicReference<>(0.);
		getOpenPositions().forEach(o->{
			double underlyingPrice = 0 ;
			if(o.asset.family.equals(Asset.Family.Option)){
				underlyingPrice = ((Asset.Option)o.asset).underlyingAsset.asTradable().lastPrice();

				double _delta = ((Asset.Option)o.asset).greeks().delta() * o.quantity * o.asset.pointValue;
				delta.set(delta.get()+_delta);

				double _gamma = ((Asset.Option)o.asset).greeks().gamma() * o.quantity * o.asset.pointValue;
				gamma.set(gamma.get()+_gamma);

				double _vega = ((Asset.Option)o.asset).greeks().vega() * o.quantity * o.asset.pointValue;
				vega.set(vega.get()+_vega);

				double _theta = ((Asset.Option)o.asset).greeks().theta() * o.quantity * o.asset.pointValue;
				theta.set(theta.get()+_theta);
			} else {
				underlyingPrice = o.asset.lastPrice();
				delta.set(delta.get()+(o.quantity*o.asset.pointValue));
				vega.set(vega.get()+(o.quantity*o.asset.pointValue));
				theta.set(theta.get()+(o.quantity*o.asset.pointValue));
				gamma.set(0.);
			}
			price.set(underlyingPrice);
		});
		return new Greeks(price.get(), delta.get(), gamma.get(), vega.get(), theta.get());
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
		return budget+realised()+unrealised();
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
	public List<OpenPosition> getOpenPositions() {
		return Collections.unmodifiableList(new ArrayList<OpenPosition>(openPositions.values()));
	}

	@Override
	public OpenPosition getPosition(String symbol) {
		return openPositions.get(symbol);
	}

	@Override
	public List<ClosedPosition> getClosedPositions() {
		return Collections.unmodifiableList(closedPositions);
	}

	@Override
	public List<ClosedPosition> getClosedPositions(String symbol) {
		List<ClosedPosition> pos = closedPositions.stream()
			.filter( p -> p.symbol.equals(symbol))
			.collect(Collectors.toList());
		return Collections.unmodifiableList(pos);
	}

	@Override
	public double realised() {
		return realised.get();
	}

	@Override
	public double commissions() {
		return commissions.get();
	}

	@Override
	public double requiredMargin() {
		return margination.get();
	}

	@Override
	public double unrealised(String symbol){
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
	public double unrealised() {
		final AtomicLong unrealized = new AtomicLong(0);
		openPositions.values().stream().forEach(o->{
			double currentPrice = o.getCurrentPrice();
			double value = ((currentPrice - o.entryPrice)*o.quantity*o.asset.pointValue);
			unrealized.addAndGet(DUtils.d2l(value));
		});
		return DUtils.l2d(unrealized.get());
	}

	@Override
	public Collection<ExecutedOrder> executedOrders() {
		return Collections.unmodifiableCollection(ordersLog);
	}
}
