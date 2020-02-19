package org.dreamwork.tools.network.bridge.client.tunnel;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.dreamwork.concurrent.Looper;
import org.dreamwork.network.bridge.tunnel.data.Command;
import org.dreamwork.network.bridge.tunnel.data.CreationCommand;
import org.dreamwork.network.bridge.tunnel.data.TokenCommand;
import org.dreamwork.network.service.ServiceFactory;
import org.dreamwork.tools.network.bridge.client.services.IClientMonitorService;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.dreamwork.tools.network.bridge.client.Keys.KEY_TUNNEL_NAME;

public class ManagerHandler extends IoHandlerAdapter {
    private Logger logger = LoggerFactory.getLogger (getClass ());

    private String name;
    private boolean blocked;
    private int mappingPort;

    private String host, proxyHost;
    private int tunnelPort, proxyPort;
    private ITunnelManager manager;

    public ManagerHandler (String name, boolean blocked, int mappingPort) {
        this.name = name;
        this.blocked = blocked;
        this.mappingPort = mappingPort;
    }

    public void setHost (String host) {
        this.host = host;
    }

    public void setProxyHost (String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public void setTunnelPort (int tunnelPort) {
        this.tunnelPort = tunnelPort;
    }

    public void setProxyPort (int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public void setManager (ITunnelManager manager) {
        this.manager = manager;
    }

    @Override
    public void sessionOpened (IoSession session) {
        // 当 session 被打开时，请求服务器创建一个新的隧道
        if (logger.isTraceEnabled ()) {
            logger.trace ("north session connected: {}", session);
            logger.trace ("sending client parameters: {port = {}, block = {}, name = {}}", mappingPort, blocked, name);
        }

        CreationCommand creation = new CreationCommand ();
        creation.port    = mappingPort;
        creation.blocked = blocked;
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
                manager.detach ();
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
                logger.info ("the client manager[{}] connected on session {}", name, session);
                if (manager != null) {
                    Looper.runInLoop (IClientMonitorService.LOOP_NAME, manager::onManagerAttached);
                }
                break;
        }

        if (logger.isTraceEnabled ()) {
            session.setAttribute ("timestamp", System.currentTimeMillis ());
        }
    }

    @Override
    public void sessionClosed (IoSession session) {
        IClientMonitorService service = ServiceFactory.get (IClientMonitorService.class);
        if (logger.isTraceEnabled ()) {
            logger.trace ("the session is going to close, clear the watchdog ...");
        }
        service.unwatch (name);
        if (logger.isTraceEnabled ()) {
            logger.trace ("the watchdog[{}] cleared", name);
        }

        // 通知管理器，会话已经关闭
        Looper.runInLoop (IClientMonitorService.LOOP_NAME, manager::onManagerClose);

/*
        // 已连接，且计划外，需要重连
        if (!planning && connected) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("unexpected closing, reconnect the session");
            }
            // unexpected close, reconnect session reconnectTimeout ms later

            Looper.schedule (()->{
                try {
                    manager.attach ();
                } catch (TimeoutException ex) {
                    logger.warn (ex.getMessage (), ex);
                }
            }, reconnectTimeout, TimeUnit.SECONDS);
            manager.updateReconnectTimeout (reconnectTimeout << 1);
        }
*/
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
                        "the client[{}] idle, status = {}, last communicated was {} ms ago", mappingPort, status, delta
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
}
