package thaw.plugins;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import thaw.core.Core;
import thaw.core.I18n;
import thaw.core.Logger;
import thaw.core.Plugin;
import thaw.core.ThawRunnable;
import thaw.core.ThawThread;
import thaw.fcp.FCPQueueManager;
import thaw.fcp.FCPTransferQuery;
import thaw.fcp.FreenetURIHelper;
import thaw.gui.FileChooser;
import thaw.gui.IconBox;
import thaw.plugins.transferLogs.Transfer;
import thaw.plugins.transferLogs.TransferTable;

public class TransferLogs implements Plugin, ActionListener, Observer {

	public final static byte TRANSFER_TYPE_NULL = 0;

	public final static byte TRANSFER_TYPE_DOWNLOAD = 1;

	public final static byte TRANSFER_TYPE_INSERTION = 2;

	public final static String[] TRANSFER_TYPE_NAMES = {
			I18n.getMessage("thaw.plugin.transferLogs.importedKey"),
			I18n.getMessage("thaw.common.download"),
			I18n.getMessage("thaw.common.insertion")
	};

	private Core core;

	private Hsqldb db;

	private JPanel tab;

	private JButton purgeLogs;

	private JButton importKeys;

	private JButton exportKeys;

	private TransferTable table;

	public TransferLogs() {

	}

	public boolean run(final Core core) {
		this.core = core;

		/* loading hsqldb */

		if (core.getPluginManager().getPlugin("thaw.plugins.Hsqldb") == null) {
			Logger.info(this, "Loading Hsqldb plugin");

			if (core.getPluginManager().loadPlugin("thaw.plugins.Hsqldb") == null
					|| !core.getPluginManager().runPlugin("thaw.plugins.Hsqldb")) {
				Logger.error(this, "Unable to load thaw.plugins.Hsqldb !");
				return false;
			}
		}

		db = (Hsqldb) core.getPluginManager().getPlugin("thaw.plugins.Hsqldb");
		db.registerChild(this);

		createTables();


		/* making GUI */

		tab = new JPanel(new BorderLayout(5, 5));

		JLabel topLabel = new JLabel(I18n.getMessage("thaw.plugin.transferLogs.transferLogs"));
		topLabel.setIcon(IconBox.file);

		table = new TransferTable(db, core.getConfig());

		purgeLogs = new JButton(I18n.getMessage("thaw.plugin.transferLogs.purgeLogs"),
				IconBox.minDelete);
		importKeys = new JButton(I18n.getMessage("thaw.plugin.transferLogs.importKeys"),
				IconBox.minImportAction);
		exportKeys = new JButton(I18n.getMessage("thaw.plugin.transferLogs.exportKeys"),
				IconBox.minExportAction);

		purgeLogs.addActionListener(this);
		importKeys.addActionListener(this);
		exportKeys.addActionListener(this);

		JPanel buttonPanel = new JPanel();
		buttonPanel.add(purgeLogs);
		buttonPanel.add(importKeys);
		buttonPanel.add(exportKeys);

		JPanel southPanel = new JPanel(new BorderLayout());
		southPanel.add(buttonPanel, BorderLayout.WEST);
		southPanel.add(new JLabel(""), BorderLayout.CENTER);

		tab.add(topLabel, BorderLayout.NORTH);
		tab.add(table.getPanel(), BorderLayout.CENTER);
		tab.add(southPanel, BorderLayout.SOUTH);

		setAsObserverEverywhere();

		core.getMainWindow().addTab(I18n.getMessage("thaw.plugin.transferLogs.transferLogsShort"),
				thaw.gui.IconBox.file,
				tab);

		return true;
	}

	public void stop() {
		core.getMainWindow().removeTab(tab);
		core.getQueueManager().deleteObserver(this);

		/* TODO : delete observers ! */
		/* Hm should we ? Just remove the observer on the queue should be enought ? */
		/* Others observers will just keep data sync ? */

		db.unregisterChild(this);
	}

	public String getNameForUser() {
		return I18n.getMessage("thaw.plugin.transferLogs.transferLogs");
	}

	public javax.swing.ImageIcon getIcon() {
		return thaw.gui.IconBox.file;
	}

	protected void createTables() {
		sendCreateTableQuery(db,
				"CREATE CACHED TABLE transferLogs ("
						+ "id INTEGER IDENTITY NOT NULL,"
						+ "dateStart TIMESTAMP NOT NULL,"
						+ "dateEnd TIMESTAMP,"
						+ "transferType TINYINT NOT NULL,"
						+ "key VARCHAR(500),"
						+ "filename VARCHAR(128), "
						+ "size BIGINT, " /* long */
						+ "isDup BOOLEAN NOT NULL, "
						+ "isSuccess BOOLEAN NOT NULL)");
	}

	protected boolean isDup(String key) {
		return isDup(db, key);
	}

	public static boolean isDup(Hsqldb db, String key) {
		try {
			synchronized (db.dbLock) {

				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT id FROM transferLogs " +
						"WHERE LOWER(key) LIKE ? AND isSuccess = TRUE");
				st.setString(1, FreenetURIHelper.getComparablePart(key) + "%");
				ResultSet set = st.executeQuery();

				boolean b = set.next();

				st.close();

				return b;
			}
		} catch (SQLException e) {
			Logger.error(new TransferLogs(), "Unable to know if a key is dup in the event because : " + e.toString());
		}

		return false;
	}

	/**
	 * Returns no error / Throws no exception.
	 *
	 * @return false if an exception happened
	 */
	protected static boolean sendQuery(Hsqldb db, final String query) {
		try {
			synchronized (db.dbLock) {
				db.executeQuery(query);
			}
			return true;
		} catch (final SQLException e) {
			Logger.notice(e, "While (re)creating sql tables: " + e.toString());
			return false;
		}
	}

	/** Add the current plugin as an observer on all the running query */
	public void setAsObserverEverywhere() {
		Vector<FCPTransferQuery> runningQueue = core.getQueueManager().getRunningQueue();

		for (FCPTransferQuery query : runningQueue) {
			notifyAddition(query);
		}

		core.getQueueManager().addObserver(this);
	}

	protected void notifyAddition(FCPTransferQuery query) {
		if (!query.isPersistent())
			return;

		new Transfer(db, query, table);
		table.refresh();
	}

	public void update(Observable o, Object param) {

		if (o instanceof FCPQueueManager) {
			if (param == null)
				return;

			FCPTransferQuery query = (FCPTransferQuery) param;

			if (core.getQueueManager().isInTheQueues(query)
					&& query.isRunning()) { // then it's an addition
				notifyAddition(query);
				return;
			}
		}

	}

	private File chooseFile(boolean save) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle(I18n.getMessage("thaw.plugin.transferLogs.chooseFile"));
		fileChooser.setDirectoryOnly(false);
		fileChooser.setDialogType(save ? FileChooser.SAVE_DIALOG
				: FileChooser.OPEN_DIALOG);
		return fileChooser.askOneFile();
	}

	public static java.sql.Timestamp getNow() {
		return new java.sql.Timestamp((new java.util.Date()).getTime());
	}

	private class KeyImporter implements ThawRunnable {

		public KeyImporter() {
		}

		public void run() {
			java.sql.Timestamp date = getNow();

			File file = chooseFile(false);

			if (file == null)
				return;

			try {
				FileInputStream fstream = new FileInputStream(file);

				DataInputStream in = new DataInputStream(fstream);
				BufferedReader br = new BufferedReader(new InputStreamReader(in));

				String strLine;

				synchronized (db.dbLock) {
					PreparedStatement st = null;

					try {
						st = db.getConnection().prepareStatement("INSERT INTO transferLogs " +
								"(dateStart, dateEnd, transferType," +
								" key, filename, size, isDup, isSuccess) " +
								" VALUES " +
								"(?, ?, 0, ?, ?, NULL, ?, TRUE)");
					} catch (SQLException e) {
						Logger.error(this, "Error while preparing to import keys : " + e.toString());
					}

					while ((strLine = br.readLine()) != null) {
						String key = strLine.trim();

						if (!FreenetURIHelper.isAKey(key))
							continue;

						boolean isDup = isDup(key);

						try {
							st.setTimestamp(1, date);
							st.setTimestamp(2, date);
							st.setString(3, key);
							st.setString(4, FreenetURIHelper.getFilenameFromKey(key));
							st.setBoolean(5, isDup);

							st.execute();
						} catch (SQLException e) {
							Logger.error(this, "Error while adding an event to the logs: " + e.toString());
						}
					}

					try {
						st.close();
					} catch (SQLException e) {
						/* \_o< */
					}
				}

				in.close();

			} catch (java.io.FileNotFoundException e) {
				Logger.error(this, "(1) Unable to import keys because: " + e.toString());
			} catch (java.io.IOException e) {
				Logger.error(this, "(2) Unable to import keys because: " + e.toString());
			}

			table.refresh();
		}

		public void stop() { /* \_o< */ }
	}

	private class KeyExporter implements ThawRunnable {

		public KeyExporter() {
		}

		public void run() {
			File file = chooseFile(true);

			if (file == null)
				return;

			try {
				FileOutputStream out = new FileOutputStream(file);

				synchronized (db.dbLock) {
					PreparedStatement st;

					st = db.getConnection().prepareStatement("SELECT DISTINCT key " +
							"FROM transferLogs " +
							"WHERE key is NOT NULL" +
							" AND isSuccess = TRUE");

					ResultSet set = st.executeQuery();

					while (set.next()) {
						out.write((set.getString("key") + "\n").getBytes("UTF-8"));
					}

					st.close();
				}

				out.close();

			} catch (SQLException e) {
				Logger.error(this, "Unable to export keys because: " + e.toString());
			} catch (java.io.FileNotFoundException e) {
				Logger.error(this, "(1) Unable to export keys because : " + e.toString());
			} catch (java.io.IOException e) {
				Logger.error(this, "(2) Unable to export keys because : " + e.toString());
			}

		}

		public void stop() { /* \_o< */ }
	}

	public static void dropTables(Hsqldb db) {
		sendQuery(db, "DROP TABLE transferEvents");
		sendQuery(db, "DROP TABLE transferLogs");
	}

	public void actionPerformed(ActionEvent e) {

		if (e.getSource() == purgeLogs) {
			dropTables(db);

			createTables();

			table.refresh();

			return;
		}

		if (e.getSource() == importKeys) {
			Thread th = new Thread(new ThawThread(new KeyImporter(), "Key importer", this));
			th.start();
			return;
		}

		if (e.getSource() == exportKeys) {
			Thread th = new Thread(new ThawThread(new KeyExporter(), "Key exporter", this));
			th.start();
			return;
		}
	}

	/**
	 * Given a CREATE TABLE expression, determines if the table exists. If the
	 * table does not exist, calls sendQuery(db,query).
	 */
	protected boolean sendCreateTableQuery(final Hsqldb db, final String query) {
		String tableName;

		tableName = db.getTableNameFromCreateTable(query);

		if (tableName != null) {
			if (!db.tableExists(tableName)) {
				Logger.warning(this, "Creating table " + tableName);
				return sendQuery(db, query);
			} else {
				return true;
			}
		} else {
			return false;
		}
	}
}
