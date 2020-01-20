package org.dreamwork.tools.network.bridge.client.tunnel;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.dreamwork.network.bridge.ConnectionInfo;
import org.dreamwork.network.bridge.util.Helper;

/**
 * Created by seth.yang on 2019/12/18
 */
public class Tunnel {
    private ConnectionInfo info;
    private ConnectionInfo peer;

    private String srcHost, dstHost;
    private int srcPort, dstPort;
    private ConnectStatus status = ConnectStatus.Disconnected;

    public Tunnel (String srcHost, int srcPort, String dstHost, int dstPort) {
        this.srcHost = srcHost;
        this.dstHost = dstHost;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
    }

    public void connect () {
        info = Helper.connect (srcHost, srcPort, new IoHandlerAdapter () {
            @Override
            public void sessionCreated (IoSession session) {
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
                session.setAttribute ("peer", peer.session);
                peer.session.setAttribute ("peer", session);
                status = ConnectStatus.Connected;
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
                if (status == ConnectStatus.Disconnecting) {
                    IoSession peer = (IoSession) session.getAttribute ("peer");
                    if (peer != null) {
                        peer.closeNow ();
                    }
                    status = ConnectStatus.Disconnected;
                    Tunnel.this.peer = null;
                    info = null;
                } else {
                    disconnect ();
                    connect ();
                }
            }
        });
    }

    public void disconnect () {
        status = ConnectStatus.Disconnecting;
        if (peer != null) {
            peer.session.closeNow ();
            peer.connector.dispose ();
        }
        if (info != null) {
            info.session.closeNow ();
            info.connector.dispose ();
        }
        peer = null;
        info = null;
    }

    public IoSession getSession () {
        return info != null ? info.session : null;
    }

    enum ConnectStatus {
        Connected, Disconnecting, Disconnected
    }
}