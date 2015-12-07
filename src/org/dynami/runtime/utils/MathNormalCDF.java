package org.dynami.runtime.utils;

import static java.lang.Math.PI;

public class MathNormalCDF {
	double mean;
	double stdev;
	public MathNormalCDF(){
		mean=0;
		stdev=1;
	}
	
	public MathNormalCDF(double mu, double sigma) {
		if(sigma<=0){
			sigma = .000001;
		}
		mean=mu;
		stdev=sigma;
	}
	
	public double computeCDF(double x){
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