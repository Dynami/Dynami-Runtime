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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import org.dynami.core.assets.Asset;
import org.dynami.core.bus.IMsg;
import org.dynami.core.config.Config;
import org.dynami.core.services.IPortfolioService;
import org.dynami.core.utils.DUtils;
import org.dynami.runtime.bus.Msg;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.impl.Service;
import org.dynami.runtime.models.ClosedPosition;
import org.dynami.runtime.models.ExecutedOrder;
import org.dynami.runtime.models.OpenPosition;
import org.dynami.runtime.topics.Topics;

public class PortfolioService extends Service implements IPortfolioService {
	private final List<ExecutedOrder> orderLog = new ArrayList<>();
	private final Map<String, OpenPosition> openPositions = new ConcurrentSkipListMap<>();
	private final List<ClosedPosition> closedPositions = new ArrayList<>();
	
	private IMsg msg = Msg.Broker;
	
	private final AtomicLong realized = new AtomicLong(0);
	@Override
	public String id() {
		return IPortfolioService.ID;
	}
	
	@Override
	public boolean init(Config config) throws Exception {
		msg.subscribe(Topics.EXECUTED_ORDER.topic, (last, msg)->{
			ExecutedOrder p = (ExecutedOrder)msg;
			orderLog.add(p);
			
			OpenPosition e = openPositions.get(p.symbol);
			if(e == null){
				Asset.Tradable trad = (Asset.Tradable)Execution.Manager.dynami().assets().getBySymbol(p.symbol);
				openPositions.put(p.symbol, new OpenPosition(p.symbol, p.quantity, p.price, p.time, trad.pointValue, p.time));
			} else {
				// chiudo la posizione
				if(e.quantity + p.quantity == 0){
					ClosedPosition closed = new ClosedPosition(e, p.price, p.time);
					closedPositions.add(closed);
					
					realized.addAndGet((long)((closed.exitPrice-closed.entryPrice)*closed.quantity*closed.pointValue));
					openPositions.remove(p.symbol);
					System.out.println("PortfolioService.realized( Close "+DUtils.l2d(realized.get())+")");
				} else if( abs(e.quantity + p.quantity) > abs(e.quantity)){
					// incremento la posizione e medio il prezzo
					long newPrice = (e.entryPrice*e.quantity+p.price*p.quantity)/(e.quantity+p.quantity);
					OpenPosition newPos = new OpenPosition(e.symbol, e.quantity+p.quantity, newPrice, p.time, e.pointValue, p.time);
					openPositions.put(newPos.symbol, newPos);
					System.out.println("PortfolioService.init() Increment "+newPos);
				} else if( Math.abs(e.quantity + p.quantity) < Math.abs(e.quantity)){
					// decremento la posizione
					OpenPosition newPos = new OpenPosition(e.symbol, e.quantity+p.quantity, e.entryPrice, p.time, e.pointValue, p.time);
					realized.addAndGet( (long)((p.price-e.entryPrice)*-p.quantity*e.pointValue));
					openPositions.put(newPos.symbol, newPos);
					System.out.println("PortfolioService.realized( "+DUtils.l2d(realized.get())+") "+newPos);
				}
			}
		});
		return true;
	}
}
