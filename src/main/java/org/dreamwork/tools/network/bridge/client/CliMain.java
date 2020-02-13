package org.dreamwork.tools.network.bridge.client;

import org.dreamwork.app.bootloader.ApplicationBootloader;
import org.dreamwork.app.bootloader.IBootable;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.config.PropertyConfiguration;
import org.dreamwork.db.IDatabase;
import org.dreamwork.db.SQLite;
import org.dreamwork.network.sshd.Sshd;
import org.dreamwork.network.sshd.data.SystemConfig;
import org.dreamwork.network.sshd.data.schema.SystemConfigSchema;
import org.dreamwork.persistence.DatabaseSchema;
import org.dreamwork.tools.network.bridge.client.command.ProxyCommand;
import org.dreamwork.tools.network.bridge.client.data.schema.ProxySchema;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
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
        IDatabase database = createDatabase (conf.getString ("database.file"));

        DatabaseSchema.register (ProxySchema.class);
        DatabaseSchema.register (SystemConfigSchema.class);
        if (!database.isTablePresent (SystemConfig.class)) {
            database.createSchemas ();
        }

        PropertyConfiguration pc = (PropertyConfiguration)
                ApplicationBootloader.getConfiguration (KEY_CONFIG_NAME);
        if (pc == null) {
            logger.error ("can't find the config file: {}, please check it in ../conf.d", KEY_CONFIG_NAME);
            System.exit (-1);
            return;
        }
        List<SystemConfig> list = new ArrayList<> ();

        checkConfigItem (database, pc, list, KEY_NETWORK_HOST);
        checkConfigItem (database, pc, list, KEY_NETWORK_MANAGE_PORT);
        checkConfigItem (database, pc, list, KEY_NETWORK_TUNNEL_PORT);

        if (!list.isEmpty ()) {
            database.save (list);
        }
//        }

        boolean sshdDisabled = conf.getBoolean ("network.bridge.client.sshd.disabled", false);
        if (!sshdDisabled) {
            // sshd server is enabled
            Sshd sshd = new Sshd (conf);
            sshd.init (database);
            sshd.registerCommands (new ProxyCommand (database));

            sshd.bind ();
        }
    }

    private void checkConfigItem (IDatabase database, PropertyConfiguration pc, List<SystemConfig> list, String keyName) {
        String value = database.getSingleField (String.class, sql, keyName);
        if (!StringUtil.isEmpty (value)) {
            pc.setRawProperty (keyName, value);
        } else {
            SystemConfig sc = new SystemConfig ();
            sc.setId (keyName);
            sc.setValue (pc.getString (keyName));
            sc.setEditable (true);
            list.add (sc);
        }
    }

    private IDatabase createDatabase (String file) throws IOException {
        File db  = new File (file);
        File dir = db.getParentFile ();
        if (logger.isTraceEnabled ()) {
            logger.trace ("trying to create/get the database from: {}", db.getCanonicalPath ());
        }
        if (!dir.exists () && !dir.mkdirs ()) {
            logger.error ("can't create dir: {}", dir.getCanonicalPath ());
            throw new IOException ("can't create dir: " + dir.getCanonicalPath ());
        }

        SQLite sqlite = SQLite.get (db.getCanonicalPath ());
        if (logger.isTraceEnabled ()) {
            sqlite.setDebug (true);
        }

        return sqlite;
    }
}