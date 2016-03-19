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
package org.dynami.runtime.services;

import org.dynami.core.bus.IMsg;
import org.dynami.core.services.ITraceService;
import org.dynami.core.services.ITraceService.Trace.Type;
import org.dynami.core.utils.DTime;
import org.dynami.core.utils.DUtils;
import org.dynami.runtime.IService;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.topics.Topics;

public class TraceService implements IService, ITraceService {

	private final IMsg msg = Execution.Manager.msg();

	@Override
	public void info(String stage, String line) {
		msg.async(Topics.TRACE.topic, new ITraceService.Trace(Type.Info, DTime.Clock.getTime(), stage, line));
	}

	@Override
	public void debug(String stage, String line) {
		msg.async(Topics.TRACE.topic, new ITraceService.Trace(Type.Debug, DTime.Clock.getTime(), stage, line));
	}

	@Override
	public void warn(String stage, String line) {
		msg.async(Topics.TRACE.topic, new ITraceService.Trace(Type.Warn, DTime.Clock.getTime(), stage, line));
	}

	@Override
	public void error(String stage, String line) {
		msg.async(Topics.TRACE.topic, new ITraceService.Trace(Type.Error, DTime.Clock.getTime(), stage, line));
	}

	@Override
	public void error(String stage, Throwable e) {
		msg.async(Topics.TRACE.topic, new ITraceService.Trace(Type.Error, DTime.Clock.getTime(), stage, DUtils.getErrorMessage(e)));
	}

	@Override
	public void market(String line) {
		msg.async(Topics.TRACE.topic, new ITraceService.Trace(Type.Info, DTime.Clock.getTime(), "Market", line));
	}

	@Override
	public String id() {
		return ID;
	}
}
