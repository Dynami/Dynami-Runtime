package org.dynami.runtime.handlers;

import java.io.File;
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

public class TrasiIntradayDataHandler implements IService, IDataHandler {

	private final AtomicInteger idx = new AtomicInteger(0);
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

	@Config.Param(name = "DB file", description = "SQLite3 database file")
	private File databaseFile = new File("./resources/FTSEMIB_1M_2015_10_02.txt");

	@Config.Param(name = "Time compression", description = "Compression used for time frame", min = 1, max = 100, step = 1, type = Config.Type.TimeFrame)
	private Long compressionRate = IData.TimeUnit.Minute.millis() * 1;

	@Config.Param(name = "Riskfree Rate", description = "Risk free rate", step =.0001, min=0.000, max=1.)
	private Double riskfreeRate = .0014;

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
		if(!databaseFile.exists()){
			Execution.Manager.msg().async(Topics.INTERNAL_ERRORS.topic, new IllegalStateException("File ["+databaseFile.getAbsolutePath()+"] doesn't exist"));
			return false;
		}
		volaEngine = volaEngineClass.newInstance();
		/**
		 * Read data from sqlite
		 */
		historical = null;//restorePriceData(databaseFile);
		historical.setAutoCompressionRate();
		historical = historical.changeCompression(compressionRate);

		msg.forceSync(true);

		Market market = new Market("IDEM", "IDEM", Locale.ITALY, LocalTime.of(9, 0, 0), LocalTime.of(17, 25, 0));

		Asset.Index index = new Asset.Index(Asset.Family.Index, "^FTSEMIB", "IT0000000001", "FTSEMIB Index", 1, .01, market);

		/**
		 * TODO initialize futures and options
		 */
		//msg.async(Topics.INSTRUMENT.topic, ftsemib);

		
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
								
								/**
								 * Fire book prices for futures
								 */
								
								Book.Orders bid = new Book.Orders(currentBar.symbol, currentBar.time, Side.BID, 1, price, 100);
								msg.async(Topics.BID_ORDERS_BOOK_PREFIX.topic + currentBar.symbol, bid);
								msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(currentBar.symbol, bid));
								
								Book.Orders ask = new Book.Orders(currentBar.symbol, currentBar.time, Side.ASK, 1, price, 100);
								msg.async(Topics.ASK_ORDERS_BOOK_PREFIX.topic + currentBar.symbol, ask);
								msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(currentBar.symbol, ask));
								
								
								/**
								 * Fire book prices for options
								 */
								
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
									TimeUnit.MILLISECONDS.sleep(clockFrequency.longValue()/2);
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

	public void setDataFile(File dataFile) {
		this.databaseFile = dataFile;
	}

	public Long getCompressionRate() {
		return compressionRate;
	}

	public void setCompressionRate(Long compressionRate) {
		this.compressionRate = compressionRate;
	}

	public Class<? extends IVolatilityEngine> getVolaEngineClass() {
		return volaEngineClass;
	}

	public void setVolaEngineClass(Class<? extends IVolatilityEngine> volaEngineClass) {
		this.volaEngineClass = volaEngineClass;
	}

	public Double getMarginRequired() {
		return marginRequired;
	}

	public void setMarginRequired(Double marginRequired) {
		this.marginRequired = marginRequired;
	}

	public static Asset.Option createOption(TextFileDataHandler dataHandler, Market market, String prefix, Asset parent, String name, String isin,
			Option.Type type, double pointValue, double margin, long expire, double strike) throws Exception {

		return new Asset.Option(DUtils.getOptionSymbol(prefix, type, expire, strike), isin,
				DUtils.getOptionName(prefix, type, expire, strike), pointValue, .05, margin, LastPriceEngine.MidPrice,
				market, expire, 1L, parent, () -> 0., // fake risk free rate
														// provider
				strike, type, Asset.Option.Exercise.European, new JQuantLibUtils.GreeksEngine(),
				BSEurOptionsUtils.implVola,
				EuropeanBlackScholes.OptionPricingEngine /*,dataHandler::priceOption*/);
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
