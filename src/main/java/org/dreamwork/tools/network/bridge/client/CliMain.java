package org.dreamwork.tools.network.bridge.client;

import org.dreamwork.app.bootloader.ApplicationBootloader;
import org.dreamwork.app.bootloader.IBootable;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.config.PropertyConfiguration;
import org.dreamwork.db.SQLite;
import org.dreamwork.network.sshd.Sshd;
import org.dreamwork.network.sshd.data.SystemConfig;
import org.dreamwork.persistence.DatabaseSchema;
import org.dreamwork.tools.network.bridge.client.data.Proxy;
import org.dreamwork.tools.network.bridge.client.data.schema.ProxySchema;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.dreamwork.tools.network.bridge.client.Keys.*;

@IBootable (argumentDef = "network-bridge-client.json")
public class CliMain {
    private final Logger logger = LoggerFactory.getLogger (CliMain.class);

    public static void main (String[] args) throws InvocationTargetException {
        ApplicationBootloader.run (CliMain.class, args);
    }

    public void start (IConfiguration conf) throws Exception {
        Sshd sshd = new Sshd ();
        sshd.setConfiguration (conf);
        SQLite sqlite = sshd.initDatabase ();

        DatabaseSchema.register (ProxySchema.class);
        if (!sqlite.isTablePresent (Proxy.class)) {
            try (InputStream in = getClass ().getClassLoader ().getResourceAsStream ("client-schema.sql")) {
                if (in != null && sqlite.execute (in) && logger.isTraceEnabled ()) {
                    logger.trace ("create table created.");
                }
            }

            PropertyConfiguration pc = (PropertyConfiguration)
                    ApplicationBootloader.getConfiguration (KEY_CONFIG_NAME);
            List<SystemConfig> list = new ArrayList<> ();

            String sql  = "SELECT _value FROM t_sys_conf WHERE id = ?";
            String host = sqlite.getSingleField (String.class, sql, KEY_NETWORK_HOST);
            if (!StringUtil.isEmpty (host)) {
                pc.setRawProperty (KEY_NETWORK_HOST, host);
            } else {
                SystemConfig sc = new SystemConfig ();
                sc.setId (KEY_NETWORK_HOST);
                sc.setValue (pc.getString (KEY_NETWORK_HOST));
                list.add (sc);
            }

            Integer port = sqlite.getSingleField (Integer.class, sql, KEY_NETWORK_MANAGE_PORT);
            if (port != null) {
                pc.setRawProperty (KEY_NETWORK_MANAGE_PORT, String.valueOf (port));
            } else {
                SystemConfig sc = new SystemConfig ();
                sc.setId (KEY_NETWORK_MANAGE_PORT);
                sc.setValue (pc.getString (KEY_NETWORK_MANAGE_PORT));
                list.add (sc);
            }

            port = sqlite.getSingleField (Integer.class, sql, KEY_NETWORK_TUNNEL_PORT);
            if (port != null) {
                pc.setRawProperty (KEY_NETWORK_TUNNEL_PORT, String.valueOf (port));
            } else {
                SystemConfig sc = new SystemConfig ();
                sc.setId (KEY_NETWORK_TUNNEL_PORT);
                sc.setValue (pc.getString (KEY_NETWORK_TUNNEL_PORT));
                list.add (sc);
            }

            if (!list.isEmpty ()) {
                sqlite.save (list);
            }
        }

        sshd.bind ();
    }
}