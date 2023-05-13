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

import com.ib.client.*;
import com.ib.testbed.contracts.ContractSamples;
import org.dynami.core.assets.Asset;
import org.dynami.core.config.Config;
import org.dynami.runtime.Service;
import org.dynami.runtime.impl.Execution;

import java.util.Currency;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Thread.*;

public class IBService extends Service {
    public static final String ID = "IBService";
    private final AtomicInteger requestId = new AtomicInteger(19000);
    private final EWrapperImpl wrapper = new EWrapperImpl(IBService.this);
    private final Map<Integer, Asset.Tradable> mappings = new TreeMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public <T extends Config> boolean init(T config) throws Exception {
        final EClientSocket m_client = wrapper.getClient();
        final EReaderSignal m_signal = wrapper.getSignal();
        //! [connect]
        m_client.eConnect("127.0.0.1", 4002, 1);
        //! [connect]
        //! [ereader]
        final EReader reader = new EReader(m_client, m_signal);

        reader.start();

        System.out.println("IBService::start() Connected: "+m_client.isConnected());
        //An additional thread is created in this program design to empty the messaging queue
        new Thread(() -> {
            while (m_client.isConnected()) {
                m_signal.waitForSignal();
                try {
                    reader.processMsgs();
                } catch (Exception e) {
                    System.out.println("Exception: "+e.getMessage());
                }
            }
        }).start();
        try {
            sleep(1000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return super.init(config);
    }

    @Override
    public boolean start() {
        return super.start();
    }

    @Override
    public boolean stop() {
        System.out.println("IBService::stop() disconnection");
        //TODO disconnect interactive broker
        if(wrapper.getClient().isConnected()){
            wrapper.getClient().eDisconnect();
        }
        return super.stop();
    }

    public boolean subscribeUSTickDataFeed(Asset.Tradable asset){
        System.out.println("IBService::subscribeUSTickDataFeed()");
        if(!mappings.containsValue(asset)){

            mappings.put(requestId.incrementAndGet(), asset);

            //wrapper.getClient().reqMktData(requestId.get(), USStock(asset), "BidAsk", false, false, null);
            wrapper.getClient().reqTickByTickData(requestId.get(), USStock(asset), "BidAsk", 10, false);
        }
        return true;
    }

    private static Contract USStock(Asset.Tradable asset) {
        //! [stkcontract]
//        Contract contract = new Contract();
//        contract.symbol(asset.symbol);
//        contract.secType("STK");
//        contract.currency("USD");
//        contract.exchange(asset.market.getCode());


        Contract contract = new Contract();
        contract.symbol(asset.symbol); // EUR
        contract.secType("CASH");
        contract.currency("GBP");
        contract.exchange("IDEALPRO");
        return contract;
    }

    public Asset.Tradable getAsset(int idx){
        return mappings.get(idx);
    }
}
