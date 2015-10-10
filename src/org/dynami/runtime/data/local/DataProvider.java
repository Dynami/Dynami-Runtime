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
package org.dynami.runtime.data.local;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.dynami.core.Event;
import org.dynami.core.assets.Asset;
import org.dynami.core.assets.Book;
import org.dynami.core.assets.Book.Side;
import org.dynami.core.bus.IMsg;
import org.dynami.core.config.Config;
import org.dynami.core.data.Bar;
import org.dynami.core.data.IData;
import org.dynami.core.utils.DTime;
import org.dynami.runtime.IDataHandler;
import org.dynami.runtime.IService;
import org.dynami.runtime.data.BarData;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.topics.Topics;

public class DataProvider implements IService, IDataHandler {

	private static final SimpleDateFormat intradaySecondsFormat = new SimpleDateFormat(TRACK_RECORD.INTRADAY_SECONDS_DATE_FORMAT);
	private static final SimpleDateFormat intradayMinutesFormat = new SimpleDateFormat(TRACK_RECORD.INTRADAY_MINUTES_DATE_FORMAT);
	private static final SimpleDateFormat dailyFormat = new SimpleDateFormat(TRACK_RECORD.DAILY_DATE_FORMAT);
	private static final SimpleDateFormat dailyShortFormat = new SimpleDateFormat(TRACK_RECORD.DAILY_SHORT_DATE_FORMAT);

	private static final String SYMBOL = "FTSEMIB";
	private IData historical;
	private long clockFrequence = 0;
	private double bidAskSpread = 5.0;
	private final long DAY_MILLIS = 1000*60*60*24;

	private final AtomicBoolean isStarted = new AtomicBoolean(true);
	private final AtomicBoolean isRunning = new AtomicBoolean(false);
	
	private final Random random = new Random(1L);

	private IMsg msg = Execution.Manager.msg();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public boolean init(Config config) throws Exception {
		historical = restorePriceData(new File("./resources/FTSEMIB_1M_2015_10_02.txt"));
		historical = historical.changeCompression(IData.COMPRESSION_UNIT.DAY);
		
		msg.forceSync(true);
		
		Asset.Future ftsemib = new Asset.Future(
				SYMBOL,
				"IT00002344",
				"FTSE-MIB",
				5.0,
				.05,
				1L,
				"IDEM",
				dailyFormat.parse("31/12/2015").getTime(),
				1L,
				"^FTSEMIB");

		msg.async(Topics.INSTRUMENT.topic, ftsemib);
		new Thread(new Runnable() {
			private final AtomicInteger idx = new AtomicInteger(0);
			@Override
			public void run() {
				int OPEN = 0, HIGH = 1, LOW = 2 , CLOSE = 3;
				Bar prevBar = null, currentBar, nextBar;
				while(isStarted.get()){
					if(isRunning.get()){
						currentBar = historical.get(idx.getAndIncrement());
						if(historical.size() > idx.get()){
							nextBar = historical.get(idx.get());							
						} else {
							nextBar = null;
						}
//						System.out.println("DataProvider.init(Bar) "+currentBar);
						HIGH = (random.nextBoolean())?1:2;
						LOW = (HIGH == 1)?2:1;
						double price = currentBar.close;
						for(int i = 0 ; i < 4; i++){
							if(i == OPEN){
								price = currentBar.open;
							} else if(i == HIGH){
								price = currentBar.high;
							} else if(i == LOW){
								price = currentBar.low;
							} else if(i == CLOSE){
								price = currentBar.close;
							}
							
							Book.Orders bid = new Book.Orders(currentBar.symbol, currentBar.time, Side.BID, 1, price-bidAskSpread/2, 100);
							msg.async(Topics.BID_ORDERS_BOOK_PREFIX.topic+currentBar.symbol, bid);
							msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(currentBar.symbol, bid));
							
							Book.Orders ask = new Book.Orders(currentBar.symbol, currentBar.time, Side.ASK, 1, price+bidAskSpread/2, 100);
							msg.async(Topics.ASK_ORDERS_BOOK_PREFIX.topic+currentBar.symbol, ask);
							msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(currentBar.symbol, ask));
							
							if(i == OPEN){
								if(prevBar != null && currentBar.time/DAY_MILLIS > prevBar.time/DAY_MILLIS){
									//FIXME prevBar for new daily bar is wrong
									DTime.Clock.update(currentBar.time);
									msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(currentBar.symbol, currentBar, Event.Type.OnBarOpen, Event.Type.OnDayOpen));
								} else {
									//FIXME prevBar for new daily bar is wrong
									DTime.Clock.update(currentBar.time);
									msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(currentBar.symbol, currentBar, Event.Type.OnBarOpen));									
								}
							} else if(i == CLOSE){
								DTime.Clock.update(currentBar.time);
								if(nextBar == null || currentBar.time/DAY_MILLIS < nextBar.time/DAY_MILLIS){
									msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(currentBar.symbol, currentBar, Event.Type.OnBarClose, Event.Type.OnDayClose));
								} else {
									msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(currentBar.symbol, currentBar, Event.Type.OnBarClose));
								}
							}
							
							try { Thread.sleep(clockFrequence/4); } catch (InterruptedException e) {}
						}
						prevBar = currentBar;
					} else {
						try { Thread.sleep(clockFrequence); } catch (InterruptedException e) {}
					}
				}
			}
		}).start();

		return true;
	}

	@Override
	public boolean start() {
		isRunning.set(true);
		return true;
	}

	@Override
	public boolean stop() {
		isRunning.set(false);
		return true;
	}

	@Override
	public boolean resume() {
		isRunning.set(true);
		return true;
	}

	@Override
	public boolean dispose() {
		isRunning.set(false);
		isStarted.set(false);
		return true;
	}

	@Override
	public Status getStatus() {
		return null;
	}

	private static BarData restorePriceData(final File f) throws Exception {
		SimpleDateFormat dateParser = null;
		BufferedReader reader = null;
		try {
			String[] tmp = null;
			reader = new BufferedReader(new FileReader(f));
			String line = null;
			boolean isFirst = true;
			BarData barData = new BarData();
			while((line = reader.readLine()) != null) {
				if(isFirst) {
					isFirst = false;
					continue;
				}
				tmp = line.split("\t");

				if(dateParser == null){
					dateParser = getApproprieateFormat(tmp[TRACK_RECORD.DATE]);
				}
				long time = dateParser.parse(tmp[TRACK_RECORD.DATE]).getTime();
				double open = Double.parseDouble(tmp[TRACK_RECORD.OPEN].replace(',', '.'));
				double high = Double.parseDouble(tmp[TRACK_RECORD.HIGH].replace(',', '.'));
				double low = Double.parseDouble(tmp[TRACK_RECORD.LOW].replace(',', '.'));
				double close = Double.parseDouble(tmp[TRACK_RECORD.CLOSE].replace(',', '.'));
				long volume = Long.parseLong(tmp[TRACK_RECORD.VOLUME]);
				barData.append(new Bar(SYMBOL, open, high, low, close, volume, 0, time));
			}
			return barData;

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			if(reader != null)
				reader.close();
		}
	}

	private static SimpleDateFormat getApproprieateFormat(final String in){
		if(in != null){
			final int lenght = in.length();
			if(lenght == TRACK_RECORD.DAILY_DATE_FORMAT.length()){
				return dailyFormat;
			} else if(lenght == TRACK_RECORD.DAILY_SHORT_DATE_FORMAT.length()){
				return dailyShortFormat;
			} else if(lenght == TRACK_RECORD.INTRADAY_MINUTES_DATE_FORMAT.length()){
				return intradayMinutesFormat;
			} else if(lenght == TRACK_RECORD.INTRADAY_SECONDS_DATE_FORMAT.length()){
				return intradaySecondsFormat;
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	public static class TRACK_RECORD {
		public static final int DATE = 0;
		public static final int OPEN = 1;
		public static final int HIGH = 2;
		public static final int LOW = 3;
		public static final int CLOSE = 4;
		public static final int VOLUME = 5;
		public static final String INTRADAY_SECONDS_DATE_FORMAT = "dd/MM/yyyy HH:mm:ss";
		public static final String INTRADAY_MINUTES_DATE_FORMAT = "dd/MM/yyyy HH:mm";
		public static final String DAILY_DATE_FORMAT = "dd/MM/yyyy";
		public static final String DAILY_SHORT_DATE_FORMAT = "yyyyMMdd";
	}
}