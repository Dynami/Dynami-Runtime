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
package org.dynami.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import org.dynami.core.config.Config;
import org.dynami.core.utils.DUtils;
import org.dynami.runtime.ib.IBService;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.services.AssetService;
import org.dynami.runtime.services.DataService;
import org.dynami.runtime.services.OrderService;
import org.dynami.runtime.services.PortfolioService;
import org.dynami.runtime.services.TraceService;
import org.dynami.runtime.topics.Topics;

public interface IServiceBus {
	public static final String ID = "IServiceBus";

	public Service registerService(final Service service, final int priority) throws Exception;

	public <T> T getService(final Class<T> service, final String ID);

	public Service getService(String ID);

	public Collection<Service> getServices();

	public default void registerDefaultServices() throws Exception {
		registerService(new AssetService(), 0);
		registerService(new TraceService(), 10);
		registerService(new DataService(), 30);
		registerService(new OrderService(), 40);
		registerService(new PortfolioService(), 50);
		registerService(new IBService(), 60);
	}

	public default boolean initServices(final Config config){
		boolean initiliazed = true;
		for(Service s : getServices()){
			try {

				if(!s.init(config)){
					initiliazed = false;
					Execution.Manager.msg().async(Topics.SERVICE_STATUS.topic, new ServiceStatus(s.id(), "init", "Unable to init service "+s.id(), null));
				}
			} catch (Exception e) {
				Execution.Manager.msg().async(Topics.SERVICE_STATUS.topic, new ServiceStatus(s.id(), "init", DUtils.getErrorMessage(e), e));
			}
		};
		return initiliazed;
	}

	public default boolean startServices(){
		final AtomicBoolean isOk = new AtomicBoolean(true);
		getServices().forEach(s->{
			try {
				if(!s.start()){
					isOk.set(false);
					Execution.Manager.msg().async(Topics.SERVICE_STATUS.topic, new ServiceStatus(s.id(), "start", "Unable to start service "+s.id(), null));
				}
			} catch (Exception e) {
				e.printStackTrace();
				Execution.Manager.msg().async(Topics.SERVICE_STATUS.topic, new ServiceStatus(s.id(), "start", "Error on "+s.id()+" "+DUtils.getErrorMessage(e), e));
			}
		});
		return isOk.get();
	}

	public default boolean resetServices(){
		final AtomicBoolean isOk = new AtomicBoolean(true);
		getServices().forEach(s->{
			try {
				if(!s.reset()){
					isOk.set(false);
					Execution.Manager.msg().async(Topics.SERVICE_STATUS.topic, new ServiceStatus(s.id(), "reset", "Unable to reset service "+s.id(), null));
				}
			} catch (Exception e) {
				Execution.Manager.msg().async(Topics.SERVICE_STATUS.topic, new ServiceStatus(s.id(), "reset", DUtils.getErrorMessage(e), e));
			}
		});
		return isOk.get();
	}


	public default boolean stopServices(){
		final AtomicBoolean isOk = new AtomicBoolean(true);
		getServices().forEach(s->{
			try {
				if(!s.stop()){
					isOk.set(false);
					Execution.Manager.msg().async(Topics.SERVICE_STATUS.topic, new ServiceStatus(s.id(), "stop", "Unable to stop service "+s.id(), null));
				}
			} catch (Exception e) {
				Execution.Manager.msg().async(Topics.SERVICE_STATUS.topic, new ServiceStatus(s.id(), "stop", DUtils.getErrorMessage(e), e));
			}
		});
		return isOk.get();
	}

	public default boolean resumeServices(){
		final AtomicBoolean isOk = new AtomicBoolean(true);
		getServices().forEach(s->{
			try {
				if(!s.resume()){
					isOk.set(false);
					Execution.Manager.msg().async(Topics.SERVICE_STATUS.topic, new ServiceStatus(s.id(), "resume", "Unable to resume service "+s.id(), null));
				}
			} catch (Exception e) {
				Execution.Manager.msg().async(Topics.SERVICE_STATUS.topic, new ServiceStatus(s.id(), "resume", DUtils.getErrorMessage(e), e));
			}
		});
		return isOk.get();
	}

	public default boolean disposeServices(){
		final AtomicBoolean isOk = new AtomicBoolean(true);
		getServices().forEach(s->{
			try {
				if(!s.dispose()){
					isOk.set(false);
					Execution.Manager.msg().async(Topics.SERVICE_STATUS.topic, new ServiceStatus(s.id(), "dispose", "Unable to dispose service "+s.id(), null));
				}
			} catch (Exception e) {
				Execution.Manager.msg().async(Topics.SERVICE_STATUS.topic, new ServiceStatus(s.id(), "dispose", DUtils.getErrorMessage(e), e));
			}
		});
		return isOk.get();
	}


	public static class ServiceStatus {
		public final String serviceId;
		public final String operation;
		public final String message;
		public final Throwable error;

		public ServiceStatus(String serviceId, String operation, String message, Throwable error){
			this.serviceId = serviceId;
			this.operation = operation;
			this.message = message;
			this.error = error;
		}

		@Override
		public String toString() {
			return "ServiceStatus [serviceId=" + serviceId + ", operation=" + operation + ", message=" + message + "]";
		}
	}

//	public default void inject(Object obj) throws Exception {
//		Field[] fields = obj.getClass().getDeclaredFields();
//		for(Field f : fields){
//			IServiceBus.Inject inject = f.getAnnotation(IServiceBus.Inject.class);
//
//			if(inject != null){
//				String id = inject.value();
//				Object s = getService(id);
//				if(s != null){
//					f.setAccessible(true);
//					f.set(obj, s);
//				}
//			}
//		}
//	}

	@Target({ElementType.FIELD})
	@Retention(RetentionPolicy.RUNTIME)
	public static @interface Inject {
		String value();
	}
}
