package org.dreamwork.tools.network.bridge.client;

import org.apache.mina.core.RuntimeIoException;
import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.dreamwork.app.bootloader.ApplicationBootloader;
import org.dreamwork.concurrent.Looper;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.network.bridge.ConnectionInfo;
import org.dreamwork.network.bridge.tunnel.ManageProtocolFactory;
import org.dreamwork.network.service.ServiceFactory;
import org.dreamwork.tools.network.bridge.client.data.Proxy;
import org.dreamwork.tools.network.bridge.client.services.IClientMonitorService;
import org.dreamwork.tools.network.bridge.client.tunnel.ITunnelManager;
import org.dreamwork.tools.network.bridge.client.tunnel.ManagerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.dreamwork.network.bridge.util.Helper.connect;
import static org.dreamwork.tools.network.bridge.client.Keys.KEY_MANAGER_TIMEOUT;

/**
 * Created by seth.yang on 2019/12/18
 */
public abstract class ManagerClient implements ITunnelManager {
    private int tunnelPort, port;
    private String name, host;
    private ConnectionInfo info;
    private Logger logger = LoggerFactory.getLogger (getClass ());
    private final Proxy proxy;
    private ManagerHandler handler = null;
    private int reconnectTimeout = 3;
    private State state = State.Detached;
    private IClientMonitorService monitor = ServiceFactory.get (IClientMonitorService.class);

    public ManagerClient (/*String name, */String host, int port, Proxy proxy/*, String proxyHost, int proxyPort*/) {
        this.name = proxy.name;
        this.host = host;
        this.port = port;
        this.proxy = proxy;
    }

    public ManagerClient setTunnelPort (int tunnelPort) {
        this.tunnelPort = tunnelPort;
        return this;
    }

    public void attach () throws TimeoutException {
        if (logger.isTraceEnabled ()) {
            logger.trace ("attaching to {}:{}", host, port);
        }

        synchronized (this) {
            state = State.Connecting;
        }

        ProtocolCodecFilter filter  = new ProtocolCodecFilter (new ManageProtocolFactory ());
        handler = new ManagerHandler (name, isBlockLastChannel (), getMappingPort ());
        handler.setHost (host);
        handler.setProxyHost (proxy.peer);
        handler.setTunnelPort (tunnelPort);
        handler.setProxyPort (proxy.peerPort);
        handler.setManager (this);
        try {
            info = connect (host, port, handler, new String[] {"protocol"}, new IoFilter[] {filter});
        } catch (RuntimeIoException ex) {
            logger.warn (ex.getMessage (), ex);
            logger.warn ("=====================================");
            logger.warn ("connect to server failed. retry in {}s later", reconnectTimeout);
            logger.warn ("=====================================");
            reconnect ();
            return;
        }

        synchronized (proxy) {
            IConfiguration conf = ApplicationBootloader.getConfiguration (Keys.KEY_CONFIG_NAME);
            int timeout = conf.getInt (KEY_MANAGER_TIMEOUT, 20);
            if (logger.isTraceEnabled ()) {
                logger.trace ("waiting for the proxy connect in {} seconds}", timeout);
            }
            timeout *= 1000;
            long now = System.currentTimeMillis ();
            try {
                // waiting for the manager attached
                proxy.wait (timeout);
            } catch (InterruptedException ex) {
                // ignore
            }
            if (logger.isTraceEnabled ()) {
                logger.trace ("i'm waked up");
            }
            long delta = System.currentTimeMillis () - now;
            if (delta > timeout) {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("timed out");
                }
                throw new TimeoutException (String.valueOf (delta));
            } else {
                logger.info ("the manager [{}] connected", name);
                if (logger.isTraceEnabled ()) {
                    logger.trace ("add watching into the client monitor");
                }
                monitor.watch (name, info.session);
            }
        }
    }

    public void detach () {
        // planning to close session
        synchronized (this) {
            state = State.Planing;
        }
        if (info != null && info.connector != null) {
            info.connector.dispose ();
        }
        handler = null;
    }

    @Override
    public void updateReconnectTimeout (int timeout) {
        this.reconnectTimeout = Math.min (timeout, 60);
    }

    @Override
    public void onManagerAttached () {
        if (logger.isTraceEnabled ()) {
            logger.trace ("the manager is attached to server, notify it.");
        }
        synchronized (this) {
            state = State.Connected;
            reconnectTimeout = 3;
        }
        synchronized (proxy) {
            proxy.notifyAll ();
        }
    }

    @Override
    public void onManagerClose () {
        // 注意：这个方法的当前线程是 IClientMonitorService.LOOP_NAME，不要阻塞这个线程
        State current;
        synchronized (this) {
            current = state;
        }
        if (logger.isTraceEnabled ()) {
            logger.trace ("current state = {}", current);
        }
        if (current == State.Planing) {
            synchronized (this) {
                state = State.Detached;
            }
        } else if (current != State.Connecting) {
            // 计划外的关闭，且不在连接过程中，需要重连
            synchronized (this) {
                state = State.Connecting;
            }
            if (logger.isTraceEnabled ()) {
                logger.trace ("reconnect to server {}s later", reconnectTimeout);
            }
            reconnect ();
        } else if (logger.isTraceEnabled ()) {
            logger.trace ("there's another connection working, ignore this message");
        }
    }

    /**
     * 向管理器注册 前端映射的端口
     * @return 前端的映射端口
     */
    protected abstract int getMappingPort ();

    /**
     * 是否阻塞上个io隧道
     * @return 是否阻塞
     */
    protected boolean isBlockLastChannel () {
        return false;
    }

    private void reconnect () {
        Looper.schedule (() -> {
            try {
                // 清理上一次连接遗留的资源
                if (info != null) {
                    if (logger.isTraceEnabled ()) {
                        String name = (String) info.session.getAttribute ("tunnel.name");
                        logger.trace ("detaching the session [{}]", name);
                    }

                    if (info.connector != null && !info.connector.isDisposed ()) {
                        info.connector.dispose ();
                    }

                    monitor.unwatch (name);
                }

                attach ();
            } catch (TimeoutException e) {
                e.printStackTrace ();
            }
        }, reconnectTimeout, TimeUnit.SECONDS);
        reconnectTimeout = Math.min (reconnectTimeout << 1, 60);
    }

    private enum State {
        Detached, Connecting, Connected, Planing
    }
}