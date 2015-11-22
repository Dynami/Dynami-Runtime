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
package org.dynami.runtime.handlers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.dynami.core.Event;
import org.dynami.core.assets.Asset;
import org.dynami.core.assets.Asset.Option;
import org.dynami.core.assets.Book;
import org.dynami.core.assets.Book.Side;
import org.dynami.core.bus.IMsg;
import org.dynami.core.config.Config;
import org.dynami.core.data.Bar;
import org.dynami.core.data.IData;
import org.dynami.core.data.IVolatilityEngine;
import org.dynami.core.utils.DTime;
import org.dynami.core.utils.DUtils;
import org.dynami.runtime.IDataHandler;
import org.dynami.runtime.IService;
import org.dynami.runtime.data.BarData;
import org.dynami.runtime.data.vola.CloseToCloseVolatilityEngine;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.topics.Topics;
import org.dynami.runtime.utils.BSEurOptionsUtils;
import org.dynami.runtime.utils.EuropeanBlackScholes;
import org.dynami.runtime.utils.LastPriceEngine;

@Config.Settings(name="TextFileDataHandler settings", description="bla bla bla")
public class TextFileDataHandler implements IService, IDataHandler {
	private static final SimpleDateFormat intradaySecondsFormat = new SimpleDateFormat(TRACK_RECORD.INTRADAY_SECONDS_DATE_FORMAT);
	private static final SimpleDateFormat intradayMinutesFormat = new SimpleDateFormat(TRACK_RECORD.INTRADAY_MINUTES_DATE_FORMAT);
	private static final SimpleDateFormat dailyFormat = new SimpleDateFormat(TRACK_RECORD.DAILY_DATE_FORMAT);
	private static final SimpleDateFormat dailyShortFormat = new SimpleDateFormat(TRACK_RECORD.DAILY_SHORT_DATE_FORMAT);
	private final long DAY_MILLIS = 1000*60*60*24;
	private final AtomicBoolean isStarted = new AtomicBoolean(true);
	private final AtomicBoolean isRunning = new AtomicBoolean(false);
	private final Random random = new Random(1L);
	private final IMsg msg = Execution.Manager.msg();
	private IVolatilityEngine volaEngine;
	private IData historical;
	private BarData computedHistorical = new BarData();
	private Thread thread;
	private final List<Option> options = new CopyOnWriteArrayList<>();
	
	
	//@Config.Param(name="Historical Volatility Engine", description="Historical volatility engine used to price options", values={CloseToCloseVolatilityEngine.class})
	private Class<? extends IVolatilityEngine> volaEngineClass = CloseToCloseVolatilityEngine.class;
	
	@Config.Param(name="Symbol", description="Main symbol")
	private String symbol = "FTSEMIB";
	
	@Config.Param(name="Clock frequency", description="Execution speed. Set to zero for no latency.", step=1)
	private Long clockFrequency = 0L;

	@Config.Param(name="Future Bid/Ask spread", description="Bid/Ask spread expressed in points", step=0.01)
	private Double bidAskSpread = 5.0;
	
	@Config.Param(name="Data file", description="Text file containing instrument historical data")
	private File dataFile = new File("./resources/FTSEMIB_1M_2015_10_02.txt");

	@Config.Param(name="Time compression", description="Compression used for time frame")
	private Long compressionRate = IData.COMPRESSION_UNIT.DAY;
	
	
	@Config.Param(name="Future Point Value", description="Future point value", step=.1)
	private Double futurePointValue = 5.;
	
	@Config.Param(name="Option Strike Step", description="Number of points between one option strike and another", step=.1)
	private Double optionStep = 500.;
	
	@Config.Param(name="Option Point Value", description="Option point value", step=.1)
	private Double optionPointValue = 2.5;
	
	@Config.Param(name="Number of strikes", description="Number of strikes above and below first price", max=50, step=1)
	private Integer optionStrikes = 10;
	
	@Config.Param(name="% Margin required", description="Margination required in percentage points", step=.1)
	private Double marginRequired = .125;
	

	@Override
	public String id() {
		return ID;
	}
	
	@Override
	public boolean init(Config config) throws Exception {
		volaEngine = volaEngineClass.newInstance();
		historical = restorePriceData(dataFile);
		historical = historical.changeCompression(compressionRate);
		
		msg.forceSync(true);
		
		Asset.Future ftsemib = new Asset.Future(
				symbol,
				"IT00002344",
				"FTSE-MIB",
				futurePointValue,
				.05,
				marginRequired,
				LastPriceEngine.MidPrice ,
				"IDEM",
				dailyFormat.parse("31/12/2015").getTime(),
				1L,
				"^FTSEMIB",
				()->1.); // risk free rate

		msg.async(Topics.INSTRUMENT.topic, ftsemib);
		
		final Bar lastBar = historical.last();
		final Bar firstBar = historical.get(0);
		
		double firstStrike = (firstBar.close%optionStep > optionStep/2)?
										firstBar.close - firstBar.close%optionStep:
										firstBar.close + (optionStep-firstBar.close%optionStep);
		
		long[] expirations = computeExpirationInPeriod(firstBar.time, lastBar.time);
		
		for(int i= 1; i < optionStrikes/2; i++){
			for(int j = 0; j < expirations.length; j++){
				double upperStrike = firstStrike+(optionStep*i); 
				double lowerStrike = firstStrike-(optionStep*i);
				for(int z = 0; z < 2; z++){
					Option.Type type = Option.Type.values()[z];
					String upperOptionSymbol = DUtils.getOptionSymbol(symbol, type, expirations[j], upperStrike);
					String lowerOptionSymbol = DUtils.getOptionSymbol(symbol, type, expirations[j], lowerStrike);
					
					Option opt0 = createOption(
							"MIBO", 
							symbol,
							DUtils.getOptionName(symbol, type, expirations[j], upperStrike), 
							upperOptionSymbol,
							type, 
							optionPointValue.doubleValue(), 
							marginRequired.doubleValue(), 
							expirations[j], 
							upperStrike);
					msg.async(Topics.INSTRUMENT.topic, opt0);
					options.add(opt0);
					
					Option opt1 = createOption(
							"MIBO", 
							symbol,
							DUtils.getOptionName(symbol, type, expirations[j], lowerStrike), 
							lowerOptionSymbol,
							type, 
							optionPointValue.doubleValue(), 
							marginRequired.doubleValue(), 
							expirations[j], 
							lowerStrike);
					
					msg.async(Topics.INSTRUMENT.topic, opt1);
					options.add(opt1);
				}
			}
		}
		
		thread = new Thread(new Runnable() {
			private final AtomicInteger idx = new AtomicInteger(0);
			@Override
			public void run() {
				int OPEN = 0, HIGH = 1, LOW = 2 , CLOSE = 3;
				Bar prevBar = null, currentBar, nextBar;
				while(isStarted.get()){
					if(isRunning.get()){
						if(idx.get() >= historical.size()){
							System.out.println("No more data!!! Give X or XX command to print final status");
							break;
						}
						currentBar = historical.get(idx.getAndIncrement());
						computedHistorical.append(currentBar);
						
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
							
							optionsPricing(msg, computedHistorical, volaEngine, options, optionStep, currentBar.time, price, bidAskSpread);
							
							Book.Orders bid = new Book.Orders(currentBar.symbol, currentBar.time, Side.BID, 1, price-bidAskSpread/2, 100);
							msg.async(Topics.BID_ORDERS_BOOK_PREFIX.topic+currentBar.symbol, bid);
							msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(currentBar.symbol, bid));
							
							Book.Orders ask = new Book.Orders(currentBar.symbol, currentBar.time, Side.ASK, 1, price+bidAskSpread/2, 100);
							msg.async(Topics.ASK_ORDERS_BOOK_PREFIX.topic+currentBar.symbol, ask);
							msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(currentBar.symbol, ask));
							
							if(i == OPEN){
								DTime.Clock.update(currentBar.time-compressionRate);
								if(prevBar != null && currentBar.time/DAY_MILLIS > prevBar.time/DAY_MILLIS){
									msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(currentBar.symbol, currentBar, Event.Type.OnBarOpen, Event.Type.OnDayOpen));
								} else {
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
							
							try {
								TimeUnit.MILLISECONDS.sleep(clockFrequency.longValue()/4);
							} catch (InterruptedException e) {}
						}
						prevBar = currentBar;
					} else {
						try { Thread.sleep(clockFrequency.longValue()); } catch (InterruptedException e) {}
					}
				}
			}
		}, "TextFileDataHandler");
		thread.start();
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
		System.out.println("TextFileDataHandler.dispose()");
		isStarted.set(false);
		isRunning.set(false);
		thread.interrupt();
		return true;
	}

	@Override
	public Status getStatus() {
		return null;
	}
	
	
	private static void optionsPricing(final IMsg msg, final BarData data, final IVolatilityEngine volaEngine, final List<Option> options, final double optionStep, final long time, final double spot, final double bidAskSpread){
		for(Option o:options){
			if(o.isExpired(time)){
				options.remove(o);
			}
			int daysLeft = o.daysToExpiration(time);
			int strikesFromAtm = (int)(Math.abs(spot-o.strike)/optionStep);
			double vola = data.getVolatility(volaEngine, daysLeft);
			if(vola > 0){
				double optBidPrice = EuropeanBlackScholes.price(o.type, spot-((bidAskSpread/2)*strikesFromAtm), o.strike, vola, daysLeft/365., 1.);
				double optAskPrice = EuropeanBlackScholes.price(o.type, spot+((bidAskSpread/2)*strikesFromAtm), o.strike, vola, daysLeft/365., 1.);
				Book.Orders bid = new Book.Orders(o.symbol, time, Side.BID, 1, optBidPrice, 100);
				msg.async(Topics.BID_ORDERS_BOOK_PREFIX.topic+o.strike, bid);
				msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(o.symbol, bid));
				
				Book.Orders ask = new Book.Orders(o.symbol, time, Side.BID, 1, optAskPrice, 100);
				msg.async(Topics.ASK_ORDERS_BOOK_PREFIX.topic+o.symbol, ask);
				msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(o.symbol, ask));
			}
		}
	}

	private BarData restorePriceData(final File f) throws Exception {
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
				barData.append(new Bar(symbol, open, high, low, close, volume, 0, time));
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
	
	public String getSymbol() {
		return symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public Long getClockFrequency() {
		return clockFrequency;
	}

	public void setClockFrequency(Long clockFrequence) {
		this.clockFrequency = clockFrequence;
	}

	public Double getBidAskSpread() {
		return bidAskSpread;
	}

	public void setBidAskSpread(Double bidAskSpread) {
		this.bidAskSpread = bidAskSpread;
	}
	public File getDataFile() {
		return dataFile;
	}

	public void setDataFile(File dataFile) {
		this.dataFile = dataFile;
	}

	public Long getCompressionRate() {
		return compressionRate;
	}

	public void setCompressionRate(Long compressionRate) {
		this.compressionRate = compressionRate;
	}

	public Double getOptionStep() {
		return optionStep;
	}

	public void setOptionStep(Double optionStep) {
		this.optionStep = optionStep;
	}

	public Integer getOptionStrikes() {
		return optionStrikes;
	}

	public void setOptionStrikes(Integer optionStrikes) {
		this.optionStrikes = optionStrikes;
	}
	
	public Class<? extends IVolatilityEngine> getVolaEngineClass() {
		return volaEngineClass;
	}

	public void setVolaEngineClass(Class<? extends IVolatilityEngine> volaEngineClass) {
		this.volaEngineClass = volaEngineClass;
	}

	public Double getFuturePointValue() {
		return futurePointValue;
	}

	public void setFuturePointValue(Double futurePointValue) {
		this.futurePointValue = futurePointValue;
	}

	public Double getOptionPointValue() {
		return optionPointValue;
	}

	public void setOptionPointValue(Double optionPointValue) {
		this.optionPointValue = optionPointValue;
	}

	public Double getMarginRequired() {
		return marginRequired;
	}

	public void setMarginRequired(Double marginRequired) {
		this.marginRequired = marginRequired;
	}

	public static Asset.Option createOption(
			String prefix, String parent, String name, 
			String isin, 
			Option.Type type,
			double pointValue,
			double margin,
			long expire, 
			double strike) throws Exception{
		
		return new Asset.Option(
				DUtils.getOptionSymbol(prefix, type, expire, strike), 
				isin, 
				DUtils.getOptionName(prefix, type, expire, strike), 
				pointValue, 
				.05, 
				margin, 
				LastPriceEngine.MidPrice,
				"IDEM", 
				expire, 
				1L, 
				parent, 
				()->1., // fake risk free rate provider
				strike, 
				type, 
				Asset.Option.Exercise.European,
				BSEurOptionsUtils.greeksEngine, 
				BSEurOptionsUtils.implVola);
	}
	
	private static long[] computeExpirationInPeriod(long start, long end){
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(start);
		cal.set(Calendar.DAY_OF_MONTH, 1);
		List<Long> expires = new ArrayList<>();
		int dayOfWeek;
		int currentMonth = cal.get(Calendar.MONTH);
		int countFriday = 0;
		while(cal.getTimeInMillis() <= end){
			dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
			if(currentMonth != cal.get(Calendar.MONTH)){
				countFriday = 0;
				currentMonth = cal.get(Calendar.MONTH);
			}
			
			if(dayOfWeek%Calendar.FRIDAY == 0){
				++countFriday;
				if(countFriday == 3){
					expires.add(cal.getTimeInMillis());
					//System.out.println("TextFileDataHandler.expirations( ) "+DUtils.DATE_FORMAT.format(cal.getTimeInMillis())+ " "+cal.get(Calendar.DAY_OF_WEEK));
				}
			} 
			cal.add(Calendar.DAY_OF_MONTH, 1);
		}
		return expires.stream()
				.mapToLong(Long::longValue)
				.filter((o)-> o >= start)
				.toArray();
	}
}
