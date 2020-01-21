package org.dreamwork.tools.network.bridge.client;

import org.dreamwork.app.bootloader.ApplicationBootloader;
import org.dreamwork.app.bootloader.IBootable;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.config.PropertyConfiguration;
import org.dreamwork.db.SQLite;
import org.dreamwork.network.sshd.Sshd;
import org.dreamwork.network.sshd.data.SystemConfig;
import org.dreamwork.persistence.DatabaseSchema;
import org.dreamwork.tools.network.bridge.client.command.ProxyCommand;
import org.dreamwork.tools.network.bridge.client.data.Proxy;
import org.dreamwork.tools.network.bridge.client.data.schema.ProxySchema;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.dreamwork.tools.network.bridge.client.Keys.*;

@IBootable (argumentDef = "network-bridge-client.json")
public class CliMain {
    private final Logger logger = LoggerFactory.getLogger (CliMain.class);
    private static final String sql  = "SELECT _value FROM t_sys_conf WHERE id = ?";

    public static void main (String[] args) throws InvocationTargetException {
        ApplicationBootloader.run (CliMain.class, args);
    }

    public void start (IConfiguration conf) throws Exception {
        Sshd sshd = new Sshd ();
        sshd.setConfiguration (conf);
        SQLite sqlite = sshd.initDatabase ();
        initDatabase (sqlite);
        sshd.registerCommands (new ProxyCommand (sqlite));

        sshd.bind ();
    }

    private void initDatabase (SQLite sqlite) throws IOException {
        DatabaseSchema.register (ProxySchema.class);
        if (!sqlite.isTablePresent (Proxy.class)) {
            try (InputStream in = getClass ().getClassLoader ().getResourceAsStream ("client-schema.sql")) {
                if (in != null && sqlite.execute (in) && logger.isTraceEnabled ()) {
                    logger.trace ("create table created.");
                }
            }

            PropertyConfiguration pc = (PropertyConfiguration)
                    ApplicationBootloader.getConfiguration (KEY_CONFIG_NAME);
            if (pc == null) {
                logger.error ("can't find the config file: {}, please check it in ../conf.d", KEY_CONFIG_NAME);
                System.exit (-1);
                return;
            }
            List<SystemConfig> list = new ArrayList<> ();

            checkConfigItem (sqlite, pc, list, KEY_NETWORK_HOST);
            checkConfigItem (sqlite, pc, list, KEY_NETWORK_MANAGE_PORT);
            checkConfigItem (sqlite, pc, list, KEY_NETWORK_TUNNEL_PORT);

            if (!list.isEmpty ()) {
                sqlite.save (list);
            }
        }
    }

    private void checkConfigItem (SQLite sqlite, PropertyConfiguration pc, List<SystemConfig> list, String keyName) {
        String value = sqlite.getSingleField (String.class, sql, keyName);
        if (!StringUtil.isEmpty (value)) {
            pc.setRawProperty (keyName, value);
        } else {
            SystemConfig sc = new SystemConfig ();
            sc.setId (keyName);
            sc.setValue (pc.getString (keyName));
            list.add (sc);
        }
    }
}