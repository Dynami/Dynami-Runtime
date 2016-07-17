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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.dynami.core.Event;
import org.dynami.core.assets.Asset;
import org.dynami.core.assets.Asset.Option;
import org.dynami.core.assets.Book;
import org.dynami.core.assets.Book.Side;
import org.dynami.core.assets.Market;
import org.dynami.core.bus.IMsg;
import org.dynami.core.config.Config;
import org.dynami.core.data.Bar;
import org.dynami.core.data.IData;
import org.dynami.core.data.IVolatilityEngine;
import org.dynami.core.data.vola.RogersSatchellVolatilityEngine;
import org.dynami.core.utils.DTime;
import org.dynami.core.utils.DUtils;
import org.dynami.runtime.IDataHandler;
import org.dynami.runtime.IService;
import org.dynami.runtime.data.BarData;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.topics.Topics;
import org.dynami.runtime.utils.BSEurOptionsUtils;
import org.dynami.runtime.utils.EuropeanBlackScholes;
import org.dynami.runtime.utils.JQuantLibUtils;
import org.dynami.runtime.utils.LastPriceEngine;

@Config.Settings(name = "TextFileDataHandler settings", description = "bla bla bla")
public class TextFileDataHandler implements IService, IDataHandler {
	private final AtomicInteger idx = new AtomicInteger(0);
	private static final SimpleDateFormat intradaySecondsFormat = new SimpleDateFormat(
			TRACK_RECORD.INTRADAY_SECONDS_DATE_FORMAT);
	private static final SimpleDateFormat intradayMinutesFormat = new SimpleDateFormat(
			TRACK_RECORD.INTRADAY_MINUTES_DATE_FORMAT);
	private static final SimpleDateFormat dailyFormat = new SimpleDateFormat(TRACK_RECORD.DAILY_DATE_FORMAT);
	private static final SimpleDateFormat dailyShortFormat = new SimpleDateFormat(TRACK_RECORD.DAILY_SHORT_DATE_FORMAT);
	private final AtomicBoolean isStarted = new AtomicBoolean(true);
	private final AtomicBoolean isRunning = new AtomicBoolean(false);
	private final IMsg msg = Execution.Manager.msg();
	private IVolatilityEngine volaEngine;
	private IData historical;
	private BarData computedHistorical = new BarData();
	private final List<Option> options = new CopyOnWriteArrayList<>();

	private Class<? extends IVolatilityEngine> volaEngineClass = RogersSatchellVolatilityEngine.class;

	@Config.Param(name = "Symbol", description = "Main symbol")
	private String symbol = "FTSEMIB";

	@Config.Param(name = "Clock frequency", description = "Execution speed. Set to zero for no latency.", step = 1)
	private Long clockFrequency = 500L;

	@Config.Param(name = "Future Bid/Ask spread", description = "Bid/Ask spread expressed in points", step = 0.01)
	private Double bidAskSpread = 5.0;

	@Config.Param(name = "Data file", description = "Text file containing instrument historical data")
	private File dataFile = new File("./resources/FTSEMIB_1M_2015_10_02.txt");//FTSEMIB_1M_2015_10_02 FTSEMIB_1M_2016_04_30

	@Config.Param(name = "Time compression", description = "Compression used for time frame", min = 1, max = 100, step = 1, type = Config.Type.TimeFrame)
	private Long compressionRate = IData.TimeUnit.Hour.millis() * 1;

	@Config.Param(name = "Future Point Value", description = "Future point value", step = .1)
	private Double futurePointValue = 5.;

	@Config.Param(name = "Riskfree Rate", description = "Risk free rate", step =.0001, min=0.000, max=1.)
	private Double riskfreeRate = .0014;

	@Config.Param(name = "Enable option pricing", description = "Activate simulated option pricing")
	private Boolean optionPricing = true;

	@Config.Param(name = "Option Strike Step", description = "Number of points between one option strike and another", step = .1)
	private Double optionStep = 500.;

	@Config.Param(name = "Option Point Value", description = "Option point value", step = .1)
	private Double optionPointValue = 2.5;

	@Config.Param(name = "Number of strikes", description = "Number of strikes above and below first price", max = 50, step = 1)
	private Integer optionStrikes = 30;

	@Config.Param(name = "% Margin required", description = "Margination required in percentage points", step = .1)
	private Double marginRequired = .125;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public boolean reset() {
		idx.set(0);
		computedHistorical = new BarData();
		return true;
	}

	@Override
	public boolean init(Config config) throws Exception {
		if(!dataFile.exists()){
			Execution.Manager.msg().async(Topics.INTERNAL_ERRORS.topic, new IllegalStateException("File ["+dataFile.getAbsolutePath()+"] doesn't exist"));
			return false;
		}
		volaEngine = volaEngineClass.newInstance();
		historical = restorePriceData(dataFile);
		historical.setAutoCompressionRate();
		historical = historical.changeCompression(compressionRate);

		msg.forceSync(true);

		Market market = new Market("IDEM", "IDEM", Locale.ITALY, LocalTime.of(9, 0, 0), LocalTime.of(17, 25, 0));

		Asset.Index index = new Asset.Index(Asset.Family.Index, "^FTSEMIB", "IT0000000001", "FTSEMIB Index", 1, .01, market);

		Asset.Future ftsemib = new Asset.Future(symbol, "IT00002344", "FTSE-MIB", futurePointValue, .05, marginRequired,
				LastPriceEngine.MidPrice, market, dailyFormat.parse("31/12/9999").getTime(), 1L, index, () -> 1.); // risk

		msg.async(Topics.INSTRUMENT.topic, ftsemib);

		final Bar lastBar = historical.last();
		final Bar firstBar = historical.get(0);
		if (optionPricing) {
			double firstStrike = (firstBar.close % optionStep > optionStep / 2)
					? firstBar.close - firstBar.close % optionStep
					: firstBar.close + (optionStep - firstBar.close % optionStep);

			long[] expirations = computeExpirationInPeriod(firstBar.time, lastBar.time);

			for (int i = 0; i <= optionStrikes / 2; i++) {
				for (int j = 0; j < expirations.length; j++) {
					double upperStrike = firstStrike + (optionStep * i);
					double lowerStrike = firstStrike - (optionStep * i);
					for (int z = 0; z < 2; z++) {
						Option.Type type = Option.Type.values()[z];
						String upperOptionSymbol = DUtils.getOptionSymbol(symbol, type, expirations[j], upperStrike);
						String lowerOptionSymbol = DUtils.getOptionSymbol(symbol, type, expirations[j], lowerStrike);

						Option opt0 = createOption(market, "MIBO", ftsemib,
								DUtils.getOptionName(symbol, type, expirations[j], upperStrike), upperOptionSymbol,
								type, optionPointValue.doubleValue(), marginRequired.doubleValue(), expirations[j],
								upperStrike);
						msg.async(Topics.INSTRUMENT.topic, opt0);
						options.add(opt0);

						Option opt1 = createOption(market, "MIBO", ftsemib,
								DUtils.getOptionName(symbol, type, expirations[j], lowerStrike), lowerOptionSymbol,
								type, optionPointValue.doubleValue(), marginRequired.doubleValue(), expirations[j],
								lowerStrike);

						msg.async(Topics.INSTRUMENT.topic, opt1);
						options.add(opt1);
					}
				}
			}
		}


		new Thread(new Runnable() {
			@Override
			public void run() {
				int OPEN = 0, CLOSE = 1;
				Bar prevBar = null, currentBar, nextBar;
				while (isStarted.get()) {
					if (isRunning.get()) {
						
						if (idx.get() >= historical.size()) {
							System.out.println("No more data!!! Give X or XX command to print final status");
							msg.sync(Topics.STRATEGY_EVENT.topic, Event.Factory.noMoreDataEvent(symbol));
							break;
						}
						currentBar = historical.get(idx.getAndIncrement());
						computedHistorical.append(currentBar);

						if (historical.size() > idx.get()) {
							nextBar = historical.get(idx.get());
						} else {
							nextBar = null;
						}
						try {
							double price = currentBar.close;
							for (int i = 0; i < 2; i++) {
								if (i == OPEN) {
									price = currentBar.open;
								} else if (i == CLOSE) {
									price = currentBar.close;
								}

								if(optionPricing){
									optionsPricing(msg, market, compressionRate, computedHistorical, volaEngine, options,
											optionStep, currentBar.time, price, bidAskSpread, riskfreeRate);
								}
								
								Book.Orders bid = new Book.Orders(currentBar.symbol, currentBar.time, Side.BID, 1,
										price - bidAskSpread / 2, 100);
								msg.async(Topics.BID_ORDERS_BOOK_PREFIX.topic + currentBar.symbol, bid);
								msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(currentBar.symbol, bid));
								
								Book.Orders ask = new Book.Orders(currentBar.symbol, currentBar.time, Side.ASK, 1,
										price + bidAskSpread / 2, 100);
								msg.async(Topics.ASK_ORDERS_BOOK_PREFIX.topic + currentBar.symbol, ask);
								msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(currentBar.symbol, ask));
								
								if (i == OPEN) {
									DTime.Clock.update(currentBar.time - compressionRate);
									if (prevBar != null && currentBar.time / DUtils.DAY_MILLIS > prevBar.time / DUtils.DAY_MILLIS) {
										msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(currentBar.symbol, DTime.Clock.getTime(),
												currentBar, Event.Type.OnBarOpen, Event.Type.OnDayOpen));
									} else {
										msg.async(Topics.STRATEGY_EVENT.topic,
												Event.Factory.create(currentBar.symbol, DTime.Clock.getTime(), currentBar, Event.Type.OnBarOpen));
									}
								} else if (i == CLOSE) {
									DTime.Clock.update(currentBar.time);
									if (nextBar == null || currentBar.time / DUtils.DAY_MILLIS < nextBar.time / DUtils.DAY_MILLIS) {
										msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(currentBar.symbol, DTime.Clock.getTime(),
												currentBar, Event.Type.OnBarClose, Event.Type.OnDayClose));
									} else {
										msg.async(Topics.STRATEGY_EVENT.topic,
												Event.Factory.create(currentBar.symbol, DTime.Clock.getTime(), currentBar, Event.Type.OnBarClose));
									}
								}

								try {
									TimeUnit.MILLISECONDS.sleep(clockFrequency.longValue() / 2);
								} catch (InterruptedException e) {}
							}
							
						} catch(RuntimeException e){
							Execution.Manager.msg().async(Topics.INTERNAL_ERRORS.topic, e);
						}
						
						prevBar = currentBar;
					} else {
						try { Thread.sleep(clockFrequency.longValue()); } catch (InterruptedException e) {}
					}
				}
				System.out.println("TextFileDataHandler.init() closing TextFileDataHandler thread");
			}
		}, "TextFileDataHandler").start();
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
	public boolean isDisposed() {
		return !isStarted.get();
	}

	@Override
	public boolean dispose() {
		isStarted.set(false);
		isRunning.set(false);
		System.out.println("TextFileDataHandler.dispose()");
		return true;
	}

	private static void optionsPricing(final IMsg msg, final Market market, final long compresssionRate,
			final BarData data, final IVolatilityEngine volaEngine, final List<Option> options, final double optionStep,
			final long time, final double spot, final double bidAskSpread, final double riskfreeRate) {
		for (Option o : options) {
			if (o.isExpired(time)) {
				options.remove(o);
				continue;
			}
			int daysLeft = o.daysToExpiration(time);
//			int daysLeft = 20;
			int strikesFromAtm = 1+(int)(Math.abs(spot - o.strike) / optionStep);

			final double factor = volaEngine.annualizationFactor(compresssionRate, daysLeft, market);
			final double vola = data.getVolatility(volaEngine, daysLeft) * factor;

			if (vola > 0) {
//				System.out.println("TextFileDataHandler.optionsPricing() "+spot);
				double optBidPrice = EuropeanBlackScholes.price(o.type, (spot - ((bidAskSpread / 2) * strikesFromAtm)),
						o.strike, vola, (double) daysLeft / DUtils.YEAR_DAYS, riskfreeRate);
				double optAskPrice = EuropeanBlackScholes.price(o.type, (spot + ((bidAskSpread / 2) * strikesFromAtm)),
						o.strike, vola, (double) daysLeft / DUtils.YEAR_DAYS, riskfreeRate);

				Book.Orders bid = new Book.Orders(o.symbol, time, Side.BID, 1, optBidPrice, 100);
				msg.async(Topics.BID_ORDERS_BOOK_PREFIX.topic + o.symbol, bid);
				msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(o.symbol, bid));

				Book.Orders ask = new Book.Orders(o.symbol, time, Side.ASK, 1, optAskPrice, 100);
				msg.async(Topics.ASK_ORDERS_BOOK_PREFIX.topic + o.symbol, ask);
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
			while ((line = reader.readLine()) != null) {
				if (isFirst) {
					isFirst = false;
					continue;
				}
				tmp = line.split("\t");

				if (dateParser == null) {
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
			if (reader != null)
				reader.close();
		}
	}

	private static SimpleDateFormat getApproprieateFormat(final String in) {
		if (in != null) {
			final int lenght = in.length();
			if (lenght == TRACK_RECORD.DAILY_DATE_FORMAT.length()) {
				return dailyFormat;
			} else if (lenght == TRACK_RECORD.DAILY_SHORT_DATE_FORMAT.length()) {
				return dailyShortFormat;
			} else if (lenght == TRACK_RECORD.INTRADAY_MINUTES_DATE_FORMAT.length()) {
				return intradayMinutesFormat;
			} else if (lenght == TRACK_RECORD.INTRADAY_SECONDS_DATE_FORMAT.length()) {
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

	public Boolean isOptionPricing() {
		return optionPricing;
	}

	public void setOptionPricing(Boolean optionPricing) {
		this.optionPricing = optionPricing;
	}

	public static Asset.Option createOption(Market market, String prefix, Asset parent, String name, String isin,
			Option.Type type, double pointValue, double margin, long expire, double strike) throws Exception {

		return new Asset.Option(DUtils.getOptionSymbol(prefix, type, expire, strike), isin,
				DUtils.getOptionName(prefix, type, expire, strike), pointValue, .05, margin, LastPriceEngine.MidPrice,
				market, expire, 1L, parent, () -> 0., // fake risk free rate
														// provider
				strike, type, Asset.Option.Exercise.European, new JQuantLibUtils.GreeksEngine(),
				BSEurOptionsUtils.implVola,
				EuropeanBlackScholes.OptionPricingEngine);
	}

	/**
	 * Get expiration dates (third Friday of each month) in the date interval.
	 * @param start
	 * @param end
	 */
	private static long[] computeExpirationInPeriod(long start, long end) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(start);
		cal.set(Calendar.DAY_OF_MONTH, 1);
		List<Long> expires = new ArrayList<>();
		int dayOfWeek;
		int currentMonth = cal.get(Calendar.MONTH);
		int countFriday = 0;
		while (cal.getTimeInMillis() <= end) {
			dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
			if (currentMonth != cal.get(Calendar.MONTH)) {
				countFriday = 0;
				currentMonth = cal.get(Calendar.MONTH);
			}

			if (dayOfWeek % Calendar.FRIDAY == 0) {
				++countFriday;
				if (countFriday == 3) {
					expires.add(cal.getTimeInMillis());
				}
			}
			cal.add(Calendar.DAY_OF_MONTH, 1);
		}
		return expires.stream()
				.mapToLong(Long::longValue)
				.filter((o) -> o >= start)
				.toArray();
	}
}
