package org.dreamwork.tools.network.bridge.client;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.dreamwork.network.bridge.ConnectionInfo;
import org.dreamwork.tools.network.bridge.client.tunnel.Tunnel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.dreamwork.network.bridge.util.Helper.connect;

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

    public ManagerClient setTunnelPort (int tunnelPort) {
        this.tunnelPort = tunnelPort;
        return this;
    }

    public ManagerClient (String name, String host, int port, String proxyHost, int proxyPort) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    public void attach () {
        if (logger.isTraceEnabled ()) {
            logger.trace ("attaching to {}:{}", host, port);
        }
        planning = false;
        info = connect (host, port, new IoHandlerAdapter () {
            private Logger logger = LoggerFactory.getLogger (getClass ());

            @Override
            public void messageReceived (IoSession session, Object message) {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("north session got message");
                }
                IoBuffer buffer = (IoBuffer) message;
                if (buffer.limit () >= 6) {
                    byte[] token = new byte[6];
                    buffer.get (token);

                    Tunnel tunnel = new Tunnel (host, tunnelPort, proxyHost, proxyPort);
                    tunnel.connect ();
                    tunnel.getSession ().write (IoBuffer.wrap (token));
                }
            }

            @Override
            public void sessionOpened (IoSession session) throws IOException {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("north session connected: {}", session);
                    logger.trace ("sending client parameters: {port = {}, block = {}, name = {}}",
                            getMappingPort (), isBlockLastChannel (), name
                    );
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream ();
                DataOutputStream dos = new DataOutputStream (baos);
                dos.writeInt (getMappingPort ());
                dos.writeBoolean (isBlockLastChannel ());
                dos.writeUTF (name);
                session.write (IoBuffer.wrap (baos.toByteArray ()));
            }

            @Override
            public void sessionClosed (IoSession session) throws Exception {
                if (!planning) {
                    // unexpected close, reconnect session
                    if (info != null) {
                        info.session.closeNow ();
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
        });
    }

    public void detach () {
        planning = true;
        if (info != null) {
            info.session.closeNow ();
            info.connector.dispose ();
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
}