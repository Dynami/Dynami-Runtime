/*
 * Copyright 2023 Alessandro Atria - a.atria@gmail.com
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

import com.google.gson.Gson;
import org.dynami.core.Event;
import org.dynami.core.bus.IMsg;
import org.dynami.core.config.Config;
import org.dynami.core.services.IDataService;
import org.dynami.runtime.Service;
import org.dynami.runtime.impl.Execution;
import org.dynami.runtime.topics.Topics;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

@Config.Settings
public class EventLoggerService extends Service {
    public static final String ID = "EventLoggerService";
    private File hiddenDirectory = new File("./");
    private File eventLogFile = new File(hiddenDirectory.getAbsolutePath()+File.pathSeparator+"event_logger.log");
    private FileWriter writer;
    private final Gson gson = new Gson();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean init(Config config) throws Exception {
        if(!hiddenDirectory.exists()){
            hiddenDirectory.mkdir();
        }

        writer = new FileWriter(eventLogFile, true);

        Execution.Manager.msg().subscribe(Topics.STRATEGY_EVENT.topic, (last, msg)->{
            new Thread(()->{
                synchronized (msg){
                    final String event = gson.toJson(msg);
                    final StringBuilder builder = new StringBuilder(event.length()+1);
                    builder.append(event);
                    builder.append('\n');
                    try {
                        writer.append(builder.toString());
                        writer.flush();
                    } catch (Exception e) {}
                }
            }).start();
        });
        return super.init(config);
    }

    @Override
    public boolean start() {
        return super.start();
    }

    @Override
    public boolean stop() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {throw new RuntimeException(e);}
        return super.stop();
    }
}
