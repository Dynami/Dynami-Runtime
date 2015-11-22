package org.dynami.core.json;

import org.dynami.core.config.Config;

public class ConfigTransformerTest {
	
	public static void main(String[] args) {
		try {
//			final JSONSerializer serializer = new JSONSerializer();
//			serializer.transform(new ConfigTransformer(), TestConfig.class);
//			
//			TestConfig conf = new TestConfig();
//			conf.autoClose=true;
//			conf.deltaThreshold=.34;
//			conf.fastPeriod = 24;
//			conf.longPeriod = 2345L;
//			conf.name = "Sample config";
//			conf.ignore = "Ignore this";
//			
//			String out = serializer.deepSerialize(conf);
//			System.out.println(out);
//			
//			final JSONDeserializer<TestConfig> deserializer = new JSONDeserializer<>();
//			
//			deserializer.use(TestConfig.class, new ConfigTransformer());
//			TestConfig conf2 = new TestConfig();
//			conf2 = deserializer.deserialize(out, TestConfig.class);
//			System.out.println(conf2.toString());
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	@Config.Settings(name="TestConfiguration", description="Self test config")
	public static class TestConfig {
		@Config.Param
		private String name;
		
		@Config.Param
		private int fastPeriod;
		
		@Config.Param
		private long longPeriod;
		
		@Config.Param
		private double deltaThreshold;
		
		@Config.Param
		private boolean autoClose;
		
		private String ignore;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public int getFastPeriod() {
			return fastPeriod;
		}

		public void setFastPeriod(int fastPeriod) {
			this.fastPeriod = fastPeriod;
		}

		public long getLongPeriod() {
			return longPeriod;
		}

		public void setLongPeriod(long longPeriod) {
			this.longPeriod = longPeriod;
		}

		public double getDeltaThreshold() {
			return deltaThreshold;
		}

		public void setDeltaThreshold(double deltaThreshold) {
			this.deltaThreshold = deltaThreshold;
		}

		public boolean isAutoClose() {
			return autoClose;
		}

		public void setAutoClose(boolean autoClose) {
			this.autoClose = autoClose;
		}
		
		public String getIgnore() {
			return ignore;
		}
		
		public void setIgnore(String ignore) {
			this.ignore = ignore;
		}

		@Override
		public String toString() {
			return "TestConfig [name=" + name + ", fastPeriod=" + fastPeriod + ", longPeriod=" + longPeriod
					+ ", deltaThreshold=" + deltaThreshold + ", autoClose=" + autoClose + ", ignore=" + ignore + "]";
		}
		
	}
}
