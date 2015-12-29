package org.dynami.runtime.utils;

import org.dynami.core.assets.Asset;

public class EuropeanBlackScholesTest {
	public static void main(String[] args) {
		double spot = 21976.84;
		double maturity = 18./365.;
		double strike = 22_000;
		double volatility = 0.1555613794346786;
		double callPrice = EuropeanBlackScholes.price(Asset.Option.Type.CALL, spot, strike, volatility, maturity, 0.);
		double putPrice = EuropeanBlackScholes.price(Asset.Option.Type.PUT, spot, strike, volatility, maturity, 0.);
		System.out.println("Call\t\tPut");
		System.out.printf("%4.3f\t\t%4.3f\n", callPrice, putPrice);
	}
}
