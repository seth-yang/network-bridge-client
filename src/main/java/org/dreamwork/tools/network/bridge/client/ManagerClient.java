package org.dreamwork.tools.network.bridge.client;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.dreamwork.network.bridge.ConnectionInfo;
import org.dreamwork.tools.network.bridge.client.tunnel.Tunnel;

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

    public int getTunnelPort () {
        return tunnelPort;
    }

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
        info = connect (host, port, new IoHandlerAdapter () {
            @Override
            public void messageReceived (IoSession session, Object message) {
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
                ByteArrayOutputStream baos = new ByteArrayOutputStream ();
                DataOutputStream dos = new DataOutputStream (baos);
                dos.writeInt (getMappingPort ());
                dos.writeBoolean (isBlockLastChannel ());
                dos.writeUTF (name);
                session.write (IoBuffer.wrap (baos.toByteArray ()));
            }
        });
    }

    public void detach () {
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
