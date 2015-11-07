package org.dynami.runtime.utils;

import org.dynami.core.assets.Asset;
import static java.lang.Math.PI;

public class EuropeanBlackScholes {
	private static MathNormalCDF mathNormalCDF= new MathNormalCDF();
	
	public static double price(Asset.Option.Type type, double stock, double strike, double volatility, double maturity, double interest){
//		volatility=volatility/100;
//		interest=interest/100;
//		maturity=maturity/365;

		double d1=(Math.log(stock/strike)+(interest+volatility*volatility/2)*maturity) / (volatility*Math.sqrt(maturity));
		double d2=d1-volatility*Math.sqrt(maturity);

		if (Asset.Option.Type.CALL.equals(type))
			return stock*mathNormalCDF.computeCDF(d1) - strike*Math.exp(-interest*maturity)* mathNormalCDF.computeCDF(d2);
	 	else
			return strike*Math.exp(-interest*maturity)* mathNormalCDF.computeCDF(-d2) -stock*mathNormalCDF.computeCDF(-d1) ;
	}
	
	public static double delta(Asset.Option.Type type, double stock,double strike, double volatility, double maturity, double interest){
//		volatility=volatility/100;
//		interest=interest/100;
//		maturity=maturity/365;

		double d1=(Math.log(stock/strike)+(interest+volatility*volatility/2)*maturity) / (volatility*Math.sqrt(maturity));
		if(Asset.Option.Type.CALL.equals(type)){
			return mathNormalCDF.computeCDF(d1) ;
		}else{
			return mathNormalCDF.computeCDF(d1) -1 ;
		}
	}
	
	public static double gamma(Asset.Option.Type type, double stock,double strike, double volatility, double maturity, double interest){
//		volatility=volatility/100;
//		interest=interest/100;
//		maturity=maturity/365;

		double d1=(Math.log(stock/strike)+(interest+volatility*volatility/2)*maturity) / (volatility*Math.sqrt(maturity));

		return (mathNormalCDF.computeCDF(d1))/(stock*volatility*Math.sqrt(maturity)) ;
	}

	/**
	Theta measures the calculated option value's sensitivity to small changes in time till maturity.
	*/
	public static double theta(Asset.Option.Type type, double stock,double strike, double volatility, double maturity, double interest){
//		volatility=volatility/100;
//		interest=interest/100;
//		maturity=maturity/365;

		double d1=(Math.log(stock/strike)+(interest+volatility*volatility/2)*maturity) / (volatility*Math.sqrt(maturity));
		double d2=d1-volatility*Math.sqrt(maturity);

		if(Asset.Option.Type.CALL.equals(type)){
			return (-stock*volatility*mathNormalCDF.computeCDF(d1))/(2*Math.sqrt(maturity))
					-interest*strike*Math.exp(-interest*maturity)* mathNormalCDF.computeCDF(d2);
		}else{
			return (-stock*volatility*mathNormalCDF.computeCDF(d1))/(2*Math.sqrt(maturity))
					+interest*strike*Math.exp(-interest*maturity)* mathNormalCDF.computeCDF(-d2);
		}
	}

	/**
	Vega measures the calculated option value's sensitivity to small changes in volatility.
	*/
	public static double vega(Asset.Option.Type type,double stock,double strike, double volatility, double maturity, double interest){
//		volatility=volatility/100;
//		interest=interest/100;
//		maturity=maturity/365;

		double d1=(Math.log(stock/strike)+(interest+volatility*volatility/2)*maturity) / (volatility*Math.sqrt(maturity));

		return stock*Math.sqrt(maturity)*mathNormalCDF.computeCDF(d1);
	}


	/**
	Rho: The partial with respect to the interest rate.
	*/
	public static double rho(Asset.Option.Type type ,double stock,double strike, double volatility, double maturity, double interest){
//		volatility=volatility/100;
//		interest=interest/100;
//		maturity=maturity/365;

		double d1=(Math.log(stock/strike)+(interest+volatility*volatility/2)*maturity) / (volatility*Math.sqrt(maturity));
		double d2=d1-volatility*Math.sqrt(maturity);

		if(Asset.Option.Type.CALL.equals(type)){
			return strike*maturity*Math.exp(-interest*maturity)*mathNormalCDF.computeCDF(d2) ;
		}else{
			return -strike*maturity*Math.exp(-interest*maturity)*mathNormalCDF.computeCDF(-d2) ;
		}
	}
}

class MathNormalCDF {
	double mean;
	double stdev;
	public MathNormalCDF(){
		mean=0;
		stdev=1;
	}
	
	MathNormalCDF(double mu,double sigma) {
		if(sigma<=0){
		}
		mean=mu;
		stdev=sigma;
	}
	
	double computeCDF(double x){
		x=(x-mean)/stdev;

		double L, K, w ;
		final double a1 = 0.31938153, a2 = -0.356563782, a3 = 1.781477937;
	 	final double a4 = -1.821255978, a5 = 1.330274429;

		L = Math.abs(x);
		K = 1.0 / (1.0 + 0.2316419 * L);
		w = 1.0 - 1.0 / Math.sqrt(2 * PI) * Math.exp(-L *L / 2) * (a1 * K + a2 * K *K + a3 * Math.pow(K,3) + a4 * Math.pow(K,4) + a5 * Math.pow(K,5));

		if (x < 0 ){
	 		w= 1.0 - w;
	 	}
		 return w;
	}
};

