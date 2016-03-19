/*
 * Copyright 2014 Alessandro Atria
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
package org.dynami.runtime.bus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.dynami.core.bus.IMsg;

/**
 * Message broker works as a topic/subscriber queue and allows to share messages,
 * in asynchronously and synchronously mode, between objects and threads.
 * @author Atria
 *
 */
public enum Msg_Threaded implements IMsg {
	Broker;

	public static final String WILDCARD = "*";
	private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	private final Map<String, List<HandlerWrapper>> subscriptions = new ConcurrentSkipListMap<>();
	private final Map<String, List<HandlerWrapper>> wildcardSubscriptions = new ConcurrentSkipListMap<>();
	private boolean forceSyncExecution = false;

	/* (non-Javadoc)
	 * @see org.dynami.core.bus.IMessageService#subscribe(java.lang.String, org.dynami.core.bus.Msg.Handler)
	 */
	@Override
	public void subscribe(String topic, IMsg.Handler handler){
		if(topic.endsWith(WILDCARD)){
			String wildcardTopic = topic.substring(0, topic.length()-1);
			if(!wildcardSubscriptions.containsKey(wildcardTopic)){
				wildcardSubscriptions.put(wildcardTopic, new CopyOnWriteArrayList<HandlerWrapper>());
			}
			wildcardSubscriptions.get(wildcardTopic).add(new HandlerWrapper(handler));
//			System.out.println("Msg.subscribe("+wildcardTopic+")");
		} else {
			if(!subscriptions.containsKey(topic)){
				subscriptions.put(topic, new CopyOnWriteArrayList<HandlerWrapper>());
			}
			subscriptions.get(topic).add(new HandlerWrapper(handler));
		}
	}

	/* (non-Javadoc)
	 * @see org.dynami.core.bus.IMessageService#unsubscribe(java.lang.String, org.dynami.core.bus.Msg.Handler)
	 */
	@Override
	public void unsubscribe(String topic, IMsg.Handler handler){
		if(!subscriptions.containsKey(topic)){
			return;
		}
		subscriptions.get(topic).remove(handler);
	}

	/* (non-Javadoc)
	 * @see org.dynami.core.bus.IMessageService#removeTopic(java.lang.String)
	 */
	@Override
	public void removeTopic(String topic){
		if(!subscriptions.containsKey(topic)){
			return;
		}
		subscriptions.remove(topic);
	}

	@Override
	public Set<String> getTopics(){
		return subscriptions.keySet();
	}

	/* (non-Javadoc)
	 * @see org.dynami.core.bus.IMessageService#async(java.lang.String, java.lang.Object)
	 */
	@Override
	public boolean async(final String topic, final Object msg){
		if(forceSyncExecution) return sync(topic, msg);

		final List<HandlerWrapper> wrappers = subscriptions.get(topic);
		if(wrappers != null && !executor.isShutdown()){
			MsgWrapper m = new MsgWrapper(msg);
			for(final HandlerWrapper wrapper : wrappers){
				executor.execute(()->{
					wrapper.handler.update((wrapper.last.getAndSet(m.time) < m.time), m.msg);
				});
			}
		}
		wildcardSubscriptions.keySet().stream().forEach((wildcardKey)->{
			if(topic.startsWith(wildcardKey)){
				final List<HandlerWrapper> wildcardWrappers = wildcardSubscriptions.get(wildcardKey);
				if(wildcardWrappers != null && !executor.isShutdown()){
					MsgWrapper m = new MsgWrapper(msg);
					for(final HandlerWrapper wrapper : wildcardWrappers){
						executor.execute(new Runnable() {
							@Override
							public void run() {
								wrapper.handler.update((wrapper.last.getAndSet(m.time) < m.time), m.msg);
							}
						});
					}
				}
			}
		});
		return true;
	}

	/**
	 * Sends asynchronous message to subscribers and execute the callback function when all subscribers processed message
	 * @param topic
	 * @param msg
	 * @param callback
	 * @return true if topic exist, false otherwise
	 */
	public boolean async(final String topic, final Object msg, final Runnable callback){
		if(forceSyncExecution){
			sync(topic, msg);
			callback.run();
			return true;
		}

		executor.submit(()->{
			final List<HandlerWrapper> wrappers = subscriptions.get(topic);
			List<Future<?>> results = new ArrayList<>();
			if(wrappers != null && !executor.isShutdown()){
				MsgWrapper m = new MsgWrapper(msg);
				for(final HandlerWrapper wrapper : wrappers){
					results.add(executor.submit(new Runnable() {
						@Override
						public void run() {
							wrapper.handler.update((wrapper.last.getAndSet(m.time) < m.time), m.msg);
						}
					}));
				}
			}
			wildcardSubscriptions.keySet().stream().forEach((wildcardKey)->{
				if(topic.startsWith(wildcardKey)){
					final List<HandlerWrapper> wildcardWrappers = wildcardSubscriptions.get(wildcardKey);
					if(wildcardWrappers != null && !executor.isShutdown()){
						MsgWrapper m = new MsgWrapper(msg);
						for(final HandlerWrapper wrapper : wildcardWrappers){
							results.add(executor.submit(new Runnable() {
								@Override
								public void run() {
									wrapper.handler.update((wrapper.last.getAndSet(m.time) < m.time), m.msg);
								}
							}));
						}
					}
				}
			});

			for(Future<?> r:results){
				try { r.get(); } catch (Exception e) {}
			}
			callback.run();
		});
		return true;
	}

	/* (non-Javadoc)
	 * @see org.dynami.core.bus.IMessageService#sync(java.lang.String, java.lang.Object)
	 */
	@Override
	public boolean sync(final String topic, final Object msg){
		final List<HandlerWrapper> wrappers = subscriptions.get(topic);
		if(wrappers != null){
			for(final HandlerWrapper wrapper : wrappers){
				wrapper.handler.update(true, msg);
			}
		}
		wildcardSubscriptions.keySet().stream().forEach((wildcardKey)->{
			if(topic.startsWith(wildcardKey)){
				final List<HandlerWrapper> wildcardWrappers = wildcardSubscriptions.get(wildcardKey);
				if(wildcardWrappers != null && !executor.isShutdown()){
					MsgWrapper m = new MsgWrapper(msg);
					for(final HandlerWrapper wrapper : wildcardWrappers){
						wrapper.handler.update(true, m.msg);
					}
				}
			}
		});
		return true;
	}


	@Override
	public void forceSync(boolean forceSync){
		forceSyncExecution = forceSync;
	}

	private static class MsgWrapper {
		public final long time;
		public final Object msg;
		public MsgWrapper(Object msg) {
			this.time = System.nanoTime();
			this.msg = msg;
		}
	}

	private static class HandlerWrapper {
		public final IMsg.Handler handler;
		public final AtomicLong last = new AtomicLong(System.nanoTime());
		public HandlerWrapper(IMsg.Handler handler){
			this.handler = handler;
		}
	}

	public void unsubscribeAllFor(String topic) {
		subscriptions.remove(topic);
	}

	@Override
	public boolean dispose() {
		try {
			executor.awaitTermination(500L, TimeUnit.MILLISECONDS);
			executor.shutdown();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return true;
	}
}
