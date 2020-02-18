package org.dreamwork.tools.network.bridge.client.services;

import org.apache.mina.core.session.IoSession;
import org.dreamwork.network.service.IService;

public interface IClientMonitorService extends IService {
    void watch (String name, IoSession session);
    void unwatch (String name);
    void start ();
    void stop ();
}