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
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

import org.dynami.core.assets.Asset;
import org.dynami.core.bus.IMsg;
import org.dynami.core.config.Config;
import org.dynami.core.services.IAssetService;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.impl.Service;
import org.dynami.runtime.topics.Topics;

public class AssetService extends Service implements IAssetService  {
	private final Map<String, Asset> registry = new ConcurrentSkipListMap<>();

	private final IMsg msg = Execution.Manager.msg();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public boolean init(Config config) throws Exception {


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

		return true;
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
					.filter((asset)-> ((Asset.DerivativeInstr)asset).parentSymbol.equals(symbol))
					.collect(Collectors.toList())
				);
	}

	@Override
	public Asset getByIsin(String isin) {
		return registry.values().stream()
				.filter((asset)-> isin.equals(asset.isin))
				.findFirst().get();
	}
}
