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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import org.dynami.core.Event;
import org.dynami.core.assets.Asset;
import org.dynami.core.assets.Market;
import org.dynami.core.assets.OptionChain;
import org.dynami.core.bus.IMsg;
import org.dynami.core.config.Config;
import org.dynami.core.services.IAssetService;
import org.dynami.core.utils.DTime;
import org.dynami.runtime.IService;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.topics.Topics;

public class AssetService implements IService, IAssetService  {
	private final Map<String, Asset> registry = new ConcurrentSkipListMap<>();
	private final Map<String, OptionChain> chains = new ConcurrentSkipListMap<>();
	private final IMsg msg = Execution.Manager.msg();
	private boolean initialized = false;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public boolean dispose() {
		registry.clear();
		return reset();
	}

	@Override
	public boolean reset() {
		chains.clear();
		return true;
	}

	@Override
	public boolean init(Config config) throws Exception {
		if(!initialized){
			msg.subscribe(Topics.STRATEGY_EVENT.topic, (last, _msg)->{
				Event e =(Event)_msg;
				if(e.is(Event.Type.OnDayClose)){
					final long currentTime = DTime.Clock.getTime()+1;
					final List<Asset> to_remove = registry.values()
							.stream()
							.filter(a->a instanceof Asset.ExpiringInstr)
							.filter(a->((Asset.ExpiringInstr)a).isExpired(currentTime))
							.collect(Collectors.toList());

					to_remove.forEach(a->{
						Asset.Tradable tra = registry.remove(a.symbol).asTradable();
						msg.unsubscribe(Topics.ASK_ORDERS_BOOK_PREFIX.topic+tra.symbol, tra.book.askBookOrdersHandler);
						msg.unsubscribe(Topics.BID_ORDERS_BOOK_PREFIX.topic+tra.symbol, tra.book.bidBookOrdersHandler);
					});

					chains.values().forEach(OptionChain::cleanExpired);
				}
			});

			/**
			 * When new instruments are put into Instruments Registry a link between data and orders book is created
			 */
			msg.subscribe(Topics.INSTRUMENT.topic, (last, _msg)->{
				final Asset instr = (Asset)_msg;
				registry.put(instr.symbol, instr);
				if(instr instanceof Asset.Tradable){
					msg.subscribe(Topics.ASK_ORDERS_BOOK_PREFIX.topic+instr.symbol, ((Asset.Tradable)instr).book.askBookOrdersHandler);
					msg.subscribe(Topics.BID_ORDERS_BOOK_PREFIX.topic+instr.symbol, ((Asset.Tradable)instr).book.bidBookOrdersHandler);
				}
			});
			initialized = true;
		}
		return initialized;
	}

	@Override
	public Market getMarketBySymbol(String symbol) {
		Asset a = getBySymbol(symbol);
		return a.market;
	}

	@Override
	public Asset getBySymbol(String symbol) {
		return registry.get(symbol);
	}

	@Override
	public Collection<Asset> getAll() {
		return Collections.unmodifiableCollection(registry.values());
	}

	@Override
	public Collection<Asset> getRelated(final String symbol) {
		return Collections.unmodifiableCollection(
				registry.values()
					.stream()
					.filter((asset)->asset instanceof Asset.DerivativeInstr)
					.filter((asset)-> ((Asset.DerivativeInstr)asset).underlyingAsset.symbol.equals(symbol))
					.collect(Collectors.toList())
				);
	}

	@Override
	public OptionChain getOptionChainFor(String symbol) {
		OptionChain chain = chains.get(symbol);
		if(chain == null){
			Asset.Option[] options = registry.values()
					.stream()
					.filter((asset)->asset instanceof Asset.Option)
					.filter((asset)-> ((Asset.DerivativeInstr)asset).underlyingAsset.symbol.equals(symbol))
					.map(i->(Asset.Option)i)
					.sorted((o1, o2)->Double.compare(o1.strike, o2.strike))
					.toArray(Asset.Option[]::new);

			chain = new OptionChain(symbol, options);
			chains.put(symbol, chain);
		}
		return chain;
	}

	@Override
	public Asset getByIsin(String isin) {
		return registry.values().stream()
				.filter((asset)-> isin.equals(asset.isin))
				.findFirst().get();
	}
}
