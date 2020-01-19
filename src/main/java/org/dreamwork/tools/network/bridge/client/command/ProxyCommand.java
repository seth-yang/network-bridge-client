package org.dreamwork.tools.network.bridge.client.command;

import org.dreamwork.cli.text.Alignment;
import org.dreamwork.cli.text.TextFormater;
import org.dreamwork.db.SQLite;
import org.dreamwork.telnet.Console;
import org.dreamwork.telnet.TerminalIO;
import org.dreamwork.telnet.command.Command;
import org.dreamwork.tools.network.bridge.client.data.Proxy;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProxyCommand extends Command {
    private static final Logger logger = LoggerFactory.getLogger (ProxyCommand.class);
    private static final String[] KNOWN_ACTIONS = {"help", "print", "add", "delete", "modify", "connect"};
    private String action = "print";
    private String message;
    private String name, peer, port, mappingPort;
    private SQLite sqlite;

    public ProxyCommand (SQLite sqlite) {
        super ("proxy", null, "proxy manage command");
        this.sqlite = sqlite;
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
                case "del"  : action = "delete";  break;
                case "mod"  : action = "modify";  break;
                case "conn" : action = "connect"; break;
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
        if (options.length > 1) for (int i = 1; i < options.length; i ++) {
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
            }
        }
    }

    @Override
    public void showHelp (Console console) throws IOException {
        help (console, TerminalIO.YELLOW, "proxy [command] [options]");
        help (console, TerminalIO.YELLOW, "where valid options are:");
        help (console, TerminalIO.CYAN,   "  -n <name>          --name=<name>                  the name of the proxy");
        help (console, TerminalIO.CYAN,   "  -m <mapping-port>  --mapping-port=<mapping-port>  the port will be mapped on the bridge server");
        help (console, TerminalIO.CYAN,   "  -P <peer-host>     --peer-host=<peer-host>        the address of local service server");
        help (console, TerminalIO.CYAN,   "  -p <peer-port>     --peer-port=<peer-port>        the port of local service");
        help (console, TerminalIO.YELLOW, "for show all created proxies:");
        help (console, TerminalIO.CYAN,   "  proxy [print]");
        help (console, TerminalIO.YELLOW, "for create a new proxy:");
        help (console, TerminalIO.CYAN,   "  proxy add [-n <name>] [-s <mapping-port>] [-P <peer-host>] [-p <peer-port>]");
        help (console, TerminalIO.CYAN,   "  will prompt to input any missing option(s)");
        help (console, TerminalIO.YELLOW, "for delete a named proxy:");
        help (console, TerminalIO.CYAN,   "  proxy del[ete] [-n] <name>");
        help (console, TerminalIO.YELLOW, "for active a named proxy:");
        help (console, TerminalIO.CYAN,   "  proxy conn[ect] [-n] <name>");
        help (console, TerminalIO.YELLOW, "for update a named proxy:");
        help (console, TerminalIO.CYAN,   "  proxy mod[ify] [-n] <name> [-s <mapping-port>] [-P <peer-host>] [-p <peer-port>]");
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
            }
        } finally {
            resetOptions ();
        }
    }

    private void performAdd (Console console) throws IOException {
        if (StringUtil.isEmpty (name)) {
            name = console.readString ("please input proxy name", false);
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

        console.println ("Please check the message: ");
        console.write ("  the proxy name          : "); help (console, TerminalIO.MAGENTA, name);
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

            sqlite.save (p, false);
        }
    }

    private static final String[] HEADERS = {"Mapping Port", "Name", "Local Service"};
    private void print (Console console) throws IOException {
        List<Proxy> list = sqlite.list (Proxy.class, "SELECT * FROM t_proxy ORDER BY name ASC");
        if (list == null || list.isEmpty ()) {
            help (console, TerminalIO.CYAN, "No Proxies Saved.");
            return;
        }

        int[] width = {HEADERS[0].length (), HEADERS[1].length ()};
        for (Proxy p : list) {
            if (width[1] < p.name.length ()) {
                width[1] = p.name.length ();
            }
        }
        console.print (TextFormater.fill (HEADERS[0], ' ', width[0], Alignment.Right));
        console.print ("  ");
        console.print (TextFormater.fill (HEADERS[1], ' ', width[1], Alignment.Left));
        console.print ("  ");
        console.println (HEADERS[2]);
        console.println (TextFormater.fill ("-", '-', width[0] + width[1] + HEADERS[2].length () + 4, Alignment.Left));

        for (Proxy p : list) {
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
            console.print (TextFormater.fill (String.valueOf (p.servicePort), ' ', width[0], Alignment.Right));
            console.print ("  ");
            console.print (TextFormater.fill (p.name, ' ', width[1], Alignment.Left));
            console.print ("  ");
            console.println (p.peer + ":" + p.peerPort);
        }
    }

    private void performDelete (Console console) throws IOException {

    }

    private void performUpdate (Console console) throws IOException {

    }

    private void connect (Console console) throws IOException {

    }

    private void help (Console console, int color, String line) throws IOException {
        console.setForegroundColor (color);
        console.println (line);
        console.setForegroundColor (TerminalIO.COLORINIT);
    }

    private void resetOptions () {
        action = "print";
        message = null;
    }

    private static final Pattern P = Pattern.compile ("^--(.*?)=(.*?)$");
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
}
