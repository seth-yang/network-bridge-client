package org.dreamwork.tools.network.bridge.client.command;

import org.dreamwork.app.bootloader.ApplicationBootloader;
import org.dreamwork.cli.text.Alignment;
import org.dreamwork.cli.text.TextFormater;
import org.dreamwork.config.IConfiguration;
import org.dreamwork.db.IDatabase;
import org.dreamwork.network.service.ISystemConfigService;
import org.dreamwork.network.service.ServiceFactory;
import org.dreamwork.telnet.Console;
import org.dreamwork.telnet.TerminalIO;
import org.dreamwork.telnet.command.Command;
import org.dreamwork.tools.network.bridge.client.ManagerClient;
import org.dreamwork.tools.network.bridge.client.ProxyFactory;
import org.dreamwork.tools.network.bridge.client.data.Proxy;
import org.dreamwork.tools.network.bridge.client.data.ServerInfo;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.dreamwork.tools.network.bridge.client.Keys.*;

public class ProxyCommand extends Command {
    private static final Logger logger = LoggerFactory.getLogger (ProxyCommand.class);
    private static final String[] KNOWN_ACTIONS = {"help", "print", "add", "delete", "modify", "connect", "disconnect"};
    private static final Pattern P = Pattern.compile ("^--(.*?)=(.*?)$");
    private static final Map<String, ManagerClient> clients = new HashMap<> ();

    private String action = "print";
    private String message;
    private String name, peer, port, mappingPort;
    private IDatabase database;

    public ProxyCommand (IDatabase database) {
        super ("tunnel", null, "tunnel manage command");
        this.database = database;
    }

    @Override
    public void parse (String... options) {
        for (String opt : options) {
            if ("-h".equals (opt) || "--help".equals (opt)) {
                action = "help";
                return;
            }
        }

        if (options.length > 0) {
            action = options [0];

            switch (action) {
                case "del"  : action = "delete";     break;
                case "mod"  : action = "modify";     break;
                case "conn" : action = "connect";    break;
                case "dis"  : action = "disconnect"; break;
            }
        }
        boolean hit = false;
        for (String act : KNOWN_ACTIONS) {
            if (act.equals (action)) {
                hit = true;
                break;
            }
        }
        if (!hit) {
            message = "unknown command: " + action;
            return;
        }

        options = translate (options);
        if (options.length > 1) {
            for (int i = 1; i < options.length; i ++) {
                switch (options [i]) {
                    case "-n":
                    case "--name":
                        name = options [++ i];
                        break;
                    case "-m":
                    case "--mapping-port":
                        mappingPort = options [++ i];
                        break;
                    case "-P":
                    case "--peer-host":
                        peer = options [++ i];
                        break;
                    case "-p":
                    case "--peer-port":
                        port = options [++ i];
                        break;
                    default:
                        if (i == 1) {
                            name = options [i];
                        }
                        break;
                }
            }
        }
    }

    @Override
    public void showHelp (Console console) throws IOException {
        help (console, TerminalIO.YELLOW, "tunnel [command] [options]");
        help (console, TerminalIO.YELLOW, "where valid options are:");
        help (console, TerminalIO.CYAN,   "  -n <name>          --name=<name>                  the name of the tunnel");
        help (console, TerminalIO.CYAN,   "  -m <mapping-port>  --mapping-port=<mapping-port>  the port will be mapped on the bridge server");
        help (console, TerminalIO.CYAN,   "  -P <peer-host>     --peer-host=<peer-host>        the address of local service server");
        help (console, TerminalIO.CYAN,   "  -p <peer-port>     --peer-port=<peer-port>        the port of local service");
        help (console, TerminalIO.YELLOW, "for show all created proxies:");
        help (console, TerminalIO.CYAN,   "  tunnel [print]");
        help (console, TerminalIO.YELLOW, "for create a new tunnel:");
        help (console, TerminalIO.CYAN,   "  tunnel add [-n <name>] [-s <mapping-port>] [-P <peer-host>] [-p <peer-port>]");
        help (console, TerminalIO.CYAN,   "  will prompt to input any missing option(s)");
        help (console, TerminalIO.YELLOW, "for delete a named tunnel:");
        help (console, TerminalIO.CYAN,   "  tunnel del[ete] [-n] <name>");
        help (console, TerminalIO.YELLOW, "for active a named tunnel:");
        help (console, TerminalIO.CYAN,   "  tunnel conn[ect] [-n] <name>");
        help (console, TerminalIO.YELLOW, "for shutdown a named tunnel:");
        help (console, TerminalIO.CYAN,   "  tunnel dis[connect] [-n] <name>");
        help (console, TerminalIO.YELLOW, "for update a named tunnel:");
        help (console, TerminalIO.CYAN,   "  tunnel mod[ify] [-n] <name> [-s <mapping-port>] [-P <peer-host>] [-p <peer-port>]");
    }

    @Override
    public boolean isOptionSupported () {
        return true;
    }

    @Override
    public void perform (Console console) throws IOException {
        if (!StringUtil.isEmpty (message)) {
            console.errorln (message);
            resetOptions ();
            return;
        }

        try {
            switch (action) {
                case "help":
                    showHelp (console);
                    break;
                case "add":
                    performAdd (console);
                    break;
                case "print":
                    print (console);
                    break;
                case "delete":
                    performDelete (console);
                    break;
                case "modify":
                    performUpdate (console);
                    break;
                case "connect":
                    connect (console);
                    break;
                case "disconnect":
                    disconnect (console);
                    break;
            }
        } finally {
            resetOptions ();
        }
    }

    private void performAdd (Console console) throws IOException {
        if (StringUtil.isEmpty (name)) {
            name = console.readString ("please input tunnel name", false);
        }

        int service_port = -1;
        if (!StringUtil.isEmpty (mappingPort)) try {
            service_port = Integer.parseInt (mappingPort);
        } catch (NumberFormatException nfe) {
            logger.warn (nfe.getMessage (), nfe);
        }
        if (service_port <= 0) {
            service_port = console.readInt (
                    "please input the service port",
                    "the port number should between (0, 65535]",
                    value -> 0 < value && value < 65535
            );
        }

        if (StringUtil.isEmpty (peer)) {
            peer = console.readString ("please input the local server", false);
        }

        int peer_port = -1;
        if (!StringUtil.isEmpty (port)) try {
            peer_port = Integer.parseInt (port);
        } catch (NumberFormatException nfe) {
            logger.warn (nfe.getMessage (), nfe);
        }
        if (peer_port <= 0) {
            peer_port = console.readInt (
                    "please input the port of local service",
                    "the port number should between (0, 65535]",
                    value -> 0 < value && value < 65535
            );
        }

        if (!checkName (console)) {
            return;
        }
        if (!checkMappingPort (console, service_port)) {
            return;
        }

        console.println ("Please check the message: ");
        console.write ("  the tunnel name         : "); help (console, TerminalIO.MAGENTA, name);
        console.write ("  the mapping port        : "); help (console, TerminalIO.MAGENTA, String.valueOf (service_port));
        console.write ("  the local server        : "); help (console, TerminalIO.MAGENTA, peer);
        console.write ("  the port of local server: "); help (console, TerminalIO.MAGENTA, String.valueOf (peer_port));
        console.setForegroundColor (TerminalIO.YELLOW);
        if (console.readBoolean ("Does the parameters are correct", true)) {
            Proxy p = new Proxy ();
            p.id   = StringUtil.uuid ();
            p.name = name;
            p.peer = peer;
            p.peerPort = peer_port;
            p.servicePort = service_port;

            database.save (p, false);
        }
    }

    private static final String[] HEADERS = {"Mapping Port", "Name", "Local Service", "Status"};
    private void print (Console console) throws IOException {
        List<Proxy> list = database.list (Proxy.class, "SELECT * FROM t_proxy ORDER BY name ASC");
        if (list == null || list.isEmpty ()) {
            help (console, TerminalIO.CYAN, "No Proxies Saved.");
            return;
        }

        int[] width = {HEADERS[0].length (), HEADERS[1].length (), HEADERS[2].length ()};
        String[][] data = new String[list.size ()][4];
        int pos = 0;
        for (Proxy p : list) {
            data [pos][0] = String.valueOf (p.servicePort);
            data [pos][1] = p.name;
            data [pos][2] = p.peer + ':' + p.peerPort;
            data [pos][3] = StringUtil.isEmpty (p.status) ? "" : p.status;
            if (width[1] < p.name.length ()) {
                width[1] = p.name.length ();
            }
            if (width[2] < data[pos][2].length ()) {
                width[2] = data[pos][2].length ();
            }
            pos ++;
        }
        console.print (TextFormater.fill (HEADERS[0], ' ', width[0], Alignment.Right));
        console.print ("  ");
        console.print (TextFormater.fill (HEADERS[1], ' ', width[1], Alignment.Left));
        console.print ("  ");
        console.print (TextFormater.fill (HEADERS[2], ' ', width[2], Alignment.Left));
        console.print ("  ");
        console.println (HEADERS[3]);
        // connected.length = 9
        int line_width = width [0] + width[1] + width [2] + 15;
        console.println (TextFormater.fill ("-", '-', line_width, Alignment.Left));

        for (String[] line : data) {
/*
            // draw the horizontal line
            console.print (TextFormater.fill ("-", '-', width[0], Alignment.Left));
            console.write ("-+-");
            console.print (TextFormater.fill ("-", '-', width[1], Alignment.Left));
            console.write ("-+-");
            console.print (TextFormater.fill ("-", '-', HEADERS[2].length (), Alignment.Left));
            console.println ();
*/

            // print a row
            console.print (TextFormater.fill (line[0], ' ', width[0], Alignment.Right));
            console.print ("  ");
            console.print (TextFormater.fill (line[1], ' ', width[1], Alignment.Left));
            console.print ("  ");
            console.print (TextFormater.fill (line[2], ' ', width[2], Alignment.Left));
            console.print ("  ");
            console.println (line[3]);
        }
    }

    private void performDelete (Console console) throws IOException {
        if (StringUtil.isEmpty (name)) {
            console.errorln ("tunnel name is missing, assign it with the -n or --name option");
            return;
        }

        String sql = "DELETE FROM t_proxy WHERE name = ?";
        if (database.executeUpdate (sql, name) > 0) {
            console.println ("  delete success");
        }
    }

    private void performUpdate (Console console) throws IOException {
        if (StringUtil.isEmpty (name)) {
            console.errorln ("tunnel name is missing, assign it with the -n or --name option");
            return;
        }

        Map<String, Object> params = new HashMap<> ();

        if (!StringUtil.isEmpty (mappingPort)) {
            try {
                int mapping_port = Integer.parseInt (mappingPort);
                if (!checkMappingPort (console, mapping_port)) {
                    return;
                }

                params.put ("mapping_port", mapping_port);
            } catch (NumberFormatException nfe) {
                console.errorln ("invalid port number");
                return;
            }
        }

        if (!StringUtil.isEmpty (port)) {
            try {
                int peer_port = Integer.parseInt (port);
                if (!checkPort (console, peer_port)) {
                    return;
                }
                params.put ("peer_port", peer_port);
            } catch (NumberFormatException nfe) {
                console.errorln ("invalid port number");
                return;
            }
        }

        if (!StringUtil.isEmpty (peer)) {
            params.put ("peer", peer);
        }

        if (!params.isEmpty ()) {
            StringBuilder builder = new StringBuilder ("UPDATE t_proxy SET ");
            Object[] values = new Object[params.size () + 1];
            int pos = 0;
            for (Map.Entry<String, Object> e : params.entrySet ()) {
                if (pos > 0) {
                    builder.append (", ");
                }
                builder.append (e.getKey ()).append (" = ?");
                values[pos ++] = e.getValue ();
            }
            builder.append (" WHERE name = ?");
            values [pos] = name;
            if (logger.isTraceEnabled ()) {
                logger.trace ("sql: {}", builder);
                logger.trace ("parameters: {}", Arrays.toString (values));
            }

            if (database.executeUpdate (builder.toString (), values) > 0) {
                console.println ("update success.");
            }
        }
    }

    private void connect (Console console) throws IOException {
        if (StringUtil.isEmpty (name)) {
            console.errorln ("tunnel name is missing, assign it with the -n or --name option");
            return;
        }

        synchronized (clients) {
            if (clients.containsKey (name)) {
                console.print ("tunnel [" + name + "] connected");
                return;
            }

            Proxy proxy = getProxyByName ();
            try {

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

                ManagerClient client = ProxyFactory.createProxy (server, proxy);
                clients.put (name, client);
                proxy.status = "connected";
                database.update (proxy);
                console.println ("tunnel [" + name + "] connected.");
            } catch (Exception ex) {
                logger.warn (ex.getMessage (), ex);
                console.errorln ("create tunnel [" + name + "] failed.");
            }
        }
    }

    private void disconnect (Console console) throws IOException {
        if (StringUtil.isEmpty (name)) {
            console.errorln ("tunnel name is missing, assign it with the -n or --name option");
            return;
        }

        Proxy proxy = getProxyByName ();
        if (StringUtil.isEmpty (proxy.status)) {
            console.errorln ("tunnel [" + name + "] is not connected");
            return;
        }

        ManagerClient client = clients.get (name);
        if (client != null) {
            client.detach ();
        }
        clients.remove (name);
        proxy.status = null;
        database.update (proxy);
        console.println ("tunnel [" + name + "] disconnected.");
    }

    private void help (Console console, int color, String line) throws IOException {
        console.setForegroundColor (color);
        console.println (line);
        console.setForegroundColor (TerminalIO.COLORINIT);
    }

    private void resetOptions () {
        action = "print";
        mappingPort = null;
        peer = null;
        port = null;
        name = null;
        message = null;
    }

    private String[] translate (String... options) {
        List<String> list = new ArrayList<> ();
        for (String opt : options) {
            if (!opt.startsWith ("--")) {
                list.add (opt);
            } else {
                Matcher m = P.matcher (opt);
                if (m.matches ()) {
                    list.add ("--" + m.group (1));
                    list.add (m.group (2));
                }
            }
        }
        String[] a = new String[list.size ()];
        return list.toArray (a) ;
    }

    private boolean checkName (Console console) throws IOException {
        if (StringUtil.isEmpty (name)) {
            console.errorln ("tunnel name is missing, assign it with the -n or --name option");
            return false;
        }

        Proxy proxy = getProxyByName ();
        if (proxy != null) {
            console.errorln ("the name [" + name + "] already exists");
            return false;
        }
        return true;
    }

    private boolean checkMappingPort (Console console, int service_port) throws IOException {
        if (!checkPort (console, service_port)) {
            return false;
        }

        String sql = "SELECT * FROM t_proxy WHERE service_port = ?";
        Proxy proxy = database.getSingle (Proxy.class, sql, service_port);
        if (proxy != null) {
            console.errorln ("the port [" + service_port + "] has mapped.");
            return false;
        }
        return true;
    }

    private boolean checkPort (Console console, int port) throws IOException {
        if (port <= 0 || port > 65535) {
            console.errorln ("invalid port number.");
            return false;
        }

        return true;
    }

    private Proxy getProxyByName () {
        String sql = "SELECT * FROM t_proxy WHERE name = ?";
        return database.getSingle (Proxy.class, sql, name);
    }
}