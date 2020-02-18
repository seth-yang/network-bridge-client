package org.dreamwork.tools.network.bridge.client;

import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.dreamwork.app.bootloader.ApplicationBootloader;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.network.bridge.ConnectionInfo;
import org.dreamwork.network.bridge.tunnel.ManageProtocolFactory;
import org.dreamwork.network.bridge.tunnel.data.Command;
import org.dreamwork.network.bridge.tunnel.data.CreationCommand;
import org.dreamwork.network.bridge.tunnel.data.TokenCommand;
import org.dreamwork.network.service.ServiceFactory;
import org.dreamwork.tools.network.bridge.client.data.Proxy;
import org.dreamwork.tools.network.bridge.client.services.IClientMonitorService;
import org.dreamwork.tools.network.bridge.client.tunnel.Tunnel;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import static org.dreamwork.network.bridge.util.Helper.connect;
import static org.dreamwork.tools.network.bridge.client.Keys.KEY_MANAGER_TIMEOUT;
import static org.dreamwork.tools.network.bridge.client.Keys.KEY_TUNNEL_NAME;

/**
 * Created by seth.yang on 2019/12/18
 */
public abstract class ManagerClient {
    private int tunnelPort;
    private int port, proxyPort;
    private String name, host, proxyHost;
    private ConnectionInfo info;
    private Logger logger = LoggerFactory.getLogger (getClass ());
    private boolean planning = false;
    private final Proxy proxy;

    public ManagerClient setTunnelPort (int tunnelPort) {
        this.tunnelPort = tunnelPort;
        return this;
    }

    public ManagerClient (String name, String host, int port, Proxy proxy, String proxyHost, int proxyPort) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxy = proxy;
    }

    public void attach () throws TimeoutException {
        if (logger.isTraceEnabled ()) {
            logger.trace ("attaching to {}:{}", host, port);
        }
        planning = false;
        IClientMonitorService service = ServiceFactory.get (IClientMonitorService.class);
        ProtocolCodecFilter filter  = new ProtocolCodecFilter (new ManageProtocolFactory ());
        info = connect (host, port, new IoHandlerAdapter () {
            private Logger logger = LoggerFactory.getLogger (getClass ());

            @Override
            public void sessionOpened (IoSession session) {
                // 当 session 被打开时，请求服务器创建一个新的隧道
                if (logger.isTraceEnabled ()) {
                    logger.trace ("north session connected: {}", session);
                    logger.trace ("sending client parameters: {port = {}, block = {}, name = {}}",
                            getMappingPort (), isBlockLastChannel (), name
                    );
                }

                CreationCommand creation = new CreationCommand ();
                creation.port    = getMappingPort ();
                creation.blocked = isBlockLastChannel ();
                creation.name    = name;

                session.setAttribute ("tunnel.name", name);
                session.setAttribute ("tunnel.port", creation.port);

                session.write (creation);

                if (logger.isTraceEnabled ()) {
                    session.setAttribute ("timestamp", System.currentTimeMillis ());
                }
            }

            @Override
            public void messageReceived (IoSession session, Object message) {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("======================================");
                    logger.trace ("north session got message: {}", message);
                    logger.trace ("======================================");
                }

                Command cmd = (Command) message;
                switch (cmd.command) {
                    case Command.CREATION:  // creation, it will not happen
                        break;

                    case Command.HEARTBEAT:  // heartbeat
                        if (logger.isTraceEnabled ()) {
                            logger.trace ("manager {} heartbeats, it's alive. waiting for next hop", name);
                        }
                        break;

                    case Command.CLOSE:  // close
                        if (logger.isTraceEnabled ()) {
                            logger.trace ("i'm required to close the connect.");
                        }
                        detach ();
                        break;

                    case Command.TOKEN:  // token transport
                        byte[] token = ((TokenCommand) message).token;
                        String key   = StringUtil.byte2hex (token, false);
                        if (logger.isTraceEnabled ()) {
                            logger.trace ("here we got a new token: {}", key);
                            logger.trace ("creating a new tunnel connect to the server {}:{}", host, tunnelPort);
                        }
                        Tunnel tunnel = new Tunnel (host, tunnelPort, proxyHost, proxyPort);
                        tunnel.setToken (token);
                        tunnel.connect ();
                        break;

                    case Command.REPLY:
                        if (logger.isTraceEnabled ()) {
                            logger.trace ("received server reply: {}", cmd);
                        }
                        session.setAttribute (KEY_TUNNEL_NAME, name);
                        synchronized (proxy) {
                            proxy.notifyAll ();
                        }
                        break;
                }

                if (logger.isTraceEnabled ()) {
                    session.setAttribute ("timestamp", System.currentTimeMillis ());
                }
            }

            @Override
            public void sessionClosed (IoSession session) throws Exception {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("the session is going to close, clear the watchdog ...");
                }
                service.unwatch (name);
                if (logger.isTraceEnabled ()) {
                    logger.trace ("the watchdog cleared");
                }

                if (!planning) {
                    if (logger.isTraceEnabled ()) {
                        logger.trace ("unexpected closing, reconnect the session");

                        logger.trace ("are they the same? {}", session == info.session);
                    }
                    // unexpected close, reconnect session
                    if (info != null) {
//                        info.session.closeNow ();
                        info.connector.dispose ();
                    }
                    info = null;

                    long time = 3000L;
                    while (true) {
                        if (logger.isTraceEnabled ()) {
                            logger.trace ("reconnect to {}:{} in {} seconds...", host, port, time / 1000);
                        }
                        Thread.sleep (time);
                        try {
                            // reconnect to the server
                            attach ();
                            break;
                        } catch (Exception ex) {
                            logger.warn (ex.getMessage (), ex);
                            time <<= 1;
                            if (time >= 60000) {
                                time = 60000;
                            }
                        }
                    }
                }
            }

            @Override
            public void messageSent (IoSession session, Object message) {
                if (logger.isTraceEnabled ()) {
                    session.setAttribute ("timestamp", System.currentTimeMillis ());
                }
            }

            @Override
            public void sessionIdle (IoSession session, IdleStatus status) {
                if (logger.isTraceEnabled ()) {
                    Long timestamp = (Long) session.getAttribute ("timestamp");
                    if (timestamp != null) {
                        long delta = System.currentTimeMillis () - timestamp;
                        logger.trace (
                                "the client[{}] idle, status = {}, last communicated was {} ms ago",
                                getMappingPort (), status, delta
                        );
                    }
                }
            }

            @Override
            public void exceptionCaught (IoSession session, Throwable cause) {
                String name = (String) session.getAttribute ("tunnel.name");
                logger.warn ("an error occurred in session [{}]", name);
                logger.warn (cause.getMessage (), cause);
            }
        }, new String[] {"protocol"}, new IoFilter[] {filter});

        synchronized (proxy) {
            IConfiguration conf = ApplicationBootloader.getConfiguration (Keys.KEY_CONFIG_NAME);
            int timeout = conf.getInt (KEY_MANAGER_TIMEOUT, 20);
            if (logger.isTraceEnabled ()) {
                logger.trace ("waiting for the proxy connect in {} seconds}", timeout);
            }
            timeout *= 1000;
            long now = System.currentTimeMillis ();
            try {
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
                logger.info ("connected");
                if (logger.isTraceEnabled ()) {
                    logger.trace ("add watching into the client monitor");
                }
                service.watch (name, info.session);
            }
        }
    }

    public void detach () {
        planning = true;
        if (info != null) {
            if (logger.isTraceEnabled ()) {
                String name = (String) info.session.getAttribute ("tunnel.name");
                logger.trace ("detaching the session [{}]", name);
            }
            info.session.closeNow ();
            info.connector.dispose ();

            IClientMonitorService service = ServiceFactory.get (IClientMonitorService.class);
            service.unwatch (name);
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

    private final static ExecutorService executor = Executors.newScheduledThreadPool (1);

    static {
        Runtime.getRuntime ().addShutdownHook (new Thread (executor::shutdownNow));
    }
}