/*
 * Copyright 2023 Alessandro Atria - a.atria@gmail.com
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
package org.dynami.runtime.ib;

import org.dynami.core.Event;
import org.dynami.core.assets.Asset;
import org.dynami.core.assets.Book;
import org.dynami.core.assets.Market;
import org.dynami.core.data.Bar;
import org.dynami.core.bus.IMsg;
import org.dynami.core.config.Config;
import org.dynami.core.data.IData;
import org.dynami.core.data.IVolatilityEngine;
import org.dynami.core.data.vola.RogersSatchellVolatilityEngine;
import org.dynami.core.utils.DTime;
import org.dynami.runtime.IDataHandler;
import org.dynami.runtime.Service;
import org.dynami.runtime.data.BarData;
import org.dynami.runtime.data.RangeBarBuilder;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.topics.Topics;
import org.dynami.runtime.utils.LastPriceEngine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


@Config.Settings(name = "Interactive Broker settings", description = "bla bla bla")
public class IBDataHandler extends Service implements IDataHandler {
    private final AtomicInteger idx = new AtomicInteger(0);
    private final IMsg msg = Execution.Manager.msg();

    private IVolatilityEngine volaEngine;
    private IData historical;
    private BarData computedHistorical = new BarData();
    private Class<? extends IVolatilityEngine> volaEngineClass = RogersSatchellVolatilityEngine.class;

    @Config.Param(name="Tick Range", description = "Mesaures the bar size ", min = 0.00005, max=1.0,  step = 0.00005)
    private double tickRange = 0.0001;

    @Config.Param(name="Is percent tick range", description = "Sets how to compute tick range")
    private boolean percentRange = false;

    //@Config.Param(name = "Riskfree Rate", description = "Risk free rate", step = .0001, min = 0.000, max = 1.)
    //private Double riskfreeRate = .0014;
    @Config.Param(name = "% Margin required", description = "Margination required in percentage points", step = .1)
    private Double marginRequired = .125;
    @Config.Param(name = "Market", description = "Market identification code. eg. ARCA, IDEM, etc.")
    private String marketName = "ARCA";
    @Config.Param(name = "Symbol", description = "Asset symbol eg. IBM, MSFT, etc.")
    private String symbol = "EUR"; // IBM

    @Config.Param(name = "Symbol point value", description = "Point value for the symbol asset")
    private double pointValue = 1.0D; // IBM

    @Config.Param(name = "Symbol tick", description = "The minimum variation price")
    private double tick = 0.001D; // IBM

    @Config.Param(name = "Save raw data", description = "Save tick by tick data in a separate database")
    private boolean saveRawData = false;


    private final AtomicReference<RangeBarBuilder> barBuilder = new AtomicReference<>();
    private final AtomicReference<Bar> prevBar = new AtomicReference<>();

    public boolean init(Config config) throws Exception {
        barBuilder.set(new RangeBarBuilder(symbol, tickRange, percentRange));
        volaEngine = volaEngineClass.getDeclaredConstructor().newInstance();

        final Market market = new Market(marketName, marketName, Locale.US, LocalTime.of(9, 0, 0), LocalTime.of(17, 25, 0));
        final Asset.Share asset = new Asset.Share(symbol, "", symbol, pointValue, tick, marginRequired, market, LastPriceEngine.MidPrice);

        msg.async(Topics.INSTRUMENT.topic, asset);

        msg.subscribe(Topics.TICK.topic+"@"+asset.symbol, (last, price)->{
            final Bar bar = barBuilder.get().push(DTime.Clock.getTime(), (double)price);

            if(bar != null) {
                computedHistorical.append(bar);
                long prevTime = (prevBar.get() != null)?prevBar.get().time : -1;
                if(prevTime > 0){
                    LocalDateTime prevDate = LocalDateTime.from(LocalDate.ofEpochDay(prevTime));
                    LocalDateTime currentDate = LocalDateTime.from(LocalDate.ofEpochDay(bar.time));

                    if(currentDate.getDayOfWeek().equals(prevDate.getDayOfWeek())){
                        System.out.println(("IBDataHandler::init() push bar "+bar.symbol));
                        msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(bar.symbol, bar.time, bar, Event.Type.OnBarClose));
                    } else {
                        msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(bar.symbol, bar.time, bar, Event.Type.OnBarClose, Event.Type.OnDayOpen));
                    }
                } else {
                    msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(bar.symbol, bar.time, bar, Event.Type.OnBarClose));
                }
            }
        });

        Thread.sleep(1000L);
        Execution.Manager.getServiceBus().getService(IBService.class, IBService.ID).subscribeUSTickDataFeed(asset);

        return super.init(config);
    }

    @Override
    public String id() {
        return ID;
    }

    public boolean reset() {
        idx.set(0);
        computedHistorical = new BarData();
        return true;
    }
}
