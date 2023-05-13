package org.dynami.runtime.data;

import org.dynami.core.data.Bar;

public class RangeBarBuilder {
    private double tickRange;
    private String symbol;
    private boolean percent;
    public RangeBarBuilder(String symbol, double tickRange, boolean percent){
        this.tickRange = tickRange;
        this.symbol = symbol;
        this.percent = percent;
    }


    double open, high, low, close, range = 0.0;

    long volume, openInterest, time;

    public Bar push(final long time, final double price){
        if(range == 0.0){
            open = high = low = close = price;
            range = (this.percent)?price*tickRange : this.tickRange;
            return null;
        } else {
            high = Math.max(high, price);
            low = Math.min(low, price);
            if(high - low > range){
                close = price;
                Bar bar = new Bar(symbol, open, high, low, close, 0, time);
                open = high = low = close = price;
                range = (this.percent)?price*tickRange : this.tickRange;
                return bar;
            } else {
                close = price;
                return null;
            }
        }
    }
}
