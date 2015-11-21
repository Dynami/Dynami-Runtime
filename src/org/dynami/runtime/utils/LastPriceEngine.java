package org.dynami.runtime.utils;

import java.util.function.BiFunction;

import org.dynami.core.assets.Book;

public class LastPriceEngine {
	
	public static final BiFunction<Book.Orders, Book.Orders, Double> MidPrice = new BiFunction<Book.Orders, Book.Orders, Double>(){
		
		public Double apply(Book.Orders bid, Book.Orders ask) {
			if(bid != null && ask != null){
				return (bid.price+ask.price)/2;
			}
			return 0.;
		};
	};

}
