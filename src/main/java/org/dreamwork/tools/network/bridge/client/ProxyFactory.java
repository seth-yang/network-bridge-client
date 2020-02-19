package org.dreamwork.tools.network.bridge.client;

import org.dreamwork.app.bootloader.ApplicationBootloader;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.network.service.ISystemConfigService;
import org.dreamwork.network.service.ServiceFactory;
import org.dreamwork.tools.network.bridge.client.data.Proxy;
import org.dreamwork.tools.network.bridge.client.data.ServerInfo;

import java.util.concurrent.TimeoutException;

import static org.dreamwork.tools.network.bridge.client.Keys.*;

public class ProxyFactory {
    public static ManagerClient createProxy (ServerInfo server, final Proxy proxy) throws TimeoutException {
        ManagerClient client = new ManagerClient (server.host, server.managePort, proxy) {
            @Override
            protected int getMappingPort () {
                return proxy.servicePort;
            }
        }.setTunnelPort (server.connectorPort);
        client.attach ();
        return client;
    }

    public static ManagerClient createProxy (final Proxy proxy) throws TimeoutException {
        IConfiguration conf = ApplicationBootloader.getConfiguration (KEY_CONFIG_NAME);
        String host    = conf.getString (KEY_NETWORK_HOST);
        int managePort = conf.getInt (KEY_NETWORK_MANAGE_PORT, 50041);
        int tunnelPort = conf.getInt (KEY_NETWORK_TUNNEL_PORT, 50042);

        ISystemConfigService service = ServiceFactory.get (ISystemConfigService.class);
        ServerInfo server = new ServerInfo (
                service.getMergedValue (KEY_NETWORK_HOST, host),
                service.getMergedValue (KEY_NETWORK_MANAGE_PORT, managePort),
                service.getMergedValue (KEY_NETWORK_TUNNEL_PORT, tunnelPort)
        );

        return createProxy (server, proxy);
    }
}