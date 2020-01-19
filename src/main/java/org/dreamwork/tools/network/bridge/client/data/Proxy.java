package org.dreamwork.tools.network.bridge.client.data;

import org.dreamwork.persistence.ISchema;
import org.dreamwork.persistence.ISchemaField;
import org.dreamwork.tools.network.bridge.client.data.schema.ProxySchema;

@ISchema (ProxySchema.class)
public class Proxy {
    @ISchemaField (name = "id", id = true)
    public String id;

    @ISchemaField (name = "name")
    public String name;

/*
    public String server;

    public int managePort;

    public int tunnelPort;
*/
    @ISchemaField (name = "service_port")
    public int servicePort;

    @ISchemaField (name = "peer")
    public String peer;

    @ISchemaField (name = "peer_port")
    public int peerPort;

    @ISchemaField (name = "status")
    public String status;
}