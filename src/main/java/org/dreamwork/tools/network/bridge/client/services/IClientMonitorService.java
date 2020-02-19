package org.dreamwork.tools.network.bridge.client.services;

import org.apache.mina.core.session.IoSession;
import org.dreamwork.network.service.IService;
import org.dreamwork.tools.network.bridge.client.ManagerClient;

public interface IClientMonitorService extends IService {
    boolean containsClient (String name);
    void addClient (String name, ManagerClient client);
    ManagerClient getClient (String name);
    void removeClient (String name);

    void watch (String name, IoSession session);
    void unwatch (String name);
    void start ();
    void stop ();

    String LOOP_NAME = "ClientMonitor";
}