package org.dynami.runtime.handlers;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.atria.dao.Criteria;
import org.atria.dao.DAO;
import org.atria.dao.DAOPrimitives;
import org.atria.dao.IEntity;
import org.atria.dao.IField;
import org.dynami.core.Event;
import org.dynami.core.assets.Asset;
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
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.topics.Topics;
import org.dynami.runtime.utils.BSEurOptionsUtils;
import org.dynami.runtime.utils.EuropeanBlackScholes;
import org.dynami.runtime.utils.JQuantLibUtils;
import org.dynami.runtime.utils.LastPriceEngine;

@Config.Settings(description="Parameters for executing stored trasi test data")
public class TrasiTestDataHandler implements IService, IDataHandler {
	private static final SimpleDateFormat DF = new SimpleDateFormat("dd/MM/yyyy");
	private final AtomicInteger idx = new AtomicInteger(0);
	private final AtomicBoolean isStarted = new AtomicBoolean(true);
	private final AtomicBoolean isRunning = new AtomicBoolean(false);
	private final IMsg msg = Execution.Manager.msg();
	private Class<? extends IVolatilityEngine> volaEngineClass = RogersSatchellVolatilityEngine.class;

	@Config.Param(name = "Symbol", description = "Main symbol")
	private String symbol = "FTSEMIB";

	@Config.Param(name = "Clock frequency", description = "Execution speed. Set to zero for no latency.", step = 1)
	private Long clockFrequency = 500L;

	@Config.Param(name = "DB file", description = "SQLite3 database file")
	private File databaseFile = new File("/Users/Dacia/Documents/02.Ale/workspace/trasi-data-test/trasi-data-test.db");
	
	@Config.Param(name="Starting date", description="Starting date for strategy")
	private Date startFrom = parse("24/07/2017");
	
	@Config.Param(name = "Option Expire Date", description = "Select option chain")
	private Date expire = parse("18/08/2017");

	@Config.Param(name = "Time compression", description = "Compression used for time frame", min = 1, max = 100, step = 1, type = Config.Type.TimeFrame)
	private Long compressionRate = IData.TimeUnit.Minute.millis() * 5;

	@Config.Param(name = "Riskfree Rate", description = "Risk free rate", step =.0001, min=0.000, max=1.)
	private Double riskfreeRate = .0014;

	@Config.Param(name = "% Margin required", description = "Margination required in percentage points", step = .001)
	private Double marginRequired = .125;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public boolean reset() {
		idx.set(0);
		return true;
	}

	@Override
	public boolean init(Config config) throws Exception {
//		volaEngine = volaEngineClass.newInstance();
		if(!databaseFile.exists()){
			Execution.Manager.msg().async(Topics.INTERNAL_ERRORS.topic, new IllegalStateException("File ["+databaseFile.getPath()+"] doesn't exist"));
			return false;
		}
		DAO.Sqlite.use(databaseFile);
		
		msg.forceSync(true);
		final AtomicLong prevTime = new AtomicLong(0);
		final Market market = new Market("IDEM", "IDEM", Locale.ITALY, LocalTime.of(9, 0, 0), LocalTime.of(17, 25, 0));
		final Asset.Index index = new Asset.Index(Asset.Family.Index, "^FTSEMIB", "IT0000000001", "FTSEMIB Index", 1, .01, market);
		
		final TrasiAsset.Future fut = DAO.Sqlite.first(new Criteria<>(TrasiAsset.Future.class).andEqualsGreaterThan("expire", expire).orderBy("expire"));
		
		final List<TrasiAsset.Option> options = DAO.Sqlite.select(new Criteria<>(TrasiAsset.Option.class).andEquals("expire", expire));
		
		final List<DAOPrimitives.Long> times = DAO.Sqlite.select(
				DAOPrimitives.Long.class, 
				" select distinct ob.time as 'value' "
				+ "from book ob , options o "
				+ "where o.expire = ? "
				+ "and o.ticker = ob.ticker "
				+ "and ob.time >= ?", 
				expire, startFrom);
		
		final Asset.Future future = new Asset.Future(fut.getTicker(), fut.getIsin(), fut.getName(), fut.getPointValue(), .05, marginRequired, LastPriceEngine.MidPrice, market, fut.getExpire().getTime(), 1L, index, () -> 1.);
		msg.sync(Topics.INSTRUMENT.topic, future);

		for(TrasiAsset.Option opt : options) {
			final Asset.Option option = new Asset.Option(opt.getTicker(), opt.getIsin(), opt.getName(), opt.getPointValue(), .05, marginRequired, LastPriceEngine.MidPrice, 
					market, opt.getExpire().getTime(), 
					1L, future, () -> riskfreeRate, opt.getStrike(), 
					(opt.getOptionType().equals(TrasiAsset.Option.Type.CALL.toString()))?Asset.Option.Type.CALL:Asset.Option.Type.PUT, 
					Asset.Option.Exercise.European,  new JQuantLibUtils.GreeksEngine(), BSEurOptionsUtils.implVola, EuropeanBlackScholes.OptionPricingEngine);
			msg.sync(Topics.INSTRUMENT.topic, option);
		}
		
//		final TrasiBookSpot fBook = DAO.Sqlite.first(new Criteria<>(TrasiBookSpot.class).andEquals("ticker", fut.getTicker()).andEquals("time", times.get(0).getValue()));
//		final double fPrice = fBook.avgPrice();
//		final Bar firstBar = new Bar(fBook.ticker, fPrice, fPrice, fPrice, fPrice, 0, fBook.getTime().getTime());
//		msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(fBook.ticker, DTime.Clock.getTime(), firstBar, Event.Type.OnBarClose));
		System.out.println("TrasiTestDataHandler.init()");
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				while (isStarted.get()) {
					if (isRunning.get()) {
						try {
							DAO.Sqlite.use(databaseFile);
							if (idx.get() >= times.size()) {
								System.out.println("No more data!!! Give X or XX command to print final status");
								msg.sync(Topics.STRATEGY_EVENT.topic, Event.Factory.noMoreDataEvent(symbol));
								break;
							}
							final long time = times.get(idx.getAndIncrement()).getValue();
							final TrasiBookSpot fBook = DAO.Sqlite.first(new Criteria<>(TrasiBookSpot.class).andEquals("ticker", fut.getTicker()).andEquals("time", time));
							
							double fPrice = fBook.avgPrice(); 
							DTime.Clock.update(time);
							// if no future price available skip elaboration
							if(fPrice == 0) continue;
							
							/**
							 * Fire book prices for futures
							 */
							Book.Orders fBid = new Book.Orders(fBook.ticker, time, Side.BID, 1, fBook.bid, fBook.bidVolume);
							msg.async(Topics.BID_ORDERS_BOOK_PREFIX.topic + fBook.ticker, fBid);
							msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.createOnTickEvent(fBook.ticker, fBid));
							
							Book.Orders fAsk = new Book.Orders(fBook.ticker, time, Side.ASK, 1, fBook.ask, fBook.askVolume);
							msg.async(Topics.ASK_ORDERS_BOOK_PREFIX.topic + fBook.ticker, fAsk);
							msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.createOnTickEvent(fBook.ticker, fAsk));

							/**
							 * Fire book prices for options
							 */
							for(TrasiAsset.Option opt : options) {
								final TrasiBookSpot oBook = DAO.Sqlite.first(new Criteria<>(TrasiBookSpot.class).andEquals("ticker", opt.getTicker()).andEquals("time", time));
								if(oBook != null && oBook.bid > 0) {
									Book.Orders oBid = new Book.Orders(oBook.ticker, time, Side.BID, 1, oBook.bid, oBook.bidVolume);
									msg.async(Topics.BID_ORDERS_BOOK_PREFIX.topic + oBook.ticker, oBid);
									msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.createOnTickEvent(oBook.ticker, oBid));
								}
//								
								if(oBook != null && oBook.ask > 0) {
									Book.Orders oAsk = new Book.Orders(oBook.ticker, time, Side.ASK, 1, oBook.ask, oBook.askVolume);
									msg.async(Topics.ASK_ORDERS_BOOK_PREFIX.topic + oBook.ticker, oAsk);
									msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.createOnTickEvent(oBook.ticker, oAsk));
								}
							}
							
							/**
							 * Fire strategy on bar close events
							 */
							final Bar current = new Bar(fBook.ticker, fPrice, fPrice, fPrice, fPrice, 0, time);
							if (prevTime.get() == 0 || (time / DUtils.DAY_MILLIS) > (prevTime.get() / DUtils.DAY_MILLIS)) {
								msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(fBook.ticker, DTime.Clock.getTime(),
										current, Event.Type.OnBarClose, Event.Type.OnDayClose));
							} else {
								msg.async(Topics.STRATEGY_EVENT.topic,
										Event.Factory.create(fBook.ticker, DTime.Clock.getTime(), current, Event.Type.OnBarClose));
							}
								
							prevTime.set(time);
						} catch(Exception e){
							Execution.Manager.msg().async(Topics.INTERNAL_ERRORS.topic, e);
						}
					}
					try { Thread.sleep(clockFrequency.longValue()); } catch (InterruptedException e) {}
				}
				System.out.println("TrasiTestDataHandler::init() closing TextFileDataHandler thread");
			}
		}, "TrasiTestDataHandler").start();
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
		System.out.println("TrasiTestDataHandler.dispose()");
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
	
	private static Date parse(String date) {
		try {
			return DF.parse(date);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static abstract class TrasiAsset {
		@IField(pk=true, lenght=30) 
		private String ticker;
		
		@IField(lenght=50)
		private String name;
		
		@IField(nullable=false, lenght=30) 
		private String isin;
		
		private String type;
		
		@IField(name="point_value", nullable=false, defaultValue="1") 
		private double pointValue;
		
		public static enum Type {Option, Future, MiniFuture}

		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public String getTicker() {
			return ticker;
		}

		public void setTicker(String ticker) {
			this.ticker = ticker;
		}

		public String getIsin() {
			return isin;
		}

		public void setIsin(String isin) {
			this.isin = isin;
		}

		public String getType() {
			return type;
		}

		void setType(String type) {
			this.type = type;
		}

		public double getPointValue() {
			return pointValue;
		}

		public void setPointValue(double pointValue) {
			this.pointValue = pointValue;
		}
		
		private static abstract class Derivative extends TrasiAsset {
			@IField(nullable=false) 
			private Date expire;
			
			public Date getExpire() {
				return expire;
			}
			
			public void setExpire(Date expire) {
				this.expire = expire;
			}
		}
		
		@IEntity(name="options")
		public static class Option extends Derivative {
			{setType(TrasiAsset.Type.Option.name());}
			
			@IField(name="type", nullable=false, lenght=5) 
			private String optionType;
			
			@IField(nullable=false) 
			private double strike;
			
			public String getOptionType() {
				return optionType;
			}
			
			public void setOptionType(String optionType) {
				this.optionType = optionType;
			}
			
			public double getStrike() {
				return strike;
			}
			
			public void setStrike(double strike) {
				this.strike = strike;
			}
			
			public static enum Type {PUT, CALL}

			@Override
			public String toString() {
				return "Option [optionType=" + optionType + ", strike=" + strike + ", getExpire()=" + getExpire()
						+ ", getName()=" + getName() + ", getTicker()=" + getTicker() + ", getIsin()=" + getIsin()
						+ ", getType()=" + getType() + ", getPointValue()=" + getPointValue()
						+ "]";
			};
		}
		
		@IEntity(name="futures")
		public static class Future extends Derivative {
			{setType(TrasiAsset.Type.Future.name());}
		}
	}

	@IEntity(name="book")
	public static class TrasiBookSpot {
		@IField(pk=true) 
		private String ticker;
		
		@IField(pk=true) 
		private Date time;
		
		@IField(defaultValue="0.0") 
		private double bid;
		
		@IField(defaultValue="0.0") 
		private double ask;

		@IField(name="bid_qt", defaultValue="0") 
		private int bidVolume;
		
		@IField(name="ask_qt", defaultValue="0") 
		private int askVolume;
		
		public String getTicker() {
			return ticker;
		}

		public void setTicker(String ticker) {
			this.ticker = ticker;
		}

		public Date getTime() {
			return time;
		}

		public void setTime(Date time) {
			this.time = time;
		}

		public double getBid() {
			return bid;
		}

		public void setBid(double bid) {
			this.bid = bid;
		}

		public double getAsk() {
			return ask;
		}

		public void setAsk(double ask) {
			this.ask = ask;
		}

		public int getBidVolume() {
			return bidVolume;
		}

		public void setBidVolume(int bidVolume) {
			this.bidVolume = bidVolume;
		}

		public int getAskVolume() {
			return askVolume;
		}

		public void setAskVolume(int askVolume) {
			this.askVolume = askVolume;
		}
		
		public double avgPrice() {
			if(ask != 0 && bid != 0) {
				return (ask+bid)/2;
			} else if(ask != 0 ) {
				return ask;
			} else if(bid != 0) {
				return bid;
			} else {
				return 0;
			}
		}
	}
	
}
