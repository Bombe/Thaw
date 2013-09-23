package thaw.core;

import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

import freenet.crypt.Yarrow;

import thaw.fcp.FCPClientHello;
import thaw.fcp.FCPConnection;
import thaw.fcp.FCPMessage;
import thaw.fcp.FCPQueryManager;
import thaw.fcp.FCPQueueLoader;
import thaw.fcp.FCPQueueManager;
import thaw.fcp.FCPWatchGlobal;
import thaw.gui.ConfigWindow;
import thaw.gui.IconBox;
import thaw.gui.MainWindow;

/**
 * A "core" contains references to all the main parts of Thaw. The Core has all
 * the functions needed to initialize Thaw / stop Thaw.
 */
public class Core implements Observer {

	/** Default configuration filename. */
	public static final String CONFIG_FILE_NAME = "thaw.conf.xml";

	private SplashScreen splashScreen = null;

	private MainWindow mainWindow = null;

	private Config config = null;

	private PluginManager pluginManager = null;

	private ConfigWindow configWindow = null;

	private FCPConnection connection = null;

	private FCPQueryManager queryManager = null;

	private FCPQueueManager queueManager = null;

	private FCPClientHello clientHello = null;

	private String lookAndFeel = null;

	public final static int MAX_CONNECT_TRIES = 3;

	public final static int TIME_BETWEEN_EACH_TRY = 20000;

	private ReconnectionManager reconnectionManager = null;

	private static final Random RANDOM = new Yarrow();

	private boolean isStopping = false;

	/** Creates a core, but do nothing else (no initialization). */
	public Core() {
		isStopping = false;
		Logger.info(this, "Thaw, version " + Main.getVersion(), true);
		Logger.info(this, "2006-2009(c) Freenet project", true);
		Logger.info(this, "Released under GPL license version 2 or later (see http://www.fsf.org/licensing/licenses/gpl.html)", true);
	}

	/** Gives a ref to the object containing the config. */
	public Config getConfig() {
		return config;
	}

	/** Gives a ref to the object managing the splash screen. */
	public SplashScreen getSplashScreen() {
		return splashScreen;
	}

	/** Gives a ref to the object managing the main window. */
	public MainWindow getMainWindow() {
		return mainWindow;
	}

	/** Gives a ref to the object managing the config window. */
	public ConfigWindow getConfigWindow() {
		return configWindow;
	}

	/** Gives a ref to the plugin manager. */
	public PluginManager getPluginManager() {
		return pluginManager;
	}

	/**
	 * Here really start the program.
	 *
	 * @return true is success, false if not
	 */
	public boolean initAll() {
		IconBox.loadIcons();

		splashScreen = new SplashScreen();

		splashScreen.display();

		splashScreen.setProgressionAndStatus(0, "Loading configuration ...");
		splashScreen.addIcon(IconBox.settings);
		if (!initConfig())
			return false;

		splashScreen.setProgressionAndStatus(10, "Applying look and feel ...");
		if (!initializeLookAndFeel())
			return false;

		splashScreen.setProgressionAndStatus(20, "Connecting ...");
		if (!initConnection())
			new thaw.gui.WarningWindow(this, I18n.getMessage("thaw.warning.unableToConnectTo") +
					" " + config.getValue("nodeAddress") +
					":" + config.getValue("nodePort"));

		splashScreen.setProgressionAndStatus(40, "Preparing the main window ...");
		splashScreen.addIcon(IconBox.mainWindow);
		if (!initGraphics())
			return false;

		splashScreen.setProgressionAndStatus(50, "Loading plugins ...");
		if (!initPluginManager())
			return false;

		splashScreen.setProgressionAndStatus(100, "Ready");

		mainWindow.setStatus(IconBox.minDisconnectAction,
				"Thaw " + Main.getVersion() + " : " + I18n.getMessage("thaw.statusBar.ready"));

		splashScreen.hide();
		splashScreen = null;
		mainWindow.setVisible(true);

		setTheme(lookAndFeel);

		return true;
	}

	/** Init configuration. May re-set I18n. */
	public boolean initConfig() {
		config = new Config(this, CONFIG_FILE_NAME);

		config.loadConfig();

		config.setDefaultValues();

		if (config.getValue("logLevel") != null)
			Logger.setLogLevel(Integer.parseInt(config.getValue("logLevel")));

		if (config.getValue("tmpDir") != null)
			System.setProperty("java.io.tmpdir", config.getValue("tmpDir"));

		if (config.getValue("lang") != null)
			I18n.setLocale(new java.util.Locale(config.getValue("lang")));

		return true;
	}

	/**
	 * My node takes too much time to answer. So now the connection process is
	 * partially threaded.
	 */
	protected class ConnectionProcess implements ThawRunnable {

		private Core c;

		private boolean running;

		public ConnectionProcess(Core c) {
			this.c = c;
			this.running = true;
		}

		public void run() {
			process();
			connectionProcess = null;
		}

		public void stop() {
			running = false;
		}

		public boolean process() {
			boolean ret = true;

			clientHello = new FCPClientHello(queryManager, config.getValue("thawId"));

			if (!clientHello.start()) {
				Logger.warning(this, "Id already used or timeout !");
				subDisconnect();
				ret = false;
			} else {
				Logger.debug(this, "Hello successful");
				Logger.debug(this, "Node name    : " + clientHello.getNodeName());
				Logger.debug(this, "FCP  version : " + clientHello.getNodeFCPVersion());
				Logger.debug(this, "Node version : " + clientHello.getNodeVersion());

				if (ret)
					queueManager.startScheduler();

				if (!running)
					ret = false;

				if (ret)
					queueManager.restartNonPersistent();

				if (!running)
					ret = false;

				if (ret) {
					final FCPWatchGlobal watchGlobal = new FCPWatchGlobal(true, queryManager);
					watchGlobal.start();
				}

				if (!running)
					ret = false;

				if (ret) {
					final FCPQueueLoader queueLoader = new FCPQueueLoader(config.getValue("thawId"), queueManager);
					queueLoader.start();
				}

				if (!running)
					ret = false;
			}

			if (ret && connection.isConnected())
				connection.addObserver(c);

			if (getMainWindow() != null && running) {
				if (ret)
					getMainWindow().setStatus(IconBox.minConnectAction,
							I18n.getMessage("thaw.statusBar.ready"));
				else
					getMainWindow().setStatus(IconBox.minDisconnectAction,
							I18n.getMessage("thaw.statusBar.disconnected"), java.awt.Color.RED);
			}

			return ret;
		}
	}

	private ConnectionProcess connectionProcess = null;

	/**
	 * Init the connection to the node. If a connection is already established, it
	 * will disconnect, so if you called canDisconnect() before, then this function
	 * can be called safely. <br/> If the connection is opened successfully,
	 * ClientHello will be thread, so you won't have its result
	 *
	 * @see #canDisconnect()
	 */
	public boolean initConnection() {
		boolean ret = true;

		if (connectionProcess != null) {
			Logger.notice(this, "A connection process is already running");
			return false;
		}

		if (getMainWindow() != null) {
			getMainWindow().setStatus(IconBox.blueBunny,
					I18n.getMessage("thaw.statusBar.connecting"), java.awt.Color.RED);

		}

		try {
			if (queueManager != null)
				queueManager.stopScheduler();

			if ((connection != null) && connection.isConnected()) {
				subDisconnect();
			}

			if (connection != null)
				connection.deleteObserver(this);

			connection = new FCPConnection(config.getValue("nodeAddress"),
					Integer.parseInt(config.getValue("nodePort")),
					Integer.parseInt(config.getValue("maxUploadSpeed")),
					Boolean.valueOf(config.getValue("multipleSockets")).booleanValue(),
					Boolean.valueOf(config.getValue("sameComputer")).booleanValue(),
					Boolean.valueOf(config.getValue("downloadLocally")).booleanValue());

			if (!connection.connect()) {
				Logger.warning(this, "Unable to connect !");
				ret = false;
			}

			if (queryManager != null)
				queryManager.deleteObserver(this);

			queryManager = new FCPQueryManager(connection);
			queryManager.addObserver(this);

			queueManager = new FCPQueueManager(queryManager,
					config.getValue("thawId"),
					Integer.parseInt(config.getValue("maxSimultaneousDownloads")),
					Integer.parseInt(config.getValue("maxSimultaneousInsertions")));

			if (ret && connection.isConnected()) {
				queryManager.startListening();

				QueueKeeper.loadQueue(queueManager, "thaw.queue.xml");

				connectionProcess = new ConnectionProcess(this);
				Thread th = new Thread(new ThawThread(connectionProcess, "Connection process", this));
				th.start();
			}

		} catch (final Exception e) { /* A little bit not ... "nice" ... */
			Logger.warning(this, "Exception while connecting : " + e.toString() + " ; " + e.getMessage() + " ; " + e.getCause());
			e.printStackTrace();
			return false;
		}

		return ret;
	}

	public FCPConnection getConnectionManager() {
		return connection;
	}

	public FCPQueueManager getQueueManager() {
		return queueManager;
	}

	/**
	 * FCPClientHello object contains all the information given by the node when
	 * the connection was initiated.
	 */
	public FCPClientHello getClientHello() {
		return clientHello;
	}

	/**
	 * To call before initGraphics() !
	 *
	 * @param lAndF
	 * 		LookAndFeel name
	 */
	public void setLookAndFeel(final String lAndF) {
		this.lookAndFeel = lAndF;
	}

	private static class LnFSetter implements Runnable {

		private Core core;

		private String theme;

		public LnFSetter(Core c, String t) {
			core = c;
			theme = t;
		}

		public void run() {
			try {
				UIManager.setLookAndFeel(theme);
			} catch (ClassNotFoundException e) {
				Logger.error(this, "Theme '" + theme + "' not found ! : " + e.toString());
			} catch (InstantiationException e) {
				Logger.error(this, "(1) Error while loading theme '" + theme + "' : " + e.toString());
			} catch (IllegalAccessException e) {
				Logger.error(this, "(2) Error while loading theme '" + theme + "' : " + e.toString());
			} catch (javax.swing.UnsupportedLookAndFeelException e) {
				Logger.error(this, "(3) Error while loading theme '" + theme + "' : " + e.toString());
			}

			if (core.getMainWindow() != null)
				javax.swing.SwingUtilities.updateComponentTreeUI(core.getMainWindow().getMainFrame());
			if (core.getConfigWindow() != null)
				javax.swing.SwingUtilities.updateComponentTreeUI(core.getConfigWindow().getFrame());
		}
	}

	private void reallySetTheme(String theme) {
		if (theme == null)
			return;

		Logger.notice(this, "Setting theme : " + theme);

		LnFSetter s = new LnFSetter(this, theme);

		try {
			javax.swing.SwingUtilities.invokeAndWait(s);
		} catch (InterruptedException e) {
			Logger.error(s, "Interrupted while setting theme '" + theme + "' because: " + e.toString());
		} catch (java.lang.reflect.InvocationTargetException e) {
			Logger.error(s, "Error while setting theme : " + e.toString());
			Logger.notice(s, "Original exception: " + e.getTargetException().toString());
			e.getTargetException().printStackTrace();
		}
	}

	public void setTheme(String theme) {
		if (theme == null) {
			if (getConfig() != null)
				theme = getConfig().getValue("lookAndFeel");

			if (theme == null)
				theme = UIManager.getSystemLookAndFeelClassName();
		}

		lookAndFeel = theme;

		/* the recommandation is to set the lnf before displaying the first window */
		/* but I had more bugs with the GTK lnf when I followed the recommandation than
		 * when I didn't. So now I only change it when the main window is already displayed :p */

		if (mainWindow != null
				&& mainWindow.getMainFrame() != null
				&& mainWindow.getMainFrame().isVisible())
			reallySetTheme(lookAndFeel);
	}

	/**
	 * This method sets the look and feel specified with setLookAndFeel(). If none
	 * was specified, the System Look and Feel is set.
	 */
	private boolean initializeLookAndFeel() { /* non static, else I can't call correctly Logger functions */

		JFrame.setDefaultLookAndFeelDecorated(false); /* Don't touch my window decorations ! */
		JDialog.setDefaultLookAndFeelDecorated(false);

		try {
			setTheme(this.lookAndFeel);

			if (splashScreen != null)
				splashScreen.rebuild();
		} catch (final Exception e) {
			Logger.warning(this, "Exception while setting the L&F : " + e.toString() + " ; " + e.getMessage());
			e.printStackTrace();
			Logger.warning(this, "Will use the default lookAndFeel");
		}

		return true;
	}

	/** Init graphics. */
	public boolean initGraphics() {
		//initializeLookAndFeel();

		mainWindow = new MainWindow(this);

		configWindow = new ConfigWindow(this);

		return true;
	}

	/** Init plugin manager. */
	public boolean initPluginManager() {
		pluginManager = new PluginManager(this);

		if (!pluginManager.loadAndRunPlugins())
			return false;

		return true;
	}

	/** End of the world. */
	public void exit() {
		this.exit(false);
	}

	/** Makes things nicely ... :) */
	public void disconnect() {
		if (reconnectionManager != null) {
			reconnectionManager.stop();
			reconnectionManager = null;
		}

		subDisconnect();
	}

	public void subDisconnect() {
		Logger.info(this, "Disconnecting");

		if (mainWindow != null) {
			mainWindow.setStatus(IconBox.minDisconnectAction,
					I18n.getMessage("thaw.statusBar.disconnected"), java.awt.Color.RED);

			/* not null because we want to force the cleaning */
			mainWindow.changeButtonsInTheToolbar(this, new java.util.Vector());
		}

		if (connection != null) {
			connection.deleteObserver(this);
			connection.disconnect();
			Logger.info(this, "Saving queue state");
			QueueKeeper.saveQueue(queueManager, "thaw.queue.xml");
		} else {
			Logger.warning(this, "No connection ?!");
		}
	}

	/** Check if the connection can be interrupted safely. */
	public boolean canDisconnect() {
		return (connection == null) || !connection.isWriting();
	}

	/**
	 * End of the world.
	 *
	 * @param force
	 * 		if true, doesn't check if FCPConnection.isWriting().
	 * @see #exit()
	 */
	public void exit(boolean force) {
		isStopping = true;

		if (!force) {
			if (!canDisconnect()) {
				if (!askDeconnectionConfirmation())
					return;
			}
		}

		Logger.info(this, "Stopping scheduler ...");
		if (queueManager != null)
			queueManager.stopScheduler();

		Logger.info(this, "Hidding main window ...");
		mainWindow.setVisible(false);
		configWindow.setVisible(false);

		Logger.info(this, "Stopping plugins ...");
		pluginManager.stopPlugins();

		disconnect();

		Logger.info(this, "Saving configuration ...");
		if (!config.saveConfig()) {
			Logger.error(this, "Config was not saved correctly !");
		}

		ThawThreadManager thawThreadManager = ThawThread.getThawThreadManager();
		thawThreadManager.setAllowFullStop(true);

		Logger.info(this, "Threads remaining:");
		thawThreadManager.listThreads();

		Logger.info(this, "Stopping all the remaining threads ...");
		thawThreadManager.stopAll();
	}

	public boolean askDeconnectionConfirmation() {
		final int ret = JOptionPane.showOptionDialog((java.awt.Component) null,
				I18n.getMessage("thaw.warning.isWriting"),
				"Thaw - " + I18n.getMessage("thaw.warning.title"),
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE,
				(javax.swing.Icon) null,
				(java.lang.Object[]) null,
				(java.lang.Object) null);
		if ((ret == JOptionPane.CLOSED_OPTION)
				|| (ret == JOptionPane.CANCEL_OPTION)
				|| (ret == JOptionPane.NO_OPTION))
			return false;

		return true;
	}

	protected class ReconnectionManager implements ThawRunnable {

		private boolean running = true;

		private boolean initialWait = true;

		public ReconnectionManager(boolean initialWait) {
			running = true;
			this.initialWait = initialWait;
		}

		public void run() {
			synchronized (PluginManager.pluginLock) {
				Logger.notice(this, "Starting reconnection process !");

				getMainWindow().setStatus(IconBox.blueBunny,
						I18n.getMessage("thaw.statusBar.connecting"), java.awt.Color.RED);
				getPluginManager().stopPlugins(); /* don't forget there is the status bar plugin */
				getMainWindow().setStatus(IconBox.blueBunny,
						I18n.getMessage("thaw.statusBar.connecting"), java.awt.Color.RED);

				subDisconnect();

				while (running && !isStopping()) {
					try {
						if (initialWait)
							Thread.sleep(Core.TIME_BETWEEN_EACH_TRY);
					} catch (final java.lang.InterruptedException e) {
						// brouzouf
					}

					initialWait = true;

					Logger.notice(this, "Trying to reconnect ...");
					if (initConnection())
						break;
				}

				if (running && !isStopping()) {
					getMainWindow().setStatus(IconBox.minConnectAction,
							I18n.getMessage("thaw.statusBar.ready"));
				} else {
					getMainWindow().setStatus(IconBox.minDisconnectAction,
							I18n.getMessage("thaw.statusBar.disconnected"), java.awt.Color.RED);
				}

				if (running && !isStopping()) {
					getPluginManager().loadAndRunPlugins();
				}

				reconnectionManager = null;

				getMainWindow().connectionHasChanged();
			}
		}

		public void stop() {
			Logger.warning(this, "Canceling reconnection ...");
			running = false;
		}
	}

	/** use Thread => will also do all the work related to the plugins */
	public void reconnect(boolean withInitialWait) {
		synchronized (this) {
			if (reconnectionManager == null) {
				reconnectionManager = new ReconnectionManager(withInitialWait);
				final Thread th = new Thread(new ThawThread(reconnectionManager,
						"Reconnection manager", this));
				th.start();
			} else {
				Logger.warning(this, "Already trying to reconnect !");
			}
		}
	}

	public boolean isReconnecting() {
		return (reconnectionManager != null);
	}

	public void askToDisableDDA() {
		String text =
				I18n.getMessage("thaw.warning.DDA.l0") + "\n" +
						I18n.getMessage("thaw.warning.DDA.l1") + "\n" +
						I18n.getMessage("thaw.warning.DDA.l2") + "\n" +
						I18n.getMessage("thaw.warning.DDA.l3") + "\n" +
						I18n.getMessage("thaw.warning.DDA.l4");

		text = text.replaceAll("#", I18n.getMessage("thaw.config.sameComputer"));

		int ret = JOptionPane.showConfirmDialog(mainWindow.getMainFrame(),
				text,
				I18n.getMessage("thaw.warning.title"),
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);

		if (ret == JOptionPane.YES_OPTION) {
			getConfig().setValue("sameComputer", Boolean.toString(false));
			getConnectionManager().setLocalSocket(false);
			getConfigWindow().close(true, false);
			/* if we are lucky, it's enought */
		}
	}

	public void update(final Observable o, final Object target) {
		Logger.debug(this, "Move on the connection (?)");

		if ((o == connection) && !connection.isConnected()) {
			reconnect(true);
		}

		if ((o == queryManager) && target instanceof FCPMessage) {
			FCPMessage m = (FCPMessage) target;

			if ("ProtocolError".equals(m.getMessageName())) {
				int code = Integer.parseInt(m.getValue("Code"));

				if (connection.isLocalSocket()
						&& (code == 8     /* Invalid field (?!) */
						|| code == 9  /* File not found */
						|| code == 12 /* Couldn't create file */
						|| code == 13 /* Couldn't write file */
						|| code == 14 /* Couldn't rename file */
						|| code == 22 /* File parse error */
						|| code == 26 /* Could not read file */)) {
					askToDisableDDA();
				}

			}
		}
	}

	public static Random getRandom() {
		return RANDOM;
	}

	public boolean isStopping() {
		return isStopping;
	}

	/*
	 * @param major always 1 atm
	 * @param minor 5, 6, etc, depending of what you want
	 */
	public static boolean checkJavaVersion(int major, int minor) {
		String ver = System.getProperty("java.version");

		if (ver == null) {
			Logger.notice(ver, "No Jvm version ?!");
			return false;
		}

		Logger.info(ver, "JVM Version : " + ver);

		String[] version = ver.split("\\.");

		if (version.length < 2) {
			Logger.notice(ver, "Can't parse the jvm version !");
			return false;
		}

		if (Integer.parseInt(version[0]) < major)
			return false;

		if (Integer.parseInt(version[1]) < minor)
			return false;

		return true;
	}

}
