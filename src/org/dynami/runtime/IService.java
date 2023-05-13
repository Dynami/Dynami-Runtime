package org.dynami.runtime;

import org.dynami.core.config.Config;

public interface IService {
    String id();

    <T extends Config> boolean init(T config) throws Exception;

    boolean start();

    boolean stop();

    boolean resume();

    boolean dispose();

    boolean reset();

    boolean isDisposed();
}
