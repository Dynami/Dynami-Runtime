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

import org.dynami.runtime.IExecutionManager;
import org.dynami.runtime.IServiceBus.ServiceStatus;
import org.dynami.runtime.bus.Msg;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.moke.DataProvider;
import org.dynami.runtime.topics.Topics;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class Starter {
	private final IExecutionManager exec = Execution.Manager;
	private final AtomicBoolean started = new AtomicBoolean(false);
	
	public Starter() {
		Msg.Broker.subscribe(Topics.SERVICE_STATUS.topic, (last, msg)->{
			ServiceStatus s = (ServiceStatus)msg;
			System.out.println(s);
		});
		
		Msg.Broker.subscribe(Topics.ERRORS.topic, (last, msg)->{
			Throwable e = (Throwable)msg;
			e.printStackTrace();
		});
	}
	
	public static void main(String[] args) {
		try {
			new Starter().execute(new String[]{
					"-file", "C:/Users/user/Desktop/test/strategy/org.sample.strategy.dynami", 
					"-strategy_lib", "C:/Users/user/Desktop/test/strategy"});			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void execute(String args[]) throws Exception {
		Args arguments = new Args();
		new JCommander(arguments, args);
		
		exec.getServiceBus().registerDefaultServices();
		exec.getServiceBus().registerService(new DataProvider(), 100);
		
		if(exec.select(arguments.instanceFilePath, arguments.strategyLibPath)){
			if(exec.init(null)){
				System.out.println("Use the following commands to handle strategy execution:");
				System.out.println(Commands.LOAD+"\tto load");
				System.out.println(Commands.RUN+"\tto start");
				System.out.println(Commands.PAUSE+"\tto pause");
				System.out.println(Commands.RESUME+"\tto resume from pause");
				System.out.println(Commands.STOP+"\tto stop");
				System.out.println(Commands.EXIT+"\tto shutdown Dynami");
				
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
					Thread.sleep(0, 500);
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
				boolean executed = exec.load();
				System.err.println(Commands.START_RESPONSE+cmd+"_"+((executed)?Commands.RESPONSE_EXECUTED:Commands.RESPONSE_NOT_EXECUTED)+Commands.END_RESPONSE);
			} catch (Exception e) {
				System.err.println(Commands.START_RESPONSE+cmd+"_"+Commands.RESPONSE_NOT_EXECUTED+Commands.END_RESPONSE);
			}
			
			break;
		case Commands.RUN:
			try {
				boolean executed = exec.run();
				started.set(executed);
				System.err.println(Commands.START_RESPONSE+cmd+"_"+((executed)?Commands.RESPONSE_EXECUTED:Commands.RESPONSE_NOT_EXECUTED)+Commands.END_RESPONSE);
			} catch (Exception e) {
				System.err.println(Commands.START_RESPONSE+cmd+"_"+Commands.RESPONSE_NOT_EXECUTED+Commands.END_RESPONSE);
			} 
			break;
		case Commands.PAUSE:
			try {
				boolean executed = exec.pause();
				System.err.println(Commands.START_RESPONSE+cmd+"_"+((executed)?Commands.RESPONSE_EXECUTED:Commands.RESPONSE_NOT_EXECUTED)+Commands.END_RESPONSE);
			} catch (Exception e) {
				System.err.println(Commands.START_RESPONSE+cmd+"_"+Commands.RESPONSE_NOT_EXECUTED+Commands.END_RESPONSE);
			}
			
			break;
		case Commands.RESUME:
			try {
				boolean executed = exec.resume();
				System.err.println(Commands.START_RESPONSE+cmd+"_"+((executed)?Commands.RESPONSE_EXECUTED:Commands.RESPONSE_NOT_EXECUTED)+Commands.END_RESPONSE);
			} catch (Exception e) {
				System.err.println(Commands.START_RESPONSE+cmd+"_"+Commands.RESPONSE_NOT_EXECUTED+Commands.END_RESPONSE);
			}
			break;
		case Commands.STOP:
			try {
				boolean executed = exec.stop();
				System.err.println(Commands.START_RESPONSE+cmd+"_"+((executed)?Commands.RESPONSE_EXECUTED:Commands.RESPONSE_NOT_EXECUTED)+Commands.END_RESPONSE);
			} catch (Exception e) {
				System.err.println(Commands.START_RESPONSE+cmd+"_"+Commands.RESPONSE_NOT_EXECUTED+Commands.END_RESPONSE);
			}
			break;
		case Commands.EXIT:
			if(exec.isRunning()){
				try {
					boolean executed = exec.stop();
					
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
		public static final String RESUME = "X";
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
