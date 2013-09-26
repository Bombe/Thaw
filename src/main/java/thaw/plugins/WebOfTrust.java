package thaw.plugins;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;
import javax.swing.ImageIcon;

import thaw.core.Core;
import thaw.core.I18n;
import thaw.core.LibraryPlugin;
import thaw.core.Logger;
import thaw.core.ThawRunnable;
import thaw.core.ThawThread;
import thaw.fcp.FreenetURIHelper;
import thaw.gui.IconBox;
import thaw.plugins.signatures.Identity;
import thaw.plugins.webOfTrust.DatabaseManager;
import thaw.plugins.webOfTrust.TrustListDownloader;
import thaw.plugins.webOfTrust.TrustListUploader;
import thaw.plugins.webOfTrust.WebOfTrustConfigTab;

public class WebOfTrust extends LibraryPlugin {

	private Core core;

	private Hsqldb db;

	private Signatures sigs;

	private WebOfTrustConfigTab configTab = null;

	private int used = 0;

	public WebOfTrust() {
		used = 0;
	}

	private boolean loadDeps(Core core) {
		/* Hsqldb */
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

		/* Signatures */
		if (core.getPluginManager().getPlugin("thaw.plugins.Signatures") == null) {
			Logger.info(this, "Loading Signatures plugin");

			if (core.getPluginManager().loadPlugin("thaw.plugins.Signatures") == null
					|| !core.getPluginManager().runPlugin("thaw.plugins.Signatures")) {
				Logger.error(this, "Unable to load thaw.plugins.Signatures !");
				return false;
			}
		}

		sigs = (Signatures) core.getPluginManager().getPlugin("thaw.plugins.Signatures");
		sigs.registerChild(this);

		return true;
	}

	private boolean unloadDeps(Core core) {

		if (sigs != null)
			sigs.unregisterChild(this);
		if (db != null)
			db.unregisterChild(this);

		sigs = null;
		db = null;

		return true;
	}

	public ImageIcon getIcon() {
		return IconBox.trust;
	}

	public String getNameForUser() {
		return I18n.getMessage("thaw.plugin.wot");
	}

	private class TrucMucheThread implements ThawRunnable {

		private boolean running = false;

		public TrucMucheThread() {
			running = false;
		}

		public void run() {
			running = true;

			while (running) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					/* \_o< */
				}

				try {
					if (running)
						process();
				} catch (Exception e) {
					Logger.error(this, "Exception in the web of trust plugin : " + e.toString());
					e.printStackTrace();
				}
			}
		}

		public void stop() {
			running = false;
		}
	}

	private TrucMucheThread thread;

	private TrustListUploader trustListUploader = null;

	private TrustListDownloader trustListDownloader = null;

	private void initThread() {
		trustListUploader = new TrustListUploader(db, core.getQueueManager(), core.getConfig());
		trustListDownloader = new TrustListDownloader(db, core.getQueueManager(), core.getConfig());

		trustListUploader.init();
		trustListDownloader.init();

		thread = new TrucMucheThread();
		new Thread(new ThawThread(thread, "WoT refresher", this)).start();
	}

	private void process() {
		if (trustListUploader != null)
			trustListUploader.process();
		if (trustListDownloader != null)
			trustListDownloader.process();
	}

	private void stopThread() {
		if (thread != null) {
			thread.stop();
			thread = null;
		}

		if (trustListUploader != null) {
			trustListUploader.stop();
			trustListUploader = null;
		}

		if (trustListDownloader != null) {
			trustListDownloader.stop();
			trustListDownloader = null;
		}
	}

	public boolean run(Core core) {
		core.getConfig().addListener("wotActivated", this);
		core.getConfig().addListener("wotIdentityUsed", this);
		core.getConfig().addListener("wotNumberOfRefresh", this);
		core.getConfig().addListener("wotPrivateKey", this);
		core.getConfig().addListener("wotPublicKey", this);

		used++;

		this.core = core;

		if (!loadDeps(core))
			return false;

		DatabaseManager.init(db, core.getConfig(), core.getSplashScreen());

		configTab = new WebOfTrustConfigTab(core.getConfigWindow(),
				core.getConfig(), db);

		core.getConfigWindow().addTab(I18n.getMessage("thaw.plugin.wot"),
				IconBox.minTrust,
				configTab.getPanel());

		configTab.addAsObserver();

		if (core.getConfig().getValue("wotActivated") == null
				|| Boolean.valueOf(core.getConfig().getValue("wotActivated")).booleanValue()) {
			initThread();
		}

		return true;
	}

	private Identity getUsedIdentity() {
		return trustListUploader.getIdentityUsed();
	}

	private String getTrustListPublicKey() {
		String key;

		if ((key = core.getConfig().getValue("wotPublicKey")) == null)
			return null;

		return FreenetURIHelper.convertSSKtoUSK(key) + "/trustList/0/trustList.xml";
	}

	public String getTrustListPublicKeyFor(Identity id) {
		if (id.equals(getUsedIdentity()))
			return getTrustListPublicKey();
		return null;
	}

	public void addTrustList(Identity identity, String publicKey, Date dateOfTheKey) {
		if (identity.getPrivateKey() != null)
			return;

		Logger.info(this, "Adding key to the WoT ...");

		try {
			synchronized (db.dbLock) {
				PreparedStatement st = db.getConnection().prepareStatement("SELECT id, keyDate FROM wotKeys WHERE publicKey = ? OR sigId = ? LIMIT 1");
				st.setString(1, publicKey);
				st.setInt(2, identity.getId());

				ResultSet set = st.executeQuery();

				if (set.next()) {
					Timestamp date = set.getTimestamp("keyDate");
					int id = set.getInt("id");

					if (date.getTime() >= dateOfTheKey.getTime()) {
						Logger.info(this, "We already know the key => ignored");
						return;
					}

					PreparedStatement up = db.getConnection().prepareStatement("UPDATE wotKeys SET publicKey = ?, keyDate = ?, lastUpdate = ? WHERE id = ?");
					up.setString(1, publicKey);
					up.setTimestamp(2, new Timestamp(dateOfTheKey.getTime()));
					up.setNull(3, Types.TIMESTAMP);
					up.setInt(4, id);
					up.execute();

					up.close();
				} else {
					PreparedStatement in = db.getConnection().prepareStatement("INSERT INTO wotKeys (publicKey, keyDate, score, sigId) VALUES (?, ?, 0, ?)");
					in.setString(1, publicKey);
					in.setTimestamp(2, new Timestamp(dateOfTheKey.getTime()));
					in.setInt(3, identity.getId());
					in.execute();

					in.close();
				}

				st.close();
			}
		} catch (SQLException e) {
			Logger.error(this, "Error while adding a key to the list of trust list");
		}

		trustListDownloader.startULPR(publicKey, identity);
	}

	public void stop() {
		used--;

		if (configTab != null) {
			configTab.deleteAsObserver();
			core.getConfigWindow().removeTab(configTab.getPanel());
			configTab = null;
		}

		if (used == 0) {
			unloadDeps(core);
		}

		stopThread();
	}

	public void realStart() {
		used++;
	}

	public void realStop() {
		used--;

		if (used == 0)
			unloadDeps(core);
	}

}
