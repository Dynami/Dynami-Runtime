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
package org.dynami.runtime.exec;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

import org.dynami.core.portfolio.ClosedPosition;
import org.dynami.core.portfolio.OpenPosition;
import org.dynami.core.services.IPortfolioService;
import org.dynami.runtime.IServiceBus.ServiceStatus;
import org.dynami.runtime.handlers.TextFileDataHandler;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.topics.Topics;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class Starter {
	private final AtomicBoolean started = new AtomicBoolean(false);

	public Starter() {
		Execution.Manager.msg().subscribe(Topics.SERVICE_STATUS.topic, (last, _msg)->{
			ServiceStatus s = (ServiceStatus)_msg;
			System.out.println(s);
		});

		Execution.Manager.msg().subscribe(Topics.INTERNAL_ERRORS.topic, (last, _msg)->{
			Throwable e = (Throwable)_msg;
			e.printStackTrace();
		});
	}

	public static void main(String[] args) {
		try {
			new Starter().execute(new String[]{
					// set proper path, but let empty file for the moment
					"-file", "../Dynami-Sample-Strategy/resources/myPersonalSettings.dynami",
					// set proper path
					"-strategy_lib", "../Dynami-UI/resources/Dynami-Sample-Strategy-0.0.1.jar"});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void execute(String args[]) throws Exception {
		Args arguments = new Args();
		new JCommander(arguments, args);

		Execution.Manager.getServiceBus().registerDefaultServices();
		Execution.Manager.getServiceBus().registerService(new TextFileDataHandler(), 100);

		if(Execution.Manager.select(arguments.instanceFilePath, arguments.strategyLibPath)){
			if(Execution.Manager.init(null)){
				System.out.println("Use the following commands to handle strategy execution:");
				System.out.println(Commands.LOAD+"+Enter\t\tto load");
				System.out.println(Commands.RUN+"+Enter\t\tto run/resume");
				System.out.println(Commands.PAUSE+"+Enter\t\tto pause");
				System.out.println(Commands.STOP+"+Enter\t\tto stop");
				System.out.println(Commands.EXIT+"+Enter\t\tto shutdown Dynami");
				System.out.println(Commands.PRINT_STATUS+"+Enter\t\tto print strategy status");
				System.out.println(Commands.PRINT_DEEP_STATUS+"+Enter\tto print deep strategy status (with closed positions)");
				listener.start();
			} else {
				System.out.println("Something wrong append on initializing Dynami");
			}
		} else {
			System.out.println("Something wrong append on selecting strategy");
		}
	}

	private final Thread listener = new Thread(new Runnable() {
		final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		@Override
		public void run() {
			try {
				while(true) {
					if (reader.ready()) {
						parseCommand(reader.readLine());
					}
					Thread.sleep(10);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}, "Dynami-CommandListener");

	private void parseCommand(final String cmd) {
		switch (cmd) {
		case Commands.LOAD:
			try {
				boolean executed = Execution.Manager.load();
				System.err.println(Commands.START_RESPONSE+cmd+"_"+((executed)?Commands.RESPONSE_EXECUTED:Commands.RESPONSE_NOT_EXECUTED)+Commands.END_RESPONSE);
			} catch (Exception e) {
				System.err.println(Commands.START_RESPONSE+cmd+"_"+Commands.RESPONSE_NOT_EXECUTED+Commands.END_RESPONSE);
			}

			break;
		case Commands.RUN:
			try {
				boolean executed = Execution.Manager.run();
				started.set(executed);
				System.err.println(Commands.START_RESPONSE+cmd+"_"+((executed)?Commands.RESPONSE_EXECUTED:Commands.RESPONSE_NOT_EXECUTED)+Commands.END_RESPONSE);
			} catch (Exception e) {
				System.err.println(Commands.START_RESPONSE+cmd+"_"+Commands.RESPONSE_NOT_EXECUTED+Commands.END_RESPONSE);
			}
			break;
		case Commands.PAUSE:
			try {
				boolean executed = Execution.Manager.pause();
				System.err.println(Commands.START_RESPONSE+cmd+"_"+((executed)?Commands.RESPONSE_EXECUTED:Commands.RESPONSE_NOT_EXECUTED)+Commands.END_RESPONSE);
			} catch (Exception e) {
				System.err.println(Commands.START_RESPONSE+cmd+"_"+Commands.RESPONSE_NOT_EXECUTED+Commands.END_RESPONSE);
			}

			break;
		case Commands.PRINT_STATUS:
			try {
				//boolean executed = !Execution.Manager.isRunning();
				IPortfolioService portfolio = Execution.Manager.dynami().portfolio();
				System.out.println("Open positions:");
				for(OpenPosition p:portfolio.getOpenPositions()){
					System.out.println(p);
					System.out.println("-------------------------------------");
				}
				System.out.println();

				System.out.printf("Budget    : %6.2f\n", portfolio.getCurrentBudget());
				System.out.printf("Realized  : %6.2f\n", portfolio.realised());
				System.out.printf("Unrealized: %6.2f\n", portfolio.unrealised());
				//System.err.println(Commands.START_RESPONSE+cmd+"_"+((executed)?Commands.RESPONSE_EXECUTED:Commands.RESPONSE_NOT_EXECUTED)+Commands.END_RESPONSE);
			} catch (Exception e) {
				System.err.println(Commands.START_RESPONSE+cmd+"_"+Commands.RESPONSE_NOT_EXECUTED+Commands.END_RESPONSE);
			}
			break;
		case Commands.PRINT_DEEP_STATUS:
			try {
				//boolean executed = !Execution.Manager.isRunning();
				IPortfolioService portfolio = Execution.Manager.dynami().portfolio();
				System.out.println("Open positions:");
				for(OpenPosition p:portfolio.getOpenPositions()){
					System.out.println(p);
					System.out.println("-------------------------------------");
				}
				System.out.println();

				System.out.printf("Budget    : %6.2f\n", portfolio.getCurrentBudget());
				System.out.printf("Realized  : %6.2f\n", portfolio.realised());
				System.out.printf("Unrealized: %6.2f\n", portfolio.unrealised());
				System.out.println();
				for(ClosedPosition cp :portfolio.getClosedPositions()){
					System.out.println(cp);
				}
				//System.err.println(Commands.START_RESPONSE+cmd+"_"+((executed)?Commands.RESPONSE_EXECUTED:Commands.RESPONSE_NOT_EXECUTED)+Commands.END_RESPONSE);
			} catch (Exception e) {
				System.err.println(Commands.START_RESPONSE+cmd+"_"+Commands.RESPONSE_NOT_EXECUTED+Commands.END_RESPONSE);
			}
			break;
		case Commands.STOP:
			try {
				boolean executed = Execution.Manager.stop();
				System.err.println(Commands.START_RESPONSE+cmd+"_"+((executed)?Commands.RESPONSE_EXECUTED:Commands.RESPONSE_NOT_EXECUTED)+Commands.END_RESPONSE);
			} catch (Exception e) {
				System.err.println(Commands.START_RESPONSE+cmd+"_"+Commands.RESPONSE_NOT_EXECUTED+Commands.END_RESPONSE);
			}
			break;
		case Commands.EXIT:
			if(Execution.Manager.isRunning()){
				try {
					boolean executed = Execution.Manager.stop();

					System.err.println(Commands.START_RESPONSE+cmd+"_"+((executed)?Commands.RESPONSE_EXECUTED:Commands.RESPONSE_NOT_EXECUTED)+Commands.END_RESPONSE);
				} catch (Exception e) {
					System.err.println(Commands.START_RESPONSE+cmd+"_"+Commands.RESPONSE_NOT_EXECUTED+Commands.END_RESPONSE);
				}
			}
			System.exit(-1);
			break;
		default:
			break;
		}
	}

	public static class Commands {
		public static final String LOAD = "L";
		public static final String RUN = "R";
		public static final String PAUSE = "P";
		public static final String PRINT_STATUS = "X";
		public static final String PRINT_DEEP_STATUS = "XX";
		public static final String STOP = "S";
		public static final String EXIT = "E";

		public static final String ORDER_PREFIX = "ORDER:";

		public static final String RESPONSE_EXECUTED = "OK";
		public static final String RESPONSE_NOT_EXECUTED = "KO";

		public static final char START_RESPONSE = 2;
		public static final char END_RESPONSE = 3;
	}

	public static class Args {
		@Parameter(names = "-file", required = true)
		String instanceFilePath;

		@Parameter(names = "-strategy_lib", required = true)
		String strategyLibPath;
	}
}
