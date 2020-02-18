package org.dreamwork.tools.network.bridge.client.tunnel;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.dreamwork.network.bridge.ConnectionInfo;
import org.dreamwork.network.bridge.util.Helper;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by seth.yang on 2019/12/18
 */
public class Tunnel {
    private ConnectionInfo info;
    private ConnectionInfo peer;

    private String srcHost, dstHost, key;
    private int srcPort, dstPort;
    private byte[] token;

    private Logger logger = LoggerFactory.getLogger (getClass ());

    public Tunnel (String srcHost, int srcPort, String dstHost, int dstPort) {
        this.srcHost = srcHost;
        this.dstHost = dstHost;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
    }

    public void connect () {
        if (logger.isTraceEnabled ()) {
            logger.trace ("trying to connect to {}:{}, token - {}", srcHost, srcPort, key);
        }
        info = Helper.connect (srcHost, srcPort, new TunnelHandler (dstHost, dstPort, token)/*new IoHandlerAdapter () {
            private Logger logger = LoggerFactory.getLogger (getClass ());

            @Override
            public void sessionOpened (IoSession session) {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("north session opened: {}", session);
                }
                peer = Helper.connect (dstHost, dstPort, new IoHandlerAdapter () {
                    @Override
                    public void messageReceived (IoSession peer, Object message) {
                        session.write (message);
                    }

                    @Override
                    public void sessionClosed (IoSession session) {
                        IoSession peer = (IoSession) session.getAttribute ("peer");
                        if (peer != null) {
                            peer.closeNow ();
                        }
                    }

                    @Override
                    public void exceptionCaught (IoSession session, Throwable cause) {
                        logger.warn ("an error occurred in session: {}", session);
                        logger.warn (cause.getMessage (), cause);
                    }
                });
                if (logger.isTraceEnabled ()) {
                    logger.trace ("south session connected: {}", peer.session);
                }

                IoBuffer buffer = IoBuffer.allocate (7);
                buffer.put ((byte) 0x04);   // token transport
                buffer.put (token);
                buffer.flip ();
                session.write (buffer);

                session.setAttribute ("peer", peer.session);
                peer.session.setAttribute ("peer", session);
                session.setAttribute ("key", key);

                logger.info (
                        "the tunnel[{}] created: {} <=> {}",
                        token,
                        session.getRemoteAddress (),
                        peer.session.getRemoteAddress ()
                );

                if (logger.isTraceEnabled ()) {
                    session.setAttribute ("timestamp", System.currentTimeMillis ());
                }
            }

            @Override
            public void messageReceived (IoSession session, Object message) {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("tunnel[{}] receive a message: {}", token, message);
                }
                IoSession peer = (IoSession) session.getAttribute ("peer");
                if (peer != null) {
                    peer.write (message);
                }

                if (logger.isTraceEnabled ()) {
                    session.setAttribute ("timestamp", System.currentTimeMillis ());
                }
            }

            @Override
            public void sessionClosed (IoSession session) {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("closing tunnel[{}]", token);
                }

                if (logger.isTraceEnabled ()) {
                    logger.trace ("closing the north connector");
                }
                if (info != null) {
                    info.connector.dispose ();
                    info = null;
                }
                logger.info ("the north connection of tunnel[{}] disconnected", key);

                if (logger.isTraceEnabled ()) {
                    logger.trace ("closing the south connector");
                }
                if (peer != null) {
                    peer.connector.dispose ();
                    peer = null;
                }
                logger.info ("the south connection of tunnel[{}] disconnected", key);

                IoSession peer = (IoSession) session.getAttribute ("peer");
                if (peer != null) {
                    peer.closeNow ();
                }
                logger.info ("tunnel[{}] closed", key);
            }

            @Override
            public void sessionIdle (IoSession session, IdleStatus status) {
                if (logger.isTraceEnabled ()) {
                    Long timestamp = (Long) session.getAttribute ("timestamp");
                    if (timestamp != null) {
                        long delta = System.currentTimeMillis () - timestamp;
                        logger.trace ("tunnel[{}] idled, last communicated in {} ms ago. status = {}", token, delta, status);
                    }
                }
            }

            @Override
            public void messageSent (IoSession session, Object message) {
                if (logger.isTraceEnabled ()) {
                    session.setAttribute ("timestamp", System.currentTimeMillis ());
                }
            }
        }*/);
    }

    public IoSession getSession () {
        return info != null ? info.session : null;
    }

    public void setToken (byte[] token) {
        this.token = token;
        if (token != null) {
            key = StringUtil.byte2hex (token, false);
        }
    }
}