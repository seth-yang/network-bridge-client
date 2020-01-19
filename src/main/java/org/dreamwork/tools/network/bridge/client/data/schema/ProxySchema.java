package org.dreamwork.tools.network.bridge.client.data.schema;

import org.dreamwork.persistence.DatabaseSchema;

public class ProxySchema extends DatabaseSchema {
    public ProxySchema () {
        tableName = "t_proxy";
        fields = new String[] {"id", "name", "service_port", "peer", "peer_port", "status"};
    }

    @Override
    public String getCreateDDL () {
        return "CREATE TABLE t_proxy (\n" +
                "    id              TEXT                PRIMARY KEY,\n" +
                "    name            TEXT,\n" +
                "    service_port    INTEGER,\n" +
                "    peer            TEXT,\n" +
                "    peer_port       INTEGER,\n" +
                "    status          TEXT\n" +
                ")";
    }

    @Override
    public String getPrimaryKeyName () {
        return "id";
    }
}
