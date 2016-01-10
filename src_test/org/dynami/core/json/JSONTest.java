package org.dynami.core.json;

import java.io.File;

import org.dynami.runtime.config.StrategySettings;
import org.dynami.runtime.json.JSON;

public class JSONTest {

	public static void main(String[] args) {
		try {
			File file = new File("C:/Users/user/Desktop/Sample.dynami");
			StrategySettings settings = JSON.Parser.deserialize(file, StrategySettings.class);
			System.out.println(settings.getStages());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
