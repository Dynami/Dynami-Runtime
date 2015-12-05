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
import org.dynami.core.assets.Asset.Option.Type;
import org.dynami.core.assets.Greeks;
import org.dynami.core.utils.DUtils;
import org.dynami.runtime.impl.Execution;

public class BSEurOptionsUtils {
	private static final double ACCURACY = 0.0001;
	
	public static Greeks.ImpliedVolatility implVola = new Greeks.ImpliedVolatility() {
		
		@Override
		public double estimate(String underlyingSymbol, long time, Type type, long expire, double strike, double optionPrice, double riskFreeRate) {
			final Asset.Tradable asset = (Asset.Tradable)Execution.Manager.dynami().assets().getBySymbol(underlyingSymbol);
			final double underPrice = asset.lastPrice();
			final double days = (expire-time)/(double)DUtils.DAY_MILLIS;
			
			return impliedVolatility(type, underPrice, strike, days, riskFreeRate, optionPrice);
		}
	};
	
	public static Greeks.Engine greeksEngine = new Greeks.Engine() {

		public void evaluate(Greeks output, String underlyingSymbol, long time, Type type, long expire, double strike, double vola, double interestRate) {
			final Asset.Tradable asset = Execution.Manager.dynami().assets().getBySymbol(underlyingSymbol).asTradable();
			final double underPrice = asset.lastPrice();
			
			final double maturity = ((expire-time)/DUtils.DAY_MILLIS)/365.;
			final double delta = EuropeanBlackScholes.delta(type, underPrice, strike, vola, maturity, interestRate);
			final double gamma = EuropeanBlackScholes.gamma(type, underPrice, strike, vola, maturity, interestRate);
			final double theta = EuropeanBlackScholes.theta(type, underPrice, strike, vola, maturity, interestRate);
			final double vega = EuropeanBlackScholes.vega(type, underPrice, strike, vola, maturity, interestRate);
			final double rho = EuropeanBlackScholes.rho(type, underPrice, strike, vola, maturity, interestRate);
			
			output.setGreeks(delta, gamma, vega, theta, rho, 0.);
		}
	};

	
	private static double callEurPrice(final double underlyingPrice, final double strike, final double days, final double riskFreeRate, final double impliedVolatility){
	    double maturity = days/365;
		double a = 0, b = 0, c = 0, d1 = 0, d2 = 0;
	    a = Math.log(underlyingPrice / strike);
	    b = (riskFreeRate + 0.5 * Math.pow(impliedVolatility,2)) * maturity;
	    c = impliedVolatility * Math.pow(maturity , 0.5);
	    d1 = (a + b) / c;
	    d2 = d1 - impliedVolatility * Math.pow(maturity , 0.5);
	    return  underlyingPrice * standardNorm(d1) - strike * Math.exp(-riskFreeRate * maturity) * standardNorm(d2);
	}
	
	private static double putEurPrice(final double price, final double strike, double days, final double riskFreeRate, final double impliedVolatility){
	    days = days/365;
		double a = 0, b = 0, c = 0, d1 = 0, d2 = 0;
		
		a = Math.log(price / strike);
	    b = (riskFreeRate + 0.5 * Math.pow(impliedVolatility , 2)) * days;
	    c = impliedVolatility * Math.pow(days , 0.5);
	    d1 = (a + b) / c;
	    d2 = d1 - impliedVolatility * Math.pow(days , 0.5);
	    double callPrice = price * standardNorm(d1) - strike * Math.exp(-riskFreeRate * days) * standardNorm(d2);
	    return strike * Math.exp(-riskFreeRate * days) - price + callPrice;
	}
	
	public static double impliedVolatility(Asset.Option.Type type, final double underlyingPrice, final double strike, double days, final double riskFreeRate, final double optionPrice){
		if(Asset.Option.Type.CALL.equals(type)){
			return callEurIV(underlyingPrice, strike, days, riskFreeRate, optionPrice);
		} else {
			return putEurIV(underlyingPrice, strike, days, riskFreeRate, optionPrice);
		}
	}
	
	private static double callEurIV(final double underlyingPrice, final double strike,  double days, final double riskFreeRate, final double optionPrice){
		double High = 1;
		double Low = 0;
		double prezzoTarget = 0;
		double result = 0;
		if(optionPrice > 0){
			prezzoTarget = optionPrice;
		}
		if(prezzoTarget > 0){
		    double bs = 0;
		    while ((High - Low) > ACCURACY){
			    bs = callEurPrice(underlyingPrice, strike, days, riskFreeRate, ((High + Low) / 2));
			    if(bs > prezzoTarget){
			    	High = (High + Low) / 2;
			    } else {
			    	Low = (High + Low) / 2;
			    }
		    }
		    result = (High + Low) / 2;
		} else {
		    result = 0.0000001;
		}
		return result;
	}
	
	
	private static double putEurIV(final double price, final double strike, double days, final double riskFreeRate, final double last){
		double High = 1;
		double Low = 0;
		double prezzoTarget = 0;
		double result = 0;
		if(last > 0){
			prezzoTarget = last;
		}
		if(prezzoTarget > 0){
			double bs = 0;
			while ((High - Low) > ACCURACY){
				bs = putEurPrice(price, strike, days, riskFreeRate, ((High + Low) / 2));
				if(bs > prezzoTarget){
					High = (High + Low) / 2;
				} else {
					Low = (High + Low) / 2;
				}
			}
			result = (High + Low) / 2;
		} else { 
			result = 0.0000001;
		}
		return result;
	}
	
	private static double standardNorm(double z){
	    double c1 = 2.506628;
	    double c2 = 0.3193815;
	    double c3 = -0.3565638;
	    double c4 = 1.7814779;
	    double c5 = -1.821256;
	    double c6 = 1.3302744;
	    double y = 0;
	    
	    double w = 0;
	    if(z > 0 || z == 0){
	    	w = 1;
	    } else {
	    	w = -1;
	    }
	    y = 1 / (1 + 0.2316419 * w * z);
	    return  (0.5 + w * (0.5 - (Math.exp(-z * z / 2) / c1) * (y * (c2 + y * (c3 + y * (c4 + y * (c5 + y * c6)))))));	           
	}
}
