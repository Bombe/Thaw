package thaw.plugins.webOfTrust;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;

import thaw.core.Config;
import thaw.core.Logger;
import thaw.fcp.FCPClientGet;
import thaw.fcp.FCPMetaTransferQuery;
import thaw.fcp.FCPQueryManager;
import thaw.fcp.FCPQueueManager;
import thaw.fcp.FreenetURIHelper;
import thaw.plugins.Hsqldb;
import thaw.plugins.Signatures;
import thaw.plugins.signatures.Identity;

/**
 * Will start ULPR on all the non-obsolete keys (I think I know what I'm doing)
 * and do also active polling
 *
 * @author jflesch
 */
public class TrustListDownloader implements Observer, Signatures.SignaturesObserver {

	public final static long KEY_OBSOLETE_AFTER = 2 /* week */ * 7 /* days */ * 24 /* hour */ * 60 /*min */ * 60 /* sec */ * 1000 /* ms */;

	public final static int CLIENTGET_PRIORITY = 3;

	public final static int MAX_SIZE = 102400; /* 100 KB */

	public final static int MIN_TRUSTLEVEL = 0;

	private final Hsqldb db;

	private final FCPMetaTransferQuery metaQuery;

	private Hashtable ulprs;

	private final FCPQueueManager queueManager;

	public TrustListDownloader(Hsqldb db, FCPQueueManager queueManager, Config config) {
		this.db = db;
		this.queueManager = queueManager;
		FCPQueryManager queryManager = queueManager.getQueryManager();
		this.metaQuery = new FCPMetaTransferQuery(queryManager);

		ulprs = new Hashtable();

		metaQuery.addObserver(this);

		Signatures.addObserver(this);
	}

	public boolean startULPR(String key, Identity identity) {
		WotIdentity id = new WotIdentity(identity);

		int tl = id.getTrustLevel();

		if (tl <= MIN_TRUSTLEVEL || tl == Identity.trustLevelInt[0] /* dev */)
			return false;

		Logger.notice(this, "Starting ULPR for the identity : '" + identity.toString() + "'");

		if (ulprs.get(FreenetURIHelper.getComparablePart(key)) != null) {
			Logger.notice(this, "An ulpr is already running for this key");
			return false;
		}

		if (id.currentTrustListHasAlreadyBeenDownloaded()) {
			/* increment the revision of one */
			key = FreenetURIHelper.changeUSKRevision(key, 0, 1);
		}

		FCPClientGet get = new FCPClientGet.Builder(queueManager)
				.setKey(key)
				.setPriority(CLIENTGET_PRIORITY)
				.setPersistence(FCPClientGet.PERSISTENCE_UNTIL_DISCONNECT)
				.setGlobalQueue(false)
				.setMaxRetries(-1)
				.setDestinationDir(System.getProperty("java.io.tmpdir"))
				.setMaxSize(MAX_SIZE)
				.setNoDDA(true)
				.build();
		ulprs.put(FreenetURIHelper.getComparablePart(key), get);
		metaQuery.start(get);

		return true;
	}

	public boolean stopULPR(String key) {
		FCPClientGet get = (FCPClientGet) ulprs.get(FreenetURIHelper.getComparablePart(key));
		metaQuery.stop(get);
		ulprs.remove(FreenetURIHelper.getComparablePart(key));

		return true;
	}

	public void init() {
		synchronized (db.dbLock) {
			try {
				PreparedStatement st;
				/* select all the non-obsolete keys */
				st = db.getConnection().prepareStatement("SELECT publicKey, sigId FROM wotKeys WHERE keyDate IS NULL OR keyDate >= ?");

				st.setTimestamp(1, new java.sql.Timestamp(new Date().getTime() - KEY_OBSOLETE_AFTER));

				ResultSet set = st.executeQuery();

				while (set.next()) {
					String key = set.getString("publicKey");
					int sigId = set.getInt("sigId");
					String filename = FreenetURIHelper.getFilenameFromKey(key);

					if (filename == null || "".equals(filename.trim())) {
						if (!key.endsWith("/"))
							key += "/";
						key += "trustList.xml";
					}

					Identity id = Identity.getIdentity(db, sigId);

					startULPR(key, id);
				}

				st.close();

			} catch (SQLException e) {
				Logger.error(this, "Error while starting ULPRs : " + e.toString());
				e.printStackTrace();
			}
		}
	}

	public synchronized void process() {

	}

	public void stop() {
		metaQuery.stopAll();
		ulprs = new Hashtable();
	}

	public synchronized void update(Observable o, Object param) {
		if (o == metaQuery && param instanceof FCPClientGet) {
			FCPClientGet q = (FCPClientGet) param;

			if (q.isFinished() && q.isSuccessful()) {
				/* the meta query already forgot this query, so we don't have to care of this point */
				File f = new File(q.getPath());

				WotIdentity expectedIdentity = WotIdentity.getIdentity(db, q.getFileKey());

				if (expectedIdentity == null) {
					Logger.notice(this, "can't find the identity corresponding to the wot key ... :'(");
					f.delete();
					return;
				}

				stopULPR(q.getFileKey());

				if (expectedIdentity.getTrustLevel() > 0 /* trust level may have changed during the download */
						&& expectedIdentity.doSecurityChecks(f)) {

					Logger.notice(this, "Parsing trust list of '" + expectedIdentity.toString() + "'");
					if (expectedIdentity.loadTrustList(f)) {

						expectedIdentity.updateInfos(q.getFileKey(), new java.util.Date());

						startULPR(q.getFileKey(), expectedIdentity);

					}
				}

				f.delete();
			}
		}
	}

	public void identityUpdated(Identity i) {
		if (i.getTrustLevel() < 0) {
			/* someone has been marked as BAD */
			WotIdentity id = new WotIdentity(i);
			String key;

			if ((key = id.getWoTPublicKey()) != null) {
				id.purgeTrustList();
				stopULPR(key);
			}
		} else if (i.getTrustLevel() > 0) {
			WotIdentity id = new WotIdentity(i);
			String key;
			if ((key = id.getWoTPublicKey()) != null) {
				startULPR(key, id);
			}
		}
	}

	public void privateIdentityAdded(Identity i) {

	}

	public void publicIdentityAdded(Identity i) {

	}

}
