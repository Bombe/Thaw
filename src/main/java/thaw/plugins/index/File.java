package thaw.plugins.index;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import thaw.core.Logger;
import thaw.fcp.FCPClientGet;
import thaw.fcp.FCPClientPut;
import thaw.fcp.FCPQueueManager;
import thaw.fcp.FCPTransferQuery;
import thaw.fcp.FreenetURIHelper;
import thaw.plugins.Hsqldb;

public class File implements Observer, FileContainer {

	private final Hsqldb db;

	private int id = -1; /* -1 = undefined */

	private String filename = null;

	private String publicKey = null;

	private java.io.File localPath = null;

	private String mime = null;

	private long size = 0;

	private int parentId;

	private Index parent;

	/* if not null, the transfer will be removed when finished */
	private FCPQueueManager queueManager = null;

	public File(final Hsqldb db, final int id) {
		this.db = db;
		this.id = id;
		reloadDataFromDb(id);
	}

	public File(final Hsqldb db, final int id, final String filename,
				String publicKey, java.io.File localPath,
				String mime, long size, int parentId) {
		this.db = db;
		this.id = id;
		this.filename = filename;
		this.publicKey = publicKey;
		this.localPath = localPath;
		this.mime = mime;
		this.size = size;
	}

	public File(final Hsqldb db, final int id, final String filename,
				String publicKey, java.io.File localPath,
				String mime, long size, int parentId, Index parent) {
		this.db = db;
		this.id = id;
		this.filename = filename;
		this.publicKey = publicKey;
		this.localPath = localPath;
		this.mime = mime;
		this.size = size;
		this.parentId = parentId;

		this.parent = parent;
	}

	public void update(Observable o, Object param) {
		if (o instanceof FCPClientPut) {
			FCPClientPut put = (FCPClientPut) o;

			String key = put.getFileKey();

			if (FreenetURIHelper.isAKey(key)) {
				setPublicKey(key);
			}

			if (put.isFinished() && put.isSuccessful()) {
				o.deleteObserver(this);

				if (queueManager != null) {
					Logger.notice(this, "REMOVING");
					if (put.stop()) {
						queueManager.remove(put);
					}

					queueManager = null;
				}
			}

			return;
		}

		Logger.error(this, "Unknow object: " + o.toString());
	}

	public void reloadDataFromDb(int id) {
		this.id = id;

		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT filename, publicKey, localPath, mime, size, indexParent " +
						" FROM files WHERE id = ? LIMIT 1");

				st.setInt(1, id);

				ResultSet rs = st.executeQuery();

				if (rs.next()) {
					String lp;

					filename = rs.getString("filename");
					publicKey = rs.getString("publicKey");
					lp = rs.getString("localPath");
					localPath = (lp == null ? null : new java.io.File(lp));
					mime = rs.getString("mime");
					size = rs.getLong("size");
					parentId = rs.getInt("indexParent");
				} else {
					Logger.error(this, "File '" + Integer.toString(id) + "' not found");
				}

				st.close();
			}

		} catch (SQLException e) {
			Logger.error(this, "Unable to get info for file '" + Integer.toString(id) + "'");
		}
	}

	public void forceReload() {
		reloadDataFromDb(id);
	}

	public void setParent(int parent_id) {
		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("UPDATE files SET indexParent = ? " +
						"WHERE id = ?");
				st.setInt(1, parent_id);
				st.setInt(2, id);
				st.execute();

				st.close();
			}
		} catch (SQLException e) {
			Logger.error(this, "Unable to set parent: " + e.toString());
		}
	}

	public String getFilename() {
		if (filename == null)
			reloadDataFromDb(id);

		String res;

		try {
			res = java.net.URLDecoder.decode(filename, "UTF-8");
		} catch (final java.io.UnsupportedEncodingException e) {
			res = filename;
		}

		return res;
	}

	public String getMime() {
		return mime;
	}

	public long getSize() {
		return size;
	}

	public String getLocalPath() {
		if (localPath == null)
			reloadDataFromDb(id);

		if (localPath == null)
			return null;

		return localPath.getAbsolutePath();
	}

	public String getPublicKey() {
		if (publicKey == null)
			reloadDataFromDb(id);

		return publicKey;
	}

	private FCPTransferQuery transfer = null;

	public FCPTransferQuery getTransfer(FCPQueueManager q) {
		if (transfer != null)
			return transfer;

		transfer = q.getTransfer(getPublicKey());

		return transfer;
	}

	public void setPublicKey(final String publicKey) {
		this.publicKey = FreenetURIHelper.cleanURI(publicKey);

		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("UPDATE files SET publicKey = ? " +
						"WHERE id = ?");

				st.setString(1, publicKey);
				st.setInt(2, id);
				st.execute();

				st.close();
			}
		} catch (SQLException e) {
			Logger.error(this, "Unable to set publicKey: " + e.toString());
		}
	}

	public void setSize(final long size) {
		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("UPDATE files SET size = ? " +
						"WHERE id = ?");

				st.setLong(1, size);
				st.setInt(2, id);
				st.execute();

				st.close();
			}
		} catch (SQLException e) {
			Logger.error(this, "Unable to set publicKey: " + e.toString());
		}
	}

	public FCPClientPut recalculateCHK(final FCPQueueManager queueManager) {
		if (localPath == null) {
			Logger.notice(this, "Trying to recalculate key from a file where we don't have the local path");
			return null;
		}

		if (getTransfer(queueManager) != null) {
			Logger.notice(this, "Another transfer is already running for this file");
			return null;
		}

		final FCPClientPut insertion = new FCPClientPut.Builder(queueManager)
				.setLocalFile(localPath)
				.setKeyType(FCPClientPut.KEY_TYPE_CHK)
				.setRev(0)
				.setName(null)
				.setPrivateKey(null)
				.setPriority(FCPClientPut.DEFAULT_PRIORITY)
				.setGlobal(true)
				.setPersistence(FCPClientPut.PERSISTENCE_FOREVER)
				.setGetCHKOnly(true)
				.build();
		this.queueManager = queueManager; /* so the transfer will be removed when finished */
		queueManager.addQueryToTheRunningQueue(insertion);

		insertion.addObserver(this);

		return insertion;
	}

	public FCPClientGet download(final String targetPath, final FCPQueueManager queueManager) {
		FCPTransferQuery q;
		String publicKey = getPublicKey();

		if (publicKey == null) {
			Logger.notice(this, "No key !");
			return null;
		}

		if (!FreenetURIHelper.isAKey(publicKey)) {
			Logger.warning(this, "Can't start download: file key is unknown");
			return null;
		}

		if ((q = getTransfer(queueManager)) != null && q.isRunning()) {
			Logger.warning(this, "Can't download: a transfer is already running");
			return null;
		}

		final FCPClientGet clientGet = new FCPClientGet.Builder(queueManager)
				.setKey(publicKey)
				.setPriority(FCPClientGet.DEFAULT_PRIORITY)
				.setPersistence(FCPClientGet.PERSISTENCE_FOREVER)
				.setGlobalQueue(true)
				.setMaxRetries(-1)
				.setDestinationDir(targetPath)
				.build();

		queueManager.addQueryToThePendingQueue(clientGet);

		return clientGet;
	}

	public FCPClientPut insertOnFreenet(final FCPQueueManager queueManager) {
		FCPTransferQuery q;
		String localPath;

		if ((q = getTransfer(queueManager)) != null && q.isRunning()) {
			Logger.warning(this, "Another transfer is already running : can't insert");
			return null;
		}

		localPath = getLocalPath();

		if (localPath == null) {
			Logger.warning(this, "No local path => can't insert");
			return null;
		}

		FCPClientPut clientPut = new FCPClientPut.Builder(queueManager)
				.setLocalFile(new java.io.File(localPath))
				.setKeyType(FCPClientPut.KEY_TYPE_CHK)
				.setRev(0) /* EDONTCARE */
				.setPriority(FCPClientPut.DEFAULT_PRIORITY)
				.setGlobal(true)
				.setPersistence(FCPClientPut.PERSISTENCE_FOREVER)
				.setCompress(true)
				.build();
		queueManager.addQueryToThePendingQueue(clientPut);

		clientPut.addObserver(this);

		return clientPut;
	}

	public int getId() {
		return id;
	}

	public int getParentId() {
		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT indexParent FROM files " +
						"WHERE id = ? LIMIT 1");
				st.setInt(1, id);

				ResultSet rs = st.executeQuery();

				if (rs.next()) {
					int i = rs.getInt("indexParent");
					st.close();
					return i;
				} else {
					Logger.error(this, "File id not found: " + Integer.toString(id));
				}

				st.close();

			}
		} catch (SQLException e) {
			Logger.error(this, "Unable to get parent id because: " + e.toString());
		}

		return -1;
	}

	public Element getXML(final Document xmlDoc) {
		/* TODO */
		return null;
	}

	public void delete() {
		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("DELETE FROM files WHERE id = ?");
				st.setInt(1, id);
				st.execute();
				st.close();
			}
		} catch (SQLException e) {
			Logger.error(this, "Unable to remove file because: " + e.toString());
		}
	}

	/** Modifiable in the database<br/> Note: Do a SQL requests each time */
	public boolean isModifiable() {
		if (parent == null) {
			Logger.debug(this, "isModifiable() => new Index().isModifiable()");
			return (new Index(db, null, parentId)).isModifiable();
		}
		return parent.isModifiable();
	}

	/**
	 * Will browse the queue to see if one of the transfers match a file in the
	 * database
	 */
	public static boolean resumeTransfers(FCPQueueManager queue, Hsqldb db) {
		synchronized (db.dbLock) {
			PreparedStatement st;

			try {
				st = db.getConnection().prepareStatement("SELECT a.id, a.filename, a.publicKey, " +
						"a.localPath, a.mime, a.size, a.indexParent " +
						"FROM files AS a JOIN indexes AS b ON (a.indexParent = b.id)" +
						"WHERE b.privateKey IS NOT NULL AND a.filename LIKE ?");
			} catch (SQLException e) {
				Logger.error("thaw.plugin.index.File", "Error while sending query to the database : " + e.toString());
				return false;
			}

			final Vector<FCPTransferQuery> runningQueue = queue.getRunningQueue();
			for (FCPTransferQuery tq : runningQueue) {
				if (tq instanceof FCPClientPut) {
					try {
						st.setString(1, tq.getFilename());

						ResultSet rs = st.executeQuery();

						while (rs.next()) {
							File file = new File(db,
									rs.getInt("id"),
									rs.getString("filename"),
									rs.getString("publicKey"),
									rs.getString("localPath") != null ? new java.io.File(rs.getString("localPath")) : null,
									rs.getString("mime"),
									rs.getLong("size"),
									rs.getInt("indexParent"));

							((Observable) tq).addObserver(file);

							if (tq.getFileKey() != null)
								file.update(((Observable) tq), null);
						}

					} catch (SQLException e) {
						Logger.warning("thaw.plugins.index.File", "Error while resuming key computations : " + e.toString());
						return false;
					}
				}
			}

			try {
				st.close();
			} catch (SQLException e) {
				/* \_o< */
			}
		}

		return true;
	}
}
