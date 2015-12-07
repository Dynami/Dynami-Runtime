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
package org.dynami.runtime.utils;

import org.dynami.core.assets.Asset;
import org.dynami.core.assets.Asset.Tradable;
import org.dynami.core.assets.Asset.Option.Type;
import org.dynami.core.assets.Greeks;
import org.dynami.core.data.IPricingEngine;
import org.dynami.core.utils.DTime;
import org.dynami.core.utils.DUtils;
import org.dynami.runtime.impl.Execution;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.Target;

public class JQuantLibUtils {
	
	public static final class OptionGreeks {
		private final VanillaOption option;
		private final Handle<YieldTermStructure> flatDividendTS;
		private final Handle<YieldTermStructure> flatTermStructure;
		private final Handle<BlackVolTermStructure> flatVolTS;
		private final BlackScholesMertonProcess bsmProcess;
		//private Handle<Quote> simSpotPriceH = new Handle<Quote>(new SimpleQuote(0.0));
		private final Exercise exercise ;
		private final PricingEngine pricingEngine;

		public OptionGreeks (
				final Date settlement, 
				final Date expire, 
				final Type type, 
				final double strike, 
				final Handle<Quote> lastH, 
				final Handle<Quote> interestRateH,
				final Handle<Quote> dividendYeldH,
				final Handle<Quote> volaH) {
			
			org.jquantlib.time.Calendar calendar = new Target();
			DayCounter dayCounter = new Actual365Fixed();
			
			flatDividendTS = new Handle<YieldTermStructure>(new FlatForward(settlement, dividendYeldH, dayCounter));
			flatTermStructure = new Handle<YieldTermStructure>(new FlatForward(settlement, interestRateH, dayCounter));
			flatVolTS = new Handle<BlackVolTermStructure>(new BlackConstantVol(settlement, calendar, volaH, dayCounter));
			bsmProcess = new BlackScholesMertonProcess(lastH, flatDividendTS, flatTermStructure, flatVolTS);
			
			exercise = new EuropeanExercise(expire);
			pricingEngine = new AnalyticEuropeanEngine(bsmProcess);
			
			final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type.equals(Asset.Option.Type.CALL)?org.jquantlib.instruments.Option.Type.Call:org.jquantlib.instruments.Option.Type.Put, strike);
			option = new VanillaOption(payoff, exercise);
			option.setPricingEngine(pricingEngine);
		}
		
		public double delta() {
			return option.delta();
		}

		public double gamma() {
			return option.gamma();
		}

		public double vega() {
			return option.vega();
		}

		public double theta() {
			return option.thetaPerDay();
		}

		public double rho() {
			return option.rho();
		}

//		public double elasticity() {
//			return option.elasticity();
//		}
//		
		public double theoreticalPrice() {
			return option.NPV();
		}
	}
	
	public static final IPricingEngine pricingEngine = new IPricingEngine(){
		@Override
		public double compute(Tradable tradable, long time, double price, double vola, double riskfreeRate) {
			if(tradable instanceof Asset.Option){
				final Asset.Option opt = (Asset.Option)tradable;
				final PlainVanillaPayoff payoff = new PlainVanillaPayoff(opt.type.equals(Asset.Option.Type.CALL)?org.jquantlib.instruments.Option.Type.Call:org.jquantlib.instruments.Option.Type.Put, opt.strike);
				final PricingEngine engine ;
				return 0;
			} else {
				return price;
			}
		}
	};
	
	public static final class GreeksEngine implements Greeks.Engine {
		private Date settlement = null;
		private OptionGreeks optionGreeks = null; 
		private final Handle<Quote> volaH = new Handle<Quote>(new SimpleQuote());
		private final Handle<Quote> dividendYeldH = new Handle<Quote>(new SimpleQuote(0.0));
		private final Handle<Quote> interestRateH = new Handle<Quote>(new SimpleQuote(0.0));
		private final Handle<Quote> lastH = new Handle<Quote>(new SimpleQuote());
		
		@Override
		public void evaluate(Greeks output, String underlyingSymbol, long time, Type type, long expire, double strike, double vola, double interestRate) {
			if(optionGreeks == null){
				settlement = new Date(new java.util.Date(DTime.Clock.getTime()));
				Date expirationDate = new Date(new java.util.Date(expire));
				expirationDate.addAssign(1);
				optionGreeks = new OptionGreeks(settlement, expirationDate, type, strike, lastH, interestRateH, dividendYeldH, volaH);
			}
			
			long currentSettlement = settlement.isoDate().getTime();
			long currentTime = DTime.Clock.getTime();
			int daysLeft = (int)((currentTime-currentSettlement)/DUtils.DAY_MILLIS);
			if(daysLeft > 0){
				settlement.addAssign(daysLeft);
			}
			new org.jquantlib.Settings().setEvaluationDate(settlement);
			if(vola > 0){
				double underlyingPrice = Execution.Manager.dynami().assets().getBySymbol(underlyingSymbol).asTradable().lastPrice();
				
				((SimpleQuote)volaH.currentLink()).setValue(vola);
				((SimpleQuote)interestRateH.currentLink()).setValue(interestRate);
				((SimpleQuote)lastH.currentLink()).setValue(underlyingPrice);
				
				output.setGreeks(optionGreeks.delta(), optionGreeks.gamma(), optionGreeks.vega(), optionGreeks.theta(), optionGreeks.rho(), optionGreeks.theoreticalPrice());
			}
		}
	}
}
