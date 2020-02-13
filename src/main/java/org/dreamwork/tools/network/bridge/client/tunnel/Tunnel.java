package org.dreamwork.tools.network.bridge.client.tunnel;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.dreamwork.network.bridge.ConnectionInfo;
import org.dreamwork.network.bridge.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by seth.yang on 2019/12/18
 */
public class Tunnel {
    private ConnectionInfo info;
    private ConnectionInfo peer;

    private String srcHost, dstHost;
    private int srcPort, dstPort;

    private Logger logger = LoggerFactory.getLogger (getClass ());

    public Tunnel (String srcHost, int srcPort, String dstHost, int dstPort) {
        this.srcHost = srcHost;
        this.dstHost = dstHost;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
    }

    public void connect () {
        if (logger.isTraceEnabled ()) {
            logger.trace ("trying to connect to {}:{}", srcHost, srcPort);
        }
        info = Helper.connect (srcHost, srcPort, new IoHandlerAdapter () {
            private Logger logger = LoggerFactory.getLogger (getClass ());

            @Override
            public void sessionCreated (IoSession session) {
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
                });
                if (logger.isTraceEnabled ()) {
                    logger.trace ("south session connected: {}", peer.session);
                }
                session.setAttribute ("peer", peer.session);
                peer.session.setAttribute ("peer", session);
            }

            @Override
            public void messageReceived (IoSession session, Object message) {
                IoSession peer = (IoSession) session.getAttribute ("peer");
                if (peer != null) {
                    peer.write (message);
                }
            }

            @Override
            public void sessionClosed (IoSession session) {
                IoSession peer = (IoSession) session.getAttribute ("peer");
                if (peer != null) {
                    peer.closeNow ();
                }

                Tunnel.this.peer = null;
                info = null;
            }
        });
    }

    public IoSession getSession () {
        return info != null ? info.session : null;
    }
}