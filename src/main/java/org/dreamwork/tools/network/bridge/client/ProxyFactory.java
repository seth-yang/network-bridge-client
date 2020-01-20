package org.dreamwork.tools.network.bridge.client;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.dreamwork.app.bootloader.ApplicationBootloader;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.tools.network.bridge.client.data.Proxy;
import org.dreamwork.network.bridge.ConnectionInfo;

import java.net.InetSocketAddress;

import static org.dreamwork.tools.network.bridge.client.Keys.*;

public class ProxyFactory {
    public static ManagerClient createProxy (final Proxy proxy) {
        IConfiguration conf = ApplicationBootloader.getConfiguration (KEY_CONFIG_NAME);
        String server = conf.getString (KEY_NETWORK_HOST);
        int managePort = conf.getInt (KEY_NETWORK_MANAGE_PORT, 50041);
        int tunnelPort = conf.getInt (KEY_NETWORK_TUNNEL_PORT, 50042);
        ManagerClient client = new ManagerClient (proxy.name, server, managePort, proxy.peer, proxy.peerPort) {
            @Override
            protected int getMappingPort () {
                return proxy.servicePort;
            }
        }.setTunnelPort (tunnelPort);
        client.attach ();
        return client;
    }

    public static ConnectionInfo connect (String host, int port, IoHandler handler) {
        NioSocketConnector connector = new NioSocketConnector ();
        connector.getSessionConfig ().setReuseAddress (true);
        connector.setHandler (handler);
        ConnectFuture future = connector.connect (new InetSocketAddress (host, port));
        future.awaitUninterruptibly ();
        IoSession session = future.getSession ();

        return new ConnectionInfo (connector, session);
    }
}