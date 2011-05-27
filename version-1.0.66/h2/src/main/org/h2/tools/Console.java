/*
 * Copyright 2004-2008 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.tools;

//#ifdef AWT
import java.awt.Button;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Label;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemColor;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import org.h2.util.IOUtils;
import java.io.InputStream;
//#endif
import java.sql.SQLException;

import org.h2.server.ShutdownHandler;
import org.h2.util.StartBrowser;

/**
 * This tool starts the H2 Console (web-) server, as well as the TCP and PG server.
 * For JDK 1.6, a system tray icon is created, for platforms that support it.
 * Otherwise, a small window opens.
 */
public class Console implements
//#ifdef AWT
ActionListener, MouseListener,
//#endif
ShutdownHandler {

//#ifdef AWT
    private Font font;
    private Image icon;
    private Frame frame;
//#endif
    private static final int EXIT_ERROR = 1;
    private Server web, tcp, pg;

    /**
     * The command line interface for this tool.
     * The command line options are the same as in the Server tool,
     * but this tool will always start the TCP, TCP and PG server.
     * Options are case sensitive.
     *
     * @param args the command line arguments
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        int exitCode = new Console().run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private int run(String[] args) {
        int exitCode = 0;
        try {
            web = Server.createWebServer(args);
            web.setShutdownHandler(this);
            web.start();
        } catch (SQLException e) {
            if (web == null) {
                e.printStackTrace();
            } else {
                System.out.println(web.getStatus());
            }
        }
        try {
            tcp = Server.createTcpServer(args);
            tcp.start();
        } catch (SQLException e) {
            if (tcp == null) {
                e.printStackTrace();
            } else {
                System.out.println(tcp.getStatus());
            }
        }
        try {
            pg = Server.createPgServer(args);
            pg.start();
        } catch (SQLException e) {
            if (pg == null) {
                e.printStackTrace();
            } else {
                System.out.println(pg.getStatus());
            }
        }
//#ifdef AWT
        if (!GraphicsEnvironment.isHeadless()) {
            font = new Font("Dialog", Font.PLAIN, 11);
            try {
                InputStream in = Console.class.getResourceAsStream("/org/h2/res/h2.png");
                if (in != null) {
                    byte[] imageData = IOUtils.readBytesAndClose(in, -1);
                    icon = Toolkit.getDefaultToolkit().createImage(imageData);
                }
                if (!createTrayIcon()) {
                    showWindow(true);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
//#endif

        // start browser anyway (even if the server is already running)
        // because some people don't look at the output,
        // but are wondering why nothing happens
        StartBrowser.openURL(web.getURL());
        if (!web.isRunning()) {
            exitCode = EXIT_ERROR;
        }
        return exitCode;
    }

    /**
     * INTERNAL
     */
    public void shutdown() {
        stopAll();
    }

    private void stopAll() {
        if (web != null && web.isRunning()) {
            web.stop();
            web = null;
        }
        if (tcp != null && tcp.isRunning()) {
            tcp.stop();
            tcp = null;
        }
        if (pg != null && pg.isRunning()) {
            pg.stop();
            pg = null;
        }
//#ifdef AWT
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
//#endif
        System.exit(0);
    }

//#ifdef AWT
    private boolean createTrayIcon() {
        try {
            // SystemTray.isSupported();
            Boolean supported = (Boolean) Class.forName("java.awt.SystemTray").
                getMethod("isSupported", new Class[0]).
                invoke(null, new Object[0]);

            if (!supported.booleanValue()) {
                return false;
            }

            PopupMenu menuConsole = new PopupMenu();
            MenuItem itemConsole = new MenuItem("H2 Console");
            itemConsole.setActionCommand("console");
            itemConsole.addActionListener(this);
            itemConsole.setFont(font);
            menuConsole.add(itemConsole);
            MenuItem itemStatus = new MenuItem("Status");
            itemStatus.setActionCommand("status");
            itemStatus.addActionListener(this);
            itemStatus.setFont(font);
            menuConsole.add(itemStatus);
            MenuItem itemExit = new MenuItem("Exit");
            itemExit.setFont(font);
            itemExit.setActionCommand("exit");
            itemExit.addActionListener(this);
            menuConsole.add(itemExit);

            // TrayIcon icon = new TrayIcon(image, "H2 Database Engine", menuConsole);
            Object trayIcon = Class.forName("java.awt.TrayIcon").
                getConstructor(new Class[] { Image.class, String.class, PopupMenu.class }).
                newInstance(new Object[] { icon, "H2 Database Engine", menuConsole });

            // SystemTray tray = SystemTray.getSystemTray();
            Object tray = Class.forName("java.awt.SystemTray").
                getMethod("getSystemTray", new Class[0]).
                invoke(null, new Object[0]);

            // trayIcon.addMouseListener(this);
            trayIcon.getClass().
                 getMethod("addMouseListener", new Class[]{MouseListener.class}).
                 invoke(trayIcon, new Object[]{this});

             // tray.add(icon);
             tray.getClass().
                getMethod("add", new Class[] { Class.forName("java.awt.TrayIcon") }).
                invoke(tray, new Object[] { trayIcon });

             return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void showWindow(final boolean exit) {
        frame = new Frame("H2 Console");
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent we) {
                if (exit) {
                    stopAll();
                } else {
                    frame.dispose();
                }
            }
        });
        if (icon != null) {
            frame.setIconImage(icon);
        }
        frame.setResizable(false);
        frame.setBackground(SystemColor.control);

        GridBagLayout layout = new GridBagLayout();
        frame.setLayout(layout);

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.EAST;
        c.insets.left = 2;
        c.insets.right = 2;
        c.insets.top = 2;
        c.insets.bottom = 2;

        Label label = new Label("H2 Console URL:", Label.LEFT);
        label.setFont(font);
        c.anchor = GridBagConstraints.WEST;
        c.gridwidth = GridBagConstraints.EAST;
        frame.add(label, c);

        TextField text = new TextField();
        text.setEditable(false);
        text.setFont(font);
        text.setText(web.getURL());
        text.setFocusable(false);
        c.anchor = GridBagConstraints.EAST;
        c.gridwidth = GridBagConstraints.REMAINDER;
        frame.add(text, c);

        Label label2 = new Label();
        c.anchor = GridBagConstraints.WEST;
        c.gridwidth = GridBagConstraints.EAST;
        frame.add(label2, c);

        Button startBrowser = new Button("Start Browser");
        startBrowser.setFocusable(false);
        startBrowser.setActionCommand("console");
        startBrowser.addActionListener(this);
        startBrowser.setFont(font);
        c.anchor = GridBagConstraints.EAST;
        c.gridwidth = GridBagConstraints.REMAINDER;
        frame.add(startBrowser, c);

        int width = 250, height = 120;
        frame.setSize(width, height);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation((screenSize.width - width) / 2, (screenSize.height - height) / 2);
        try {
            frame.setVisible(true);
        } catch (Throwable t) {
            // some systems don't support this method, for example IKVM
            // however it still works
            // ignore
            System.out.println("URL: " + web.getURL());
        }
    }

    private void startBrowser() {
        if (web != null) {
            StartBrowser.openURL(web.getURL());
        }
    }
//#endif

    /**
     * INTERNAL
     */
//#ifdef AWT
    public void actionPerformed(ActionEvent e) {
        String command = e.getActionCommand();
        if ("exit".equals(command)) {
            stopAll();
        } else if ("console".equals(command)) {
            startBrowser();
        } else if ("status".equals(command)) {
            showWindow(false);
        }
    }
//#endif

    /**
     * INTERNAL
     */
//#ifdef AWT
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            startBrowser();
        }
    }
//#endif

    /**
     * INTERNAL
     */
//#ifdef AWT
    public void mouseEntered(MouseEvent e) {
    }
//#endif

    /**
     * INTERNAL
     */
//#ifdef AWT
    public void mouseExited(MouseEvent e) {
    }
//#endif

    /**
     * INTERNAL
     */
//#ifdef AWT
    public void mousePressed(MouseEvent e) {
    }
//#endif

    /**
     * INTERNAL
     */
//#ifdef AWT
    public void mouseReleased(MouseEvent e) {
    }
//#endif

}