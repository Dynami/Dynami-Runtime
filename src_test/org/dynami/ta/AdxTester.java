package org.dynami.ta;

import java.io.File;

import org.dynami.core.data.IData;
import org.dynami.runtime.data.BarData;
import org.dynami.ta.momentum.Adx;
import org.dynami.ta.overlap_studies.Sma;

public class AdxTester extends TATester {
	public static void main(String[] args) {
		try {
			IData data = loadData(new File("./resources/FTSEMIB_1M_2015_10_02.txt"), "FTSEMIB");
			
			Adx adx = new Adx(14);
			Sma sma = new Sma(14);
			BarData _data = new BarData();
			for(int i = 0; i < 40; i++){
				_data.append(data.get(i));
				
//				sma.compute(_data.close());
//				sma.get().forEach(System.out::println);
				adx.reset();
				adx.compute(_data.high(), _data.low(), _data.close());
				adx.get().forEach(System.out::println);
				System.out.println("############");
			}
			
//			Adx adx2 = new Adx(14);
//			adx2.compute(_data.high(), _data.low(), _data.close());
//			adx2.get().forEach(System.out::println);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
