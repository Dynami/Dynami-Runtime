package org.dynami.runtime.moke;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
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
import org.dynami.core.utils.DUtils;
import org.dynami.runtime.IService;
import org.dynami.runtime.bus.Msg;
import org.dynami.runtime.data.BarData;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.topics.Topics;

public class DataProvider implements IService, IDataProvider {

	private static final SimpleDateFormat intradaySecondsFormat = new SimpleDateFormat(TRACK_RECORD.INTRADAY_SECONDS_DATE_FORMAT);
	private static final SimpleDateFormat intradayMinutesFormat = new SimpleDateFormat(TRACK_RECORD.INTRADAY_MINUTES_DATE_FORMAT);
	private static final SimpleDateFormat dailyFormat = new SimpleDateFormat(TRACK_RECORD.DAILY_DATE_FORMAT);
	private static final SimpleDateFormat dailyShortFormat = new SimpleDateFormat(TRACK_RECORD.DAILY_SHORT_DATE_FORMAT);

	private static final String SYMBOL = "FTSEMIB";
	private IData historical;
	private long clockFrequence = 1000;
	private long bidAskSpread = DUtils.d2l(5.0);

	private final AtomicBoolean isStarted = new AtomicBoolean(true);
	private final AtomicBoolean isRunning = new AtomicBoolean(false);

	private IMsg msg = Execution.Manager.msg();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public boolean init(Config config) throws Exception {
		historical = restorePriceData(new File("C:/Users/user/Desktop/test/FTSEMIB_1M.txt"));
		historical = historical.changeCompression(IData.COMPRESSION_UNIT.MINUTE*10);
		Asset.Future ftsemib = new Asset.Future(
				SYMBOL,
				"IT00002344",
				"FTSE-MIB",
				5.0,
				DUtils.d2l(.05),
				1,
				"IDEM",
				dailyFormat.parse("31/12/2015").getTime(),
				DUtils.d2l(1.),
				"^FTSEMIB");

		msg.async(Topics.INSTRUMENT.topic, ftsemib);

		new Thread(new Runnable() {
			private final AtomicInteger idx = new AtomicInteger(0);
			@Override
			public void run() {
				while(isStarted.get()){
					if(isRunning.get()){
						Bar b = historical.get(idx.getAndIncrement());
						System.out.println("DataProvider.init(...).new Runnable() {...}.run() "+b);
						Book.Orders bid = new Book.Orders(b.symbol, b.time, Side.BID, 1, b.close-bidAskSpread/2, 100);
						Book.Orders ask = new Book.Orders(b.symbol, b.time, Side.ASK, 1, b.close+bidAskSpread/2, 100);
						msg.async(Topics.ORDERS_BOOK_PREFIX.topic+b.symbol, bid);
						msg.async(Topics.ORDERS_BOOK_PREFIX.topic+b.symbol, ask);
						msg.async(Topics.BAR.topic, b);

						msg.async(Topics.STRATEGY_EVENT.topic, Event.Factory.create(b.symbol, Event.Type.OnBarClose, b));
					}
					try {
						Thread.sleep(clockFrequence);
					} catch (InterruptedException e) {}
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
