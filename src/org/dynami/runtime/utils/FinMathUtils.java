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
import org.dynami.core.assets.Asset.Option;
import org.dynami.core.assets.Asset.Option.Type;
import org.dynami.core.assets.Greeks;
import org.dynami.core.utils.DUtils;
import org.dynami.runtime.impl.Execution;

import net.finmath.functions.AnalyticFormulas;

public class FinMathUtils {
	
	
	public static Greeks.ImpliedVolatility implVola = new Greeks.ImpliedVolatility() {

		@Override
		public double estimate(String underlyingSymbol, long time, Type type, long expire, double strike, double optionPrice, double riskFreeRate) {
			final Asset.Tradable asset = Execution.Manager.dynami().assets().getBySymbol(underlyingSymbol).asTradable();
			final double underPrice = asset.lastPrice();
			
			final double maturity = (expire-time)/(double)DUtils.DAY_MILLIS;
			return AnalyticFormulas.blackScholesOptionImpliedVolatility(underPrice, maturity, riskFreeRate, optionPrice, strike);  
		}
		
	};
	
	public static Greeks.Engine greeksEngine = new Greeks.Engine() {

		@Override
		public void evaluate(Greeks output, String underlyingSymbol, long time, Type type, long expire, double strike, double price, double vola, double riskFreeRate) {
			final Asset.Tradable asset = Execution.Manager.dynami().assets().getBySymbol(underlyingSymbol).asTradable();
			final double underPrice = asset.lastPrice();
			
			final double maturity = (expire-time)/(double)DUtils.DAY_MILLIS;
			double delta = AnalyticFormulas.blackScholesOptionDelta(underPrice, riskFreeRate, vola, maturity, strike);
			double gamma = AnalyticFormulas.blackScholesOptionGamma(underPrice, riskFreeRate, vola, maturity, strike);
			double vega = AnalyticFormulas.blackScholesOptionVega(underPrice, riskFreeRate, vola, maturity, strike);
			double rho = AnalyticFormulas.blackScholesOptionRho(underPrice, riskFreeRate, vola, maturity, strike);
			double theta = 0; // AnalyticFormulas.blackScholesOptionTheta(underPrice, riskFreeRate, vola, maturity, strike)*(Option.Type.CALL.equals(type)?1:-1);
			output.setGreeks(delta, gamma, vega, theta, rho);
		}
	};

}
