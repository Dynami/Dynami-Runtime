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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.dynami.core.bus.IMsg;

/**
 * Message broker works as a topic/subscriber queue and allows to share messages,
 * in asynchronously and synchronously mode, between objects and threads.
 * @author Atria
 *
 */
public enum Msg2 implements IMsg {
	Broker;
	private boolean forceSync = false;
	private final static int SIZE = 1024;
	private final AtomicBoolean shutdown = new AtomicBoolean(false);
	private final Map<String, TopicHandler> topics = new ConcurrentSkipListMap<>();
	private final Thread internal = new Thread(new Runnable() {
		@Override
		public void run() {
			while(!shutdown.get()){
				topics.values().stream().forEach(th -> {
					th.subscribers.stream().forEach((TopicSubscriber ts)->{
						if(ts.cursor.get() < th.cursor.get()){
							if(th.cursor.get() >= ts.cursor().get()+th._SIZE_){
								ts.cursor.set(th.cursor.get()-th._SIZE_);
							}
							ts.handler.update(
									th.cursor.get()-1== ts.cursor.get(),
									th.buffer[ts.cursor.getAndIncrement()%th._SIZE_]);
						}
					});
				});
				// sleep execution for a nanosecond, otherwise sometimes raises unexpected NullPointerException
				try {
					TimeUnit.NANOSECONDS.sleep(1);
//					Thread.sleep(0, 1); 
				} catch (InterruptedException e) {}
			}
		}
	}, "Msg2.engine");

	{
		internal.start();
	}

	public void subscribe(String topic, IMsg.Handler handler){
		topics.putIfAbsent(topic, new TopicHandler());
		final TopicHandler h = topics.get(topic);
		h.subscribe(new TopicSubscriber(handler, h.cursor.get()));
	}

	@Override
	public void unsubscribe(String topic, Handler handler) {
		topics.get(topic).subscribers.remove(handler);
	}


	@Override
	public void unsubscribeAllFor(String topic) {
		TopicHandler topicHandler = topics.get(topic);
		if(topicHandler != null){
			topicHandler.subscribers.clear();
		}
	}

	@Override
	public void removeTopic(String topic) {
		topics.remove(topic);
	}

	public boolean async(String topic, Object msg){
		if(forceSync) return sync(topic, msg);
		TopicHandler topicHandler = topics.get(topic);
		if(topicHandler == null) return false;

		topicHandler.buffer(msg);
		return true;
	}



	@Override
	public boolean sync(String topic, Object msg) {
		TopicHandler handler = topics.get(topic);
		if(handler == null) return false;

		handler.subscribers.forEach(s->s.handler.update(true, msg));
		return true;
	}

	@Override
	public void forceSync(boolean forceSync) {
		this.forceSync = forceSync;
	}

	private static class TopicSubscriber {
		private final AtomicInteger cursor = new AtomicInteger(0);
		public final IMsg.Handler handler;

		public TopicSubscriber(IMsg.Handler handler, int _cursor) {
			this.handler = handler;
			this.cursor.set(_cursor);
		}

		public AtomicInteger cursor(){
			return cursor;
		}

		@Override
		public boolean equals(Object obj) {
			if(obj instanceof IMsg.Handler){
				return handler.equals(obj);
			} else {
				return super.equals(obj);
			}
		}
	}

	private static class TopicHandler {
		private final List<TopicSubscriber> subscribers = new CopyOnWriteArrayList<>();
		private final AtomicInteger cursor = new AtomicInteger(0);
		public final int _SIZE_;
		private final Object[] buffer;

		public TopicHandler(int bufferSize){
			this._SIZE_ = bufferSize;
			buffer = new Object[bufferSize];
		}

		public TopicHandler() {
			this(SIZE);
		}

		public void subscribe(TopicSubscriber subscriber){
			subscribers.add(subscriber);
		}

		public void buffer(Object msg){
			buffer[cursor.getAndIncrement()%_SIZE_] = msg;
		}
	}


	@Override
	public boolean dispose() {
		shutdown.set(true);
		
		return true;
	}
}
