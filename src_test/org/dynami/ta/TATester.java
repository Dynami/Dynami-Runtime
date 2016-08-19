package org.dynami.ta;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Scanner;

import org.dynami.core.data.Bar;
import org.dynami.runtime.data.BarData;
import org.dynami.runtime.handlers.TextFileDataHandler.TRACK_RECORD;

public abstract class TATester {
	private static final SimpleDateFormat intradaySecondsFormat = new SimpleDateFormat(
			TRACK_RECORD.INTRADAY_SECONDS_DATE_FORMAT);
	private static final SimpleDateFormat intradayMinutesFormat = new SimpleDateFormat(
			TRACK_RECORD.INTRADAY_MINUTES_DATE_FORMAT);
	private static final SimpleDateFormat dailyFormat = new SimpleDateFormat(TRACK_RECORD.DAILY_DATE_FORMAT);
	private static final SimpleDateFormat dailyShortFormat = new SimpleDateFormat(TRACK_RECORD.DAILY_SHORT_DATE_FORMAT);
	public static BarData loadData(final File f, String symbol) throws Exception {
		SimpleDateFormat dateParser = null;
//		BufferedReader reader = null;
		Scanner scanner = new Scanner(f);
		try {
			String[] tmp = null;
//			reader = new BufferedReader(new FileReader(f));
			String line = null;
			boolean isFirst = true;
			BarData barData = new BarData();
			while (scanner.hasNextLine()) {
				line = scanner.nextLine();
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
			scanner.close();
//			if (reader != null)
//				reader.close();
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
}
