package org.dynami.runtime.utils;

import org.dynami.core.assets.Asset;
import org.dynami.core.utils.DUtils;
import org.dynami.runtime.utils.JQuantLibUtils.OptionGreeks;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.time.Date;

public class JQuantlibTest {
	public static void main(String[] args) {
		try {
			
			final Handle<Quote> volaH = new Handle<Quote>(new SimpleQuote());
			final Handle<Quote> dividendYeldH = new Handle<Quote>(new SimpleQuote(0.0));
			final Handle<Quote> interestRateH = new Handle<Quote>(new SimpleQuote(0.012));
			final Handle<Quote> lastH = new Handle<Quote>(new SimpleQuote());
			
			Date expire = new Date(DUtils.DATE_FORMAT.parse("15-04-2014"));
			Date settlement = new Date(DUtils.DATE_FORMAT.parse("26-03-2014"));
//			new org.jquantlib.Settings().setEvaluationDate(settlement);
			JQuantLibUtils.OptionGreeks greeks = new OptionGreeks(settlement, expire, Asset.Option.Type.PUT, 20_500, lastH, interestRateH, dividendYeldH, volaH);
			
			((SimpleQuote)volaH.currentLink()).setValue(.26);
			((SimpleQuote)lastH.currentLink()).setValue(20_500);
			
			System.out.printf("Delta\t:%2.3f", greeks.delta());
			
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
