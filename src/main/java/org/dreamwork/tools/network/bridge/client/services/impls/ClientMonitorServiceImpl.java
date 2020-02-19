package org.dreamwork.tools.network.bridge.client.services.impls;

import org.apache.mina.core.session.IoSession;
import org.dreamwork.app.bootloader.ApplicationBootloader;
import org.dreamwork.concurrent.Looper;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.network.bridge.tunnel.data.Heartbeat;
import org.dreamwork.tools.network.bridge.client.ManagerClient;
import org.dreamwork.tools.network.bridge.client.services.IClientMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import static org.dreamwork.tools.network.bridge.client.Keys.*;

public class ClientMonitorServiceImpl implements Runnable, IClientMonitorService {
    private Logger logger = LoggerFactory.getLogger (getClass ());
    private boolean running = true;
    private final Object LOCKER = new byte[0];
    private final Map<String, IoSession> sessions = new ConcurrentHashMap<> ();
    private final Map<String, ManagerClient> clients = new HashMap<> ();

    @Override
    public boolean containsClient (String name) {
        synchronized (clients) {
            return clients.containsKey (name);
        }
    }

    @Override
    public void addClient (String name, ManagerClient client) {
        synchronized (clients) {
            clients.put (name, client);
        }
    }

    @Override
    public ManagerClient getClient (String name) {
        synchronized (clients) {
            return clients.get (name);
        }
    }

    @Override
    public void removeClient (String name) {
        synchronized (clients) {
            clients.remove (name);
        }
    }

    @Override
    public void watch (String name, IoSession session) {
        synchronized (sessions) {
            if (sessions.containsKey (name)) {
                logger.warn ("the name {} has watched", name);
                throw new IllegalStateException ();
            }
            sessions.put (name, session);
        }

        synchronized (LOCKER) {
            LOCKER.notifyAll ();
        }
    }

    @Override
    public void unwatch (String name) {
        synchronized (sessions) {
            sessions.remove (name);
        }
    }

    @Override
    public void run () {
        IConfiguration conf = ApplicationBootloader.getConfiguration (KEY_CONFIG_NAME);
        int interval = conf.getInt (KEY_HEARTBEAT_INTERVAL, 3600);
        if (logger.isTraceEnabled ()) {
            logger.trace ("the heartbeat interval = {} seconds", interval);
        }
        interval *= 1000;
        while (running) {
            synchronized (LOCKER) {
                while (sessions.isEmpty ()) {
                    try {
                        LOCKER.wait (interval);
                    } catch (InterruptedException ex) {
                        logger.warn (ex.getMessage (), ex);
                    }
                }
            }

            if (running) {
                // check running again, and still in running state, send heartbeat to the server
                Set<IoSession> set;
                synchronized (sessions) {
                    set = new HashSet<> (sessions.values ());
                }
                if (!set.isEmpty ()) {
                    if (logger.isTraceEnabled ()) {
                        logger.trace ("i'm waked up, there's {} work to do.", set.size ());
                    }
                    for (IoSession session : set) {
                        try {
                            Heartbeat hb = new Heartbeat ();
                            hb.name = (String) session.getAttribute (KEY_TUNNEL_NAME);
                            session.write (hb);
                            if (logger.isTraceEnabled ()) {
                                logger.trace ("tunnel[{}] heartbeat complete over session: {}!", hb.name, session);
                            }
                        } catch (Exception ex) {
                            logger.warn (ex.getMessage (), ex);
                        }
                    }
                } else if (logger.isTraceEnabled ()) {
                    logger.trace ("there's no sessions to watch, waiting again");
                }

                synchronized (LOCKER) {
                    try {
                        if (logger.isTraceEnabled ()) {
                            logger.trace ("waiting for {} seconds", interval / 1000);
                        }
                        LOCKER.wait (interval);  // 30s
                        if (logger.isTraceEnabled ()) {
                            logger.trace ("the sleep broken after {} seconds", interval / 1000);
                        }
                    } catch (InterruptedException ex) {
                        logger.warn (ex.getMessage (), ex);
                    }
                }
            }
        }

        logger.info ("the client monitor shutdown complete.");
    }

    @Override
    public void start () {
        logger.info ("staring the client manager monitor");
        Looper.invokeLater (this);
    }

    @Override
    public void stop () {
        running = false;
        synchronized (LOCKER) {
            LOCKER.notifyAll ();
        }
    }
}