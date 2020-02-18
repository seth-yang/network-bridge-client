package org.dreamwork.tools.network.bridge.client.data;

public class ServerInfo {
    public final String host;
    public final int managePort, connectorPort;

    public ServerInfo (String host, int managePort, int connectorPort) {
        this.host = host;
        this.managePort = managePort;
        this.connectorPort = connectorPort;
    }
}
