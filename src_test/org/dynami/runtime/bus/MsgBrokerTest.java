package org.dynami.runtime.bus;

import java.util.concurrent.atomic.AtomicLong;

public class MsgBrokerTest {

	public static void main(String[] args) {
		try {
			final int A = 1;
			final int B = 3;
			final int C = 4;
			final int D = 8;
			final int E = 16;

			int c = A|B;
			//System.out.println(c);
//			System.out.println(c^A);
//			System.out.println(c^B);
//			System.out.println(c^C);
//			System.out.println(c^D);
//			System.out.println(c^E);

			boolean isA = c > (c^A);
			boolean isB = c > (c^B);
			boolean isC = c > (c^C);
			boolean isD = c > (c^D);
			boolean isE = c > (c^E);
			System.out.println("A "+isA);
			System.out.println("B "+isB);
			System.out.println("C "+isC);
			System.out.println("D "+isD);
			System.out.println("E "+isE);
			//MsgBrokerTest.testMsg1();
			//MsgBrokerTest.testMsg2();
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}

	}

	public static void testMsg1() throws Exception {
		System.out.println(1024);
		final long ITERATIONS = 1000L;
		final String TOPIC = "p";
		final AtomicLong counter = new AtomicLong(0);
		Msg.Broker.subscribe(TOPIC, (last, msg)->{
			counter.set((long)msg);
//			if(last)
//				System.out.println(msg);
			});

		long start_sending = System.nanoTime();
		for(long i = 0; i < ITERATIONS; i++){
			Msg.Broker.async(TOPIC, i);
		}
		long end_sending = System.nanoTime();
		System.out.println("Msg1 "+ITERATIONS+" messages sent in "+(end_sending-start_sending)+" ns");

		// wait until all messages are consumed
		while(ITERATIONS > counter.get()+1){}

		long end_consuming = System.nanoTime();
		System.out.println("Msg1 "+ITERATIONS+" messages consumed in "+(end_consuming-start_sending)+" ns");
		Msg.Broker.dispose();
	}

	public static void testMsg2() throws Exception {
		final long ITERATIONS = 20L;
		final String TOPIC = "p";
		final AtomicLong counter = new AtomicLong(0);
		final AtomicLong _counter_ = new AtomicLong(0);
		Msg.Broker.subscribe(TOPIC, (last, msg)->{
			counter.set((long)msg);
			_counter_.incrementAndGet();
			if(last)
				System.out.println(msg);
			});

		long start_sending = System.nanoTime();
		for(long i = 0; i < ITERATIONS; i++){
			Msg.Broker.async(TOPIC, i);
			Thread.sleep(1);
		}

		long end_sending = System.nanoTime();
		System.out.println("Msg2 "+ITERATIONS+" messages sent in "+(end_sending-start_sending)+" ns");

		// wait until all messages are consumed
		while(ITERATIONS > counter.get()+1){}

		System.out.println("Msg2 Processed "+_counter_.get());
		long end_consuming = System.nanoTime();
		System.out.println("Msg2 "+ITERATIONS+" messages consumed in "+(end_consuming-start_sending)+" ns");
		Msg.Broker.dispose();
	}
}
