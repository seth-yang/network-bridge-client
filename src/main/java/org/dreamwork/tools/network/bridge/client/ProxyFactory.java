package org.dreamwork.tools.network.bridge.client;

import org.dreamwork.app.bootloader.ApplicationBootloader;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.network.bridge.tunnel.ManagerClient;
import org.dreamwork.tools.network.bridge.client.data.Proxy;

import static org.dreamwork.tools.network.bridge.client.Keys.*;

public class ProxyFactory {
    public static void createProxy (final Proxy proxy) {
        IConfiguration conf = ApplicationBootloader.getConfiguration (KEY_CONFIG_NAME);
        String server = conf.getString (KEY_NETWORK_HOST);
        int managePort = conf.getInt (KEY_NETWORK_MANAGE_PORT, 50041);
        int tunnelPort = conf.getInt (KEY_NETWORK_TUNNEL_PORT, 50042);
        new ManagerClient (proxy.name, server, managePort, proxy.peer, proxy.peerPort) {
            @Override
            protected int getMappingPort () {
                return proxy.servicePort;
            }
        }.setTunnelPort (tunnelPort).attach ();
    }
}