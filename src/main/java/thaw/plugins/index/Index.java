package thaw.plugins.index;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;
import javax.swing.JOptionPane;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import thaw.core.Config;
import thaw.core.I18n;
import thaw.core.Logger;
import thaw.core.Main;
import thaw.core.Version;
import thaw.fcp.FCPClientGet;
import thaw.fcp.FCPClientPut;
import thaw.fcp.FCPGenerateSSK;
import thaw.fcp.FCPQueueManager;
import thaw.fcp.FCPTransferQuery;
import thaw.fcp.FreenetURIHelper;
import thaw.gui.MainWindow;
import thaw.plugins.Hsqldb;
import thaw.plugins.signatures.Identity;

public class Index extends Observable implements MutableTreeNode,
		IndexTreeNode,
		Observer,
		IndexContainer {

	private final static long MAX_SIZE = 5242880; /* 5MB */

	private final Hsqldb db;

	private int id;

	private TreeNode parentNode;

	private String publicKey = null;

	/* needed for display: */
	private String privateKey = null;

	private int rev = -1;

	private String displayName = null;

	private boolean hasChanged = false;

	private boolean newComment = false;

	private boolean publishPrivateKey = false;

	private java.sql.Date date = null;

	/* loaded only if asked explictly */
	private String realName = null;

	/* when all the comment fetching will failed,
	   loading will stop */
	public final static int COMMENT_FETCHING_RUNNING_AT_THE_SAME_TIME = 5;

	private int lastCommentRev = 0;

	private int nmbFailedCommentFetching = 0;

	private Config config;

	private boolean isNew;

	private boolean successful = true;

	private Index() {
		db = null;
	}

	public Index(Hsqldb db, Config config, int id) {
		this.db = db;
		this.config = config;
		this.id = id;
	}

	/** Use it when you can have these infos easily ; else let the index do the job */
	public Index(Hsqldb db, Config config, int id, TreeNode parentNode,
				 String publicKey, int rev, String privateKey, boolean publishPrivateKey,
				 String displayName, java.sql.Date insertionDate,
				 boolean hasChanged, boolean newComment) {
		this(db, config, id);
		this.parentNode = parentNode;
		this.privateKey = privateKey;
		this.publishPrivateKey = publishPrivateKey;
		this.publicKey = publicKey;
		this.rev = rev;
		this.displayName = displayName;
		this.date = insertionDate;
		this.hasChanged = hasChanged;
		this.newComment = newComment;
	}

	public void setIsNewFlag() {
		isNew = true;
	}

	/** Won't apply in the database ! */
	public void setId(int id) {
		this.id = id;
	}

	/** Is this node coming from the tree ? */
	public boolean isInTree() {
		return (getParent() != null);
	}

	public TreeNode getParent() {
		return parentNode;
	}

	public Enumeration children() {
		return null;
	}

	public boolean getAllowsChildren() {
		return false;
	}

	public TreeNode getChildAt(int childIndex) {
		return null;
	}

	public int getChildCount() {
		return 0;
	}

	/** relative to tree, not indexes :p */
	public int getIndex(TreeNode node) {
		return -1;
	}

	public void setParent(MutableTreeNode newParent) {
		parentNode = newParent;
		setParent(((IndexTreeNode) newParent).getId());
	}

	public void setParent(final int parentId) {
		synchronized (db.dbLock) {

			Logger.info(this, "setParent(" + Integer.toString(parentId) + ")");
			try {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("UPDATE indexes " +
						"SET parent = ? " +
						"WHERE id = ?");
				if (parentId >= 0)
					st.setInt(1, parentId);
				else
					st.setNull(1, Types.INTEGER);

				st.setInt(2, id);

				st.execute();

				st.close();

				if (parentId >= 0) {
					st = db.getConnection().prepareStatement("INSERT INTO indexParents (indexId, folderId) " +
							" SELECT ?, parentId FROM folderParents " +
							"   WHERE folderId = ?");
					st.setInt(1, id);
					st.setInt(2, parentId);

					st.execute();
					st.close();
				} /* else this parent has no parent ... :) */

				st = db.getConnection().prepareStatement("INSERT INTO indexParents (indexId, folderId) " +
						"VALUES (?, ?)");

				st.setInt(1, id);
				if (parentId >= 0)
					st.setInt(2, parentId);
				else
					st.setNull(2, Types.INTEGER);

				st.execute();

				st.close();
			} catch (SQLException e) {
				Logger.error(this, "Error while changing parent : " + e.toString());
			}

		}
	}

	/** entry point */
	public void removeFromParent() {
		Logger.info(this, "removeFromParent()");

		((IndexFolder) parentNode).remove(this);

		synchronized (db.dbLock) {

			PreparedStatement st;

			try {
				st = db.getConnection().prepareStatement("DELETE FROM indexParents " +
						"WHERE indexId = ?");
				st.setInt(1, id);
				st.execute();
				st.close();

			} catch (SQLException e) {
				Logger.error(this, "Error while removing the index: " + e.toString());
			}
		}
	}

	public void remove(int index) {
		/* nothing to do */
	}

	public void remove(MutableTreeNode node) {
		/* nothing to do */
	}

	public void insert(MutableTreeNode child, int index) {
		/* nothing to do */
	}

	public boolean isLeaf() {
		return true;
	}

	public void setUserObject(Object o) {
		rename(o.toString());
	}

	public MutableTreeNode getTreeNode() {
		return this;
	}

	public void rename(final String name) {
		synchronized (db.dbLock) {

			try {
				final Connection c = db.getConnection();
				PreparedStatement st;

				st = c.prepareStatement("UPDATE indexes SET displayName = ? WHERE id = ?");
				st.setString(1, name);
				st.setInt(2, id);
				st.execute();

				st.close();

			} catch (final SQLException e) {
				Logger.error(this, "Unable to rename the index in '" + name + "', because: " + e.toString());
			}
		}

	}

	public void delete() {
		removeFromParent();

		synchronized (db.dbLock) {

			try {

				PreparedStatement st;

				purgeFileList(false);
				purgeLinkList(false);
				purgeCommentKeys();

				st = db.getConnection().prepareStatement("DELETE FROM indexParents " +
						"WHERE indexId = ?");
				st.setInt(1, id);
				st.execute();

				st.close();

				Logger.notice(this, "DELETING AN INDEX");

				st = db.getConnection().prepareStatement("DELETE FROM indexes WHERE id = ?");
				st.setInt(1, id);
				st.execute();

				st.close();
			} catch (SQLException e) {
				Logger.error(this, "Unable to delete the index because : " + e.toString());
			}
		}
	}

	/** call purgeLinkList(false) */
	public void purgeLinkList() {
		purgeLinkList(false);
	}

	public void purgeLinkList(boolean useDontDelete) {
		synchronized (db.dbLock) {

			try {
				final Connection c = db.getConnection();
				final PreparedStatement st;

				if (!useDontDelete)
					st = c.prepareStatement("DELETE FROM links WHERE indexParent = ?");
				else
					st = c.prepareStatement("DELETE FROM links WHERE indexParent = ? " +
							"AND dontDelete = FALSE");

				st.setInt(1, getId());
				st.execute();

				st.close();
			} catch (final SQLException e) {
				Logger.error(this, "Unable to purge da list ! Exception: " + e.toString());
			}
		}
	}

	/** call purgeFileList(false) */
	public void purgeFileList() {
		purgeFileList(false);
	}

	public void purgeFileList(boolean useDontDelete) {
		synchronized (db.dbLock) {
			try {
				final Connection c = db.getConnection();
				final PreparedStatement st;

				if (!useDontDelete)
					st = c.prepareStatement("DELETE FROM files WHERE indexParent = ?");
				else
					st = c.prepareStatement("DELETE FROM files WHERE indexParent = ? AND dontDelete = FALSE");
				st.setInt(1, getId());
				st.execute();

				st.close();
			} catch (final SQLException e) {
				Logger.error(this, "Unable to purge da list ! Exception: " + e.toString());
			}
		}
	}

	public int getId() {
		return id;
	}

	public boolean loadData() {
		Logger.debug(this, "loadData()");
		synchronized (db.dbLock) {
			try {
				PreparedStatement st =
						db.getConnection().prepareStatement("SELECT publicKey, " +
								"       revision, " +
								"       privateKey, " +
								"       publishPrivateKey, " +
								"       displayName, " +
								"       newRev, " +
								"       newComment, " +
								"       insertionDate " +
								"FROM indexes " +
								"WHERE id = ? LIMIT 1");

				st.setInt(1, id);

				ResultSet set = st.executeQuery();

				if (set.next()) {
					publicKey = set.getString("publicKey");
					privateKey = set.getString("privateKey");
					publishPrivateKey = set.getBoolean("publishPrivateKey");
					rev = set.getInt("revision");
					displayName = set.getString("displayName");
					hasChanged = set.getBoolean("newRev");
					newComment = set.getBoolean("newComment");
					date = set.getDate("insertionDate");
					st.close();
					return true;
				} else {
					Logger.error(this, "Unable to find index " + Integer.toString(id) + " in the database ?!");
					st.close();
					return false;
				}
			} catch (final SQLException e) {
				Logger.error(this, "Unable to get public key because: " + e.toString());
			}
		}
		return false;
	}

	public String getPublicKey() {
		if (publicKey == null) {
			Logger.debug(this, "getPublicKey() => loadData()");
			loadData();
		}

		if (!publicKey.endsWith(".frdx"))
			return publicKey + "/" + toString(false) + ".frdx";

		return publicKey;
	}

	public java.sql.Date getDate() {
		if (publicKey == null) {
			Logger.debug(this, "getDate() => loadData()");
			loadData();
		}

		return date;
	}

	public boolean isObsolete() {
		return FreenetURIHelper.isObsolete(getPublicKey());
	}

	public int getRevision() {
		if (rev < 0) {
			Logger.debug(this, "getRevision() => loadData()");
			loadData();
		}

		return rev;
	}

	public String getPrivateKey() {
		if (publicKey == null) { /* we rely on the publicKey because the privateKey is not often availabe */
			Logger.debug(this, "getPrivateKey() => loadData()");
			loadData();
		}

		return privateKey;
	}

	public boolean publishPrivateKey() {
		if (publicKey == null) {
			Logger.debug(this, "getPrivateKey() => loadData()");
			loadData();
		}

		return publishPrivateKey;
	}

	public void setPublishPrivateKey(boolean val) {

		try {
			synchronized (db.dbLock) {
				PreparedStatement st =
						db.getConnection().prepareStatement("UPDATE indexes " +
								"SET publishPrivateKey = ? " +
								"WHERE id = ?");
				st.setBoolean(1, val);
				st.setInt(2, id);
				st.execute();
				st.close();
			}

			publishPrivateKey = val;

		} catch (SQLException e) {
			Logger.error(this, "Unable to set publishPrivateKey value because: " + e.toString());
		}
	}

	public void setPublicKey(String publicKey) {
		int rev = FreenetURIHelper.getUSKRevision(publicKey);

		setPublicKey(publicKey, rev);
	}

	/**
	 * Use directly this function only if you're sure that the rev is the same in
	 * the key
	 *
	 * @param publicKey
	 * 		must be an USK
	 */
	public void setPublicKey(String publicKey, int rev) {
		this.publicKey = publicKey;
		this.rev = rev;

		synchronized (db.dbLock) {

			try {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("UPDATE indexes " +
						"SET publicKey = ?, revision = ? " +
						"WHERE id = ?");
				st.setString(1, publicKey);
				st.setInt(2, rev);
				st.setInt(3, id);

				st.execute();
				st.close();


				/* we update also all the links in the index with the private key */

				st = db.getConnection().prepareStatement("SELECT links.id, links.publicKey " +
						"FROM LINKS JOIN INDEXES ON links.indexParent = indexes.id " +
						"WHERE indexes.privateKey IS NOT NULL AND LOWER(publicKey) LIKE ?");

				st.setString(1, FreenetURIHelper.getComparablePart(publicKey) + "%");
				ResultSet res = st.executeQuery();

				PreparedStatement updateLinkSt;

				updateLinkSt = db.getConnection().prepareStatement("UPDATE links SET publicKey = ? WHERE id = ?");

				while (res.next()) {
					String pubKey = res.getString("publicKey").replaceAll(".xml", ".frdx");

					if (FreenetURIHelper.compareKeys(pubKey, publicKey)) {
						updateLinkSt.setString(1, publicKey);
						updateLinkSt.setInt(2, res.getInt("id"));
						updateLinkSt.execute();
					}
				}

				st.close();
				updateLinkSt.close();

			} catch (SQLException e) {
				Logger.error(this, "Unable to set public Key because: " + e.toString());
			}
		}
	}

	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
		setPrivateKey(db, id, privateKey);
	}

	public static void setPrivateKey(Hsqldb db, int indexId, String privateKey) {
		if (privateKey != null && !FreenetURIHelper.isAKey(privateKey))
			privateKey = null;

		synchronized (db.dbLock) {
			try {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("UPDATE indexes " +
						"SET privateKey = ? " +
						"WHERE id = ?");
				if (privateKey != null)
					st.setString(1, privateKey);
				else
					st.setNull(1, Types.VARCHAR);
				st.setInt(2, indexId);

				st.execute();
				st.close();
			} catch (SQLException e) {
				Logger.error(new Index(), "Unable to set private Key because: " + e.toString());
			}
		}
	}

	public String getRealName() {
		if (realName != null)
			return realName;

		synchronized (db.dbLock) {
			try {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT originalName FROM indexes WHERE id = ?");

				st.setInt(1, id);
				ResultSet set = st.executeQuery();

				if (set.next()) {
					realName = set.getString("originalName");
					st.close();
					return realName;
				} else {
					Logger.error(this, "Unable to get index real name: not found");
					st.close();
					return null;
				}
			} catch (SQLException e) {
				Logger.error(this, "Unable to get real index name: " + e.toString());
			}
		}

		return null;
	}

	public String toString() {
		return toString(true);
	}

	public String toString(boolean withRev) {
		if (displayName == null || rev < 0) {
			Logger.debug(this, "toString() => loadData()");
			loadData();
		}

		if (withRev) {
			if (rev > 0 || (rev == 0 && privateKey == null))
				return displayName + " (r" + Integer.toString(rev) + ")";
			else {
				if (rev > 0)
					return displayName + " [" + I18n.getMessage("thaw.plugin.index.nonInserted") + "]";
				else
					return displayName;
			}
		} else
			return displayName;

	}

	private IndexTree indexTree = null;

	public int insertOnFreenet(Observer o, IndexBrowserPanel indexBrowser, FCPQueueManager queueManager) {
		String privateKey = getPrivateKey();
		String publicKey = getPublicKey();
		int rev = getRevision();

		if (indexBrowser != null && indexBrowser.getMainWindow() != null) {
			indexTree = indexBrowser.getIndexTree();

			synchronized (db.dbLock) {
				try {
					PreparedStatement st;

					st = db.getConnection().prepareStatement("SELECT id FROM links where indexParent = ? LIMIT 1");
					st.setInt(1, id);

					ResultSet set = st.executeQuery();

					if (!set.next()) {
						st.close();

						/* no link ?! we will warn the user */

						int ret =
								JOptionPane.showOptionDialog(indexBrowser.getMainWindow().getMainFrame(),
										I18n.getMessage("thaw.plugin.index.indexWithNoLink").replaceAll("\\?", toString(false)),
										I18n.getMessage("thaw.warning.title"),
										JOptionPane.YES_NO_OPTION,
										JOptionPane.WARNING_MESSAGE,
										null,
										null,
										null);

						if (ret == JOptionPane.CLOSED_OPTION
								|| ret == JOptionPane.NO_OPTION) {
							return 0;
						}
					} else
						st.close();

				} catch (SQLException e) {
					Logger.error(this, "Error while checking the link number before insertion : " + e.toString());
				}
			}

			if (getCategory() == null) {
				int ret =
						JOptionPane.showOptionDialog(indexBrowser.getMainWindow().getMainFrame(),
								I18n.getMessage("thaw.plugin.index.noCategory").replaceAll("\\?", toString(false)),
								I18n.getMessage("thaw.warning.title"),
								JOptionPane.YES_NO_OPTION,
								JOptionPane.WARNING_MESSAGE,
								null,
								null,
								null);

				if (ret == JOptionPane.CLOSED_OPTION
						|| ret == JOptionPane.NO_OPTION) {
					return 0;
				}
			}

		}


		/* Let's hope that users are not stupid
		 * and won't insert too much revisions at once. */
		/*
		if (indexBrowser != null && indexBrowser.getIndexTree() != null
		    && indexBrowser.getIndexTree().isIndexUpdating(this)) {
			Logger.notice(this, "A transfer is already running !");
			return 0;
		}
		*/

		if (!FreenetURIHelper.isAKey(publicKey)
				|| !FreenetURIHelper.isAKey(privateKey)) { /* non modifiable */
			Logger.notice(this, "Tried to insert an index for which we don't have the private key ...");
			return 0;
		}

		String tmpdir = System.getProperty("java.io.tmpdir");

		if (tmpdir == null)
			tmpdir = "";
		else
			tmpdir = tmpdir + java.io.File.separator;

		java.io.File targetFile = new java.io.File(tmpdir + getRealName() + ".frdx");

		Logger.info(this, "Generating index ...");

		IndexParser parser = new IndexParser(this);

		if (!parser.generateXML(targetFile.getAbsolutePath()))
			return 0;

		FCPClientPut put;

		if (targetFile.exists()) {
			Logger.info(this, "Inserting new version");

			String key = FreenetURIHelper.changeUSKRevision(publicKey, rev, 1);

			rev++;

			put = new FCPClientPut.Builder(queueManager)
					.setLocalFile(targetFile)
					.setKeyType(FCPClientPut.KEY_TYPE_SSK)
					.setRev(rev)
					.setName(realName)
					.setPrivateKey(privateKey)
					.setPriority(2)
					.setGlobal(true)
					.setPersistence(FCPClientPut.PERSISTENCE_FOREVER)
					.setCompress(true)
					.build();

			put.setMetadata("ContentType", "application/x-freenet-index");

			if (indexBrowser != null && indexBrowser.getIndexTree() != null)
				indexBrowser.getIndexTree().addUpdatingIndex(this);

			queueManager.addQueryToTheRunningQueue(put);

			put.addObserver(this);
			this.addObserver(o);

			setPublicKey(key, rev);

			try {
				synchronized (db.dbLock) {
					PreparedStatement st;

					String query = "UPDATE # SET dontDelete = FALSE WHERE indexParent = ?";

					st = db.getConnection().prepareStatement(query.replaceFirst("#", "files"));
					st.setInt(1, id);
					st.execute();
					st.close();

					st = db.getConnection().prepareStatement(query.replaceFirst("#", "links"));
					st.setInt(1, id);
					st.execute();

					st.close();
				}

			} catch (SQLException e) {
				Logger.error(this, "Error while reseting dontDelete flags: " + e.toString());
			}

		} else {
			Logger.warning(this, "Index not generated !");
			return 0;
		}

		return 1;
	}

	public int downloadFromFreenet(Observer o, IndexTree tree, FCPQueueManager queueManager) {
		return downloadFromFreenet(o, tree, queueManager, -1);
	}

	/**
	 * if true, when the transfer will finish, the index public key will be
	 * updated
	 */
	private boolean rewriteKey = true;

	private FCPQueueManager queueManager;

	private boolean fetchingNegRev = false;

	private boolean mustFetchNegRev = false;

	public int downloadFromFreenet(Observer o, IndexTree tree, FCPQueueManager queueManager, int specificRev) {
		this.queueManager = queueManager;
		indexTree = tree;
		rewriteKey = true;

		fetchingNegRev = false;
		mustFetchNegRev = false;

		if (config != null && config.getValue("indexFetchNegative") != null)
			mustFetchNegRev = Boolean.valueOf(config.getValue("indexFetchNegative")).booleanValue();

		boolean v = realDownloadFromFreenet(specificRev);

		if (o != null)
			this.addObserver(o);

		return (v ? 1 : 0);
	}

	protected boolean realDownloadFromFreenet(int specificRev) {
		FCPClientGet clientGet;
		String publicKey;

		int rev = getRevision();

		publicKey = getPublicKey();
		String privateKey = getPrivateKey();

		if (rev <= 0 && privateKey != null) {
			Logger.error(this, "Can't update an non-inserted index !");
			return false;
		}

		if (indexTree != null && indexTree.isIndexUpdating(this)) {
			Logger.notice(this, "A transfer is already running !");
			return false;
		}

		if (publicKey == null) {
			Logger.error(this, "No public key !! Can't get the index !");
			return false;
		}

		String key;

		if (specificRev >= 0) {
			key = FreenetURIHelper.convertUSKtoSSK(publicKey);
			key = FreenetURIHelper.changeSSKRevision(key, specificRev, 0);
			rewriteKey = false;
		} else {
			key = publicKey;
			rewriteKey = true;
		}

		if (rev < 0)
			rewriteKey = false;

		Logger.info(this, "Updating index ...");

		if (key.startsWith("USK")) {
			int daRev = FreenetURIHelper.getUSKRevision(key);

			if ((fetchingNegRev && daRev > 0)
					|| (!fetchingNegRev && daRev < 0)) {
				daRev = -1 * daRev;
				key = FreenetURIHelper.changeUSKRevision(key, daRev, 0);
			}
		}

		Logger.debug(this, "Key asked: " + key);

		clientGet = new FCPClientGet.Builder(queueManager)
				.setKey(key)
				.setPriority(2)
				.setPersistence(FCPClientGet.PERSISTENCE_UNTIL_DISCONNECT)
				.setGlobalQueue(false)
				.setMaxRetries(10)
				.setDestinationDir(System.getProperty("java.io.tmpdir"))
				.setMaxSize(MAX_SIZE)
				.setNoDDA(true)
				.build();

		/*
		 * These requests are usually quite fast, and don't consume too much
		 * of bandwidth / CPU. So we can skip the queue and start immediately
		 * (and like this, they won't appear in the queue)
		 */
		clientGet.start();

		if (indexTree != null)
			indexTree.addUpdatingIndex(this);

		clientGet.addObserver(this);

		return true;
	}

	public void useTrayIconToNotifyNewRev() {
		if (indexTree == null)
			return;

		String announcement = I18n.getMessage("thaw.plugin.index.newRev");

		try {
			announcement = announcement.replaceAll("X", toString(false));
			announcement = announcement.replaceAll("Y", Integer.toString(getRevision()));

			thaw.plugins.TrayIcon.popMessage(indexTree.getIndexBrowserPanel().getCore().getPluginManager(),
					I18n.getMessage("thaw.plugin.index.browser"),
					announcement);
		} catch (Exception e) {
			/* it can happen with some index name (probably because
			   of characters like '$' */
			Logger.notice(this, "Can't popup to notify an index update because : " + e.toString());
		}
	}

	public void update(Observable o, Object param) {
		if (o instanceof FCPClientGet) {
			FCPClientGet get = (FCPClientGet) o;

			if (get.isFinished()) {
				get.deleteObserver(this);

				if (get.isSuccessful()) {
					successful = true;

					String key = get.getFileKey();

					int oldRev = rev;
					int newRev = FreenetURIHelper.getUSKRevision(key);

					if (rewriteKey) {
						setPublicKey(key, newRev);
					}

					if (oldRev < newRev || isNew) {
						setHasChangedFlag(true);
						useTrayIconToNotifyNewRev();
					}

					isNew = false;

					String path = get.getPath();

					if (path != null) {
						IndexParser parser = new IndexParser(this);

						parser.loadXML(path);

						if (!fetchingNegRev && mustFetchNegRev
								&& getCommentPublicKey() != null) {
							final java.io.File fl = new java.io.File(path);
							fl.delete();

							setChanged();
							notifyObservers();

							fetchingNegRev = true;
							realDownloadFromFreenet(-1);
							return;
						}

						boolean loadComm = true;

						if (config != null && config.getValue("indexFetchComments") != null)
							loadComm = Boolean.valueOf(config.getValue("indexFetchComments")).booleanValue();

						if (getCommentPublicKey() != null && loadComm) {
							loadComments(queueManager);
						} else if (indexTree != null)
							indexTree.removeUpdatingIndex(this);
					} else
						Logger.warning(this, "No path specified in transfer ?!");
				} else { /* if not successful */
					Logger.warning(this, "Download of index " + this.toString() + " failed");
					successful = false;
					indexTree.removeUpdatingIndex(this);
				}
			}
		}

		if (o instanceof FCPClientPut) {
			FCPClientPut put = ((FCPClientPut) o);

			/* TODO : check if it's successful, else merge if it's due to a collision */
			if (put.isFinished()) {
				put.deleteObserver(this);

				if (indexTree != null)
					indexTree.removeUpdatingIndex(this);

				if (put.isSuccessful()) {
					try {
						synchronized (db.dbLock) {
							PreparedStatement st;

							Calendar cal = Calendar.getInstance();
							java.sql.Date dateSql = new java.sql.Date(cal.getTime().getTime());

							st = db.getConnection().prepareStatement("UPDATE indexes " +
									"SET insertionDate = ? " +
									"WHERE id = ?");
							st.setDate(1, dateSql);
							st.setInt(2, id);

							st.execute();
							st.close();
						}
					} catch (SQLException e) {
						Logger.error(this, "Error while updating the insertion date : " + e.toString());
					}
				} else {
					if (put.getRevision() == rev)
						setPublicKey(publicKey, rev - 1);
				}
			}
		}

		if (o instanceof Comment) {

			Comment c = (Comment) o;

			if (c.exists()) {
				nmbFailedCommentFetching = 0;

				if (c.isNew() && c.isValid()) {
					Logger.info(this, "New comment !");

					setNewCommentFlag(true);

					setChanged();
					notifyObservers();
				}

			} else {
				nmbFailedCommentFetching++;
			}

			c.deleteObserver(this);

			if (nmbFailedCommentFetching > COMMENT_FETCHING_RUNNING_AT_THE_SAME_TIME + 1) {
				if (indexTree != null) {
					Logger.info(this, "All the comments should be fetched");
					indexTree.removeUpdatingIndex(this);
				}
			} else {
				lastCommentRev++;
				Comment comment = new Comment(db, this, lastCommentRev, null, null);
				comment.addObserver(this);
				comment.fetchComment(queueManager);
			}

		}

		if (o instanceof FCPTransferQuery) {
			FCPTransferQuery transfer = (FCPTransferQuery) o;

			if (transfer.isFinished()) {
				String path = transfer.getPath();

				final java.io.File fl = new java.io.File(path);
				fl.delete();

				setChanged();
				notifyObservers();
			}
		}

	}

	/** call purgeIndex(true) */
	public void purgeIndex() {
		purgeIndex(true);
	}

	public void purgeIndex(boolean useDontDelete) {
		purgeFileList(useDontDelete);
		purgeLinkList(useDontDelete);
		if (!useDontDelete)
			purgeCommentKeys();
	}

	public void setInsertionDate(java.util.Date date) {
		try {
			synchronized (db.dbLock) {
				java.sql.Date dateSql = null;
				dateSql = new java.sql.Date(date.getTime());

				PreparedStatement st =
						db.getConnection().prepareStatement("UPDATE indexes " +
								"SET insertionDate = ? " +
								"WHERE id = ?");
				st.setDate(1, dateSql);
				st.setInt(2, id);

				st.execute();
				st.close();
			}
		} catch (SQLException e) {
			Logger.error(this, "Error while updating index insertion date: " + e.toString());
		}
	}

	////// Comments black list //////
	public Vector getCommentBlacklistedRev() {
		Vector v = new Vector();

		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT rev " +
						"FROM indexCommentBlackList " +
						"WHERE indexId = ?");
				st.setInt(1, id);

				ResultSet set = st.executeQuery();

				while (set.next()) {
					v.add(new Integer(set.getInt("rev")));
				}

				st.close();
			}
		} catch (SQLException e) {
			Logger.error(this, "Unable to get comment black list  because: " + e.toString());
		}

		return v;
	}

	public void addBlackListedRev(int rev) {
		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("INSERT into indexCommentBlackList (rev, indexId) VALUES (?, ?)");
				st.setInt(1, rev);
				st.setInt(2, id);

				st.execute();
				st.close();
			}
		} catch (SQLException e) {
			Logger.error(this, "Error while adding element to the blackList: " + e.toString());
		}

	}

	////// FILE LIST ////////

	public File[] getFileList() {
		return getFileList(null, false);
	}

	public File[] getFileList(String columnToSort, boolean asc) {
		synchronized (db.dbLock) {

			try {
				java.util.LinkedList files = new java.util.LinkedList();

				PreparedStatement st;

				if (columnToSort == null) {
					st = db.getConnection().prepareStatement("SELECT id, filename, publicKey, localPath, mime, size " +
							"FROM files WHERE indexParent = ?");
				} else {
					st = db.getConnection().prepareStatement("SELECT id, filename, publicKey, localPath, mime, size " +
							"FROM files WHERE indexParent = ? ORDER by " +
							columnToSort + (asc == true ? "" : " DESC"));
				}

				st.setInt(1, id);

				ResultSet rs = st.executeQuery();

				while (rs.next()) {
					int file_id = rs.getInt("id");
					String filename = rs.getString("filename");
					String file_publicKey = rs.getString("publicKey");
					String lp = rs.getString("localPath");
					java.io.File localPath = (lp == null ? null : new java.io.File(lp));
					String mime = rs.getString("mime");
					long size = rs.getLong("size");

					thaw.plugins.index.File file =
							new thaw.plugins.index.File(db, file_id, filename, file_publicKey,
									localPath, mime, size, id, this);
					files.add(file);
				}

				st.close();

				return (File[]) files.toArray(new File[0]);

			} catch (SQLException e) {
				Logger.error(this, "SQLException while getting file list: " + e.toString());
			}
		}
		return null;
	}

	public boolean addFile(String key, long size, String mime) {
		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("INSERT INTO files "
						+ "(filename, publicKey, localPath, mime, size, category, indexParent) "
						+ "VALUES (?, ?, NULL, ?, ?, NULL, ?)");

				String filename = FreenetURIHelper.getFilenameFromKey(key);
				if (filename == null)
					filename = key;

				st.setString(1, filename);
				st.setString(2, key);
				st.setString(3, mime);
				st.setLong(4, size);
				st.setInt(5, id);

				st.execute();
				st.close();

				return true;
			}
		} catch (SQLException e) {
			Logger.error(this, "Error while adding file to index '" + toString() + "' : " + e.toString());
		}

		return false;
	}

	//// LINKS ////

	public Link[] getLinkList() {
		return getLinkList(null, false);
	}

	public Link[] getLinkList(String columnToSort, boolean asc) {
		synchronized (db.dbLock) {

			try {
				java.util.LinkedList links = new java.util.LinkedList();

				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT links.id AS id, " +
						" links.publicKey AS publicKey, " +
						" links.blackListed AS blacklisted," +
						" links.indexParent AS indexParent, " +
						" categories.name AS categoryName " +
						" FROM links LEFT OUTER JOIN categories " +
						" ON links.category = categories.id " +
						"WHERE links.indexParent = ?");

				st.setInt(1, id);

				ResultSet res = st.executeQuery();

				while (res.next()) {
					Link l = new Link(db, res.getInt("id"), res.getString("publicKey"),
							res.getString("categoryName"), res.getBoolean("blackListed"),
							this);
					links.add(l);
				}

				st.close();

				return (Link[]) links.toArray(new Link[0]);

			} catch (SQLException e) {
				Logger.error(this, "SQLException while getting link list: " + e.toString());
			}
		}

		return null;
	}

	public static String getNameFromKey(final String key) {
		String name = null;

		name = FreenetURIHelper.getFilenameFromKey(key);

		if (name == null)
			return null;

		/* quick and dirty */
		name = name.replaceAll(".xml", "");
		name = name.replaceAll(".frdx", "");

		return name;
	}

	public boolean addLink(String key, String category) {
		try {
			if (key == null) /* it was the beginning of the index */
				return true;

			key = key.trim();

			boolean blackListed = (BlackList.isBlackListed(db, key) >= 0);

			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("INSERT INTO links " +
						"(publicKey, mark, comment, " +
						"indexParent, indexTarget, blackListed, category) " +
						"VALUES (?, 0, ?, ?, NULL, ?, ?)");
				st.setString(1, key);
				st.setString(2, "No comment"); /* comment not used at the moment */
				st.setInt(3, id);
				st.setBoolean(4, blackListed);

				if (category != null)
					st.setInt(5, getCategoryId(category));
				else
					st.setNull(5, Types.INTEGER);

				st.execute();
				st.close();

				return true;
			}
		} catch (SQLException e) {
			Logger.error(this, "Error while adding link to index '" + toString() + "' : " + e.toString());
		}

		return false;
	}

	public String findTheLatestKey(String linkKey) {
		synchronized (db.dbLock) {
			try {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT publicKey, revision " +
						"FROM indexes " +
						"WHERE publicKey LIKE ?");

				st.setString(1, FreenetURIHelper.getComparablePart(linkKey));

				ResultSet set = st.executeQuery();

				while (set.next()) {
					/* we will assume that we have *always* the latest version of the index */

					String oKey = set.getString("publicKey");
					if (FreenetURIHelper.compareKeys(oKey, linkKey)) {
						String key = FreenetURIHelper.changeUSKRevision(oKey,
								set.getInt("revision"),
								0);
						st.close();
						return key;
					}
				}

				st.close();
			} catch (SQLException e) {
				Logger.error(this, "Can't find the latest key of a link because : " + e.toString());
			}
		}

		return linkKey;
	}

	public boolean isModifiable() {
		if (getPrivateKey() != null)
			return true;

		return false;
	}

	public static int isAlreadyKnown(Hsqldb db, String key) {
		return isAlreadyKnown(db, key, false);
	}

	/** @return the index id if found ; -1 else */
	public static int isAlreadyKnown(Hsqldb db, String key, boolean strict) {
		if (key.length() < 40) {
			Logger.error(new Index(), "isAlreadyKnown(): Invalid key: " + key);
			return -1;
		}

		key = key.replaceAll(".xml", ".frdx");

		synchronized (db.dbLock) {
			try {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT id, publicKey from indexes WHERE LOWER(publicKey) LIKE ?"
						+ (strict ? "" : " LIMIT 1"));

				st.setString(1, FreenetURIHelper.getComparablePart(key) + "%");

				ResultSet res = st.executeQuery();

				if (strict) {
					while (res.next()) {
						String pubKey = res.getString("publicKey").replaceAll(".xml", ".frdx");

						if (FreenetURIHelper.compareKeys(pubKey, key)) {
							int r = res.getInt("id");
							st.close();
							return r;
						}
					}

					st.close();
					return -1;
				} else {
					if (!res.next()) {
						st.close();
						return -1;
					}

					int r = res.getInt("id");
					st.close();
					return r;
				}

			} catch (final SQLException e) {
				Logger.error(new Index(), "isAlreadyKnown: Unable to check if link '" + key + "' point to a know index because: " + e.toString());
			}
		}

		return -1;
	}

	public void forceFlagsReload() {
		Logger.verbose(this, "forceReload() => loadData()");
		loadData();
	}

	public boolean hasChanged() {
		if (publicKey == null) {
			Logger.debug(this, "hasChanged() => loadData()");
			loadData();
		}

		return hasChanged;
	}

	public boolean hasNewComment() {
		if (publicKey == null) {
			Logger.debug(this, "hasNewComment() => loadData()");
			loadData();
		}

		return newComment;
	}

	public boolean setHasChangedFlagInMem(boolean flag) {
		hasChanged = flag;
		return true;
	}

	public boolean setNewCommentFlagInMem(boolean flag) {
		newComment = flag;
		return true;
	}

	/** @return true if a change was done */
	public boolean setHasChangedFlag(boolean flag) {
		setHasChangedFlagInMem(flag);

		synchronized (db.dbLock) {
			try {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("UPDATE indexes SET newRev = ? " +
						"WHERE id = ?");

				st.setBoolean(1, flag);
				st.setInt(2, id);
				if (st.executeUpdate() > 0) {
					st.close();
					return true;
				}

				st.close();
				return false;
			} catch (SQLException e) {
				Logger.error(this, "Unable to change 'hasChanged' flag because: " + e.toString());
			}
		}

		return false;
	}

	/** @return true if a change was done */
	public boolean setNewCommentFlag(boolean flag) {
		setNewCommentFlagInMem(flag);

		synchronized (db.dbLock) {
			try {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("UPDATE indexes SET newComment = ? " +
						"WHERE id = ?");

				st.setBoolean(1, flag);
				st.setInt(2, id);
				if (st.executeUpdate() > 0) {
					st.close();
					return true;
				}

				st.close();
				return false;
			} catch (SQLException e) {
				Logger.error(this, "Unable to change 'newComment' flag because: " + e.toString());
			}
		}

		return false;
	}

	public Element do_export(Document xmlDoc, boolean withContent) {
		Element e = xmlDoc.createElement("fullIndex");

		e.setAttribute("displayName", toString(false));
		e.setAttribute("publicKey", getPublicKey());
		if (getPrivateKey() != null)
			e.setAttribute("privateKey", getPrivateKey());

		if (withContent) {
			new IndexParser(this).fillInRootElement(e, xmlDoc);
		}

		return e;
	}

	public boolean equals(Object o) {
		if (o == null || !(o instanceof Index))
			return false;

		if (((Index) o).getId() == getId())
			return true;
		return false;
	}

	/** @return an SSK@ */
	public String getCommentPublicKey() {
		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT publicKey FROM indexCommentKeys WHERE indexId = ? LIMIT 1");
				st.setInt(1, id);

				ResultSet set = st.executeQuery();

				String r = null;

				if (set != null && set.next()) {
					r = set.getString("publicKey");
				}

				st.close();

				return r;
			}
		} catch (SQLException e) {
			Logger.error(this, "Unable to get comment public key because : " + e.toString());
		}

		return null;
	}

	/** @return an SSK@ */
	public String getCommentPrivateKey() {
		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT privateKey FROM indexCommentKeys WHERE indexId = ? LIMIT 1");
				st.setInt(1, id);

				ResultSet set = st.executeQuery();

				String r = null;

				if (set != null && set.next())
					r = set.getString("privateKey");

				st.close();

				return r;
			}
		} catch (SQLException e) {
			Logger.error(this, "Unable to get comment public key because : " + e.toString());
		}

		return null;
	}

	/** Will also purge comments ! */
	public void purgeCommentKeys() {
		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("DELETE FROM indexCommentBlackList WHERE indexId = ?");
				st.setInt(1, id);
				st.execute();
				st.close();

				st = db.getConnection().prepareStatement("DELETE FROM indexComments WHERE indexId = ?");
				st.setInt(1, id);
				st.execute();
				st.close();

				st = db.getConnection().prepareStatement("DELETE FROM indexCommentKeys WHERE indexId = ?");
				st.setInt(1, id);
				st.execute();
				st.close();

			}
		} catch (SQLException e) {
			Logger.error(this, "Unable to purge comment keys, because : " + e.toString());
		}
	}

	/** will reset the comments ! */
	public void setCommentKeys(String publicKey, String privateKey) {
		String oldPubKey = getCommentPublicKey();
		String oldPrivKey = getCommentPrivateKey();

		if (((publicKey == null && oldPubKey == null)
				|| (publicKey != null && publicKey.equals(oldPubKey)))
				&&
				((privateKey == null && oldPrivKey == null)
						|| (privateKey != null && privateKey.equals(oldPrivKey))))
			return; /* same keys => no change */

		purgeCommentKeys();

		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("INSERT INTO indexCommentKeys (publicKey, privateKey, indexId) VALUES (?, ?, ?)");
				st.setString(1, publicKey);
				st.setString(2, privateKey);
				st.setInt(3, id);

				st.execute();
				st.close();
			}
		} catch (SQLException e) {
			Logger.error(this, "Unable to set comment keys, because : " + e.toString());
		}
	}

	protected class CommentKeyRegenerator implements Observer {

		private FCPGenerateSSK sskGenerator;

		public CommentKeyRegenerator(FCPQueueManager queueManager) {
			sskGenerator = new FCPGenerateSSK(queueManager);
			sskGenerator.addObserver(this);

			sskGenerator.start();
		}

		public void update(Observable o, Object param) {
			if (o instanceof FCPGenerateSSK) {
				setCommentKeys(((FCPGenerateSSK) o).getPublicKey(),
						((FCPGenerateSSK) o).getPrivateKey());
			}
		}

	}

	public void regenerateCommentKeys(FCPQueueManager queueManager) {
		new CommentKeyRegenerator(queueManager);
	}

	/** @return true if the public key to fetch the comments is available */
	public boolean canHaveComments() {
		return (getCommentPublicKey() != null);
	}

	public boolean postComment(FCPQueueManager queueManager, MainWindow mainWindow, Identity author, String msg) {
		if (getCommentPrivateKey() == null) {
			return false;
		}

		Comment comment = new Comment(db, this, -1, author, msg);

		return comment.insertComment(queueManager, mainWindow);
	}

	public void loadComments(FCPQueueManager queueManager) {
		if (getCommentPublicKey() == null)
			return;

		if (queueManager == null) {
			Logger.warning(this, "Can't load comments ! QueueManager is not set for this index !");
			return;
		}

		for (lastCommentRev = 0;
			 lastCommentRev < COMMENT_FETCHING_RUNNING_AT_THE_SAME_TIME;
			 lastCommentRev++) {
			Comment comment = new Comment(db, this, lastCommentRev, null, null);
			comment.addObserver(this);
			comment.fetchComment(queueManager);
		}
	}

	public Vector getComments() {
		return getComments(true);
	}

	public Vector getComments(boolean asc) {

		try {
			synchronized (db.dbLock) {
				Vector comments = new Vector();

				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT authorId, text, rev " +
						"FROM indexComments WHERE indexId = ? ORDER BY rev" +
						(asc ? "" : " DESC"));

				st.setInt(1, id);

				ResultSet set = st.executeQuery();

				while (set.next())
					comments.add(new Comment(db, this,
							set.getInt("rev"),
							Identity.getIdentity(db, set.getInt("authorId")),
							set.getString("text")));

				st.close();

				if (comments.size() == 0)
					Logger.notice(this, "No comment for this index");
				else
					Logger.info(this, Integer.toString(comments.size()) + " comments for this index");

				return comments;
			}
		} catch (SQLException e) {
			Logger.error(this, "Error while fetching comment list : " + e.toString());
		}

		return null;
	}

	public int getNmbComments() {

		try {
			int nmb = 0;

			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT count(indexComments.id) " +
						"FROM indexComments " +
						"WHERE indexComments.indexId = ? " +
						"AND indexComments.rev NOT IN " +
						" (SELECT indexCommentBlackList.rev " +
						"  FROM indexCommentBlackList " +
						"  WHERE indexCommentBlackList.indexId = ?)");

				st.setInt(1, id);
				st.setInt(2, id);

				ResultSet set = st.executeQuery();

				if (set.next())
					nmb = set.getInt(1);

				st.close();

				return nmb;
			}
		} catch (SQLException e) {
			Logger.error(this, "Error while fetching comment list : " + e.toString());
		}

		return 0;
	}

	/* The user who is able to have so much depth in its tree
	 * is crazy.
	 */
	public final static int MAX_DEPTH = 128;

	public TreePath getTreePath(IndexTree tree) {

		int[] folderIds = new int[MAX_DEPTH];

		for (int i = 0; i < folderIds.length; i++)
			folderIds[i] = -1;

		synchronized (db.dbLock) {
			try {
				/* we find the id of the parents */

				PreparedStatement st = db.getConnection().prepareStatement("SELECT folderId FROM indexParents " +
						"WHERE indexId = ? LIMIT 1");
				st.setInt(1, id);
				ResultSet res = st.executeQuery();

				if (!res.next()) {
					Logger.error(this, "Can't find the index " + Integer.toString(id) + "in the db! The tree is probably broken !");
					st.close();
					return null;
				}

				int i = 0;

				do {
					int j = res.getInt("folderId");

					if (j != 0) /* root */
						folderIds[i] = j;

					i++;
				} while (res.next());

				st.close();

				int nmb_folders = i + 1; /* i + root */

				Object[] path = new Object[nmb_folders + 1]; /* folders + the index */

				for (i = 0; i < path.length; i++)
					path[i] = null;

				path[0] = indexTree.getRoot();

				for (i = 1; i < nmb_folders; i++) {
					IndexFolder folder = null;

					for (int j = 0;
						 folder == null && j < folderIds.length && folderIds[j] != -1;
						 j++) {

						folder = ((IndexFolder) path[i - 1]).getChildFolder(folderIds[j], false);

					}

					if (folder == null)
						break;

					path[i] = folder;
				}

				if (i >= 2)
					path[i - 1] = ((IndexFolder) path[i - 2]).getChildIndex(id, false);
				else
					path[1] = indexTree.getRoot().getChildIndex(id, false);

				int non_null_elements = 0;
				/* we may have null elements if the tree wasn't fully loaded for this path */
				for (i = 0; i < path.length; i++) {
					if (path[i] == null)
						break;
				}

				non_null_elements = i;

				if (non_null_elements != nmb_folders) {
					/* we eliminate the null elements */
					Object[] new_path = new Object[non_null_elements];

					for (i = 0; i < non_null_elements; i++)
						new_path[i] = path[i];

					path = new_path;
				}

				return new TreePath(path);

			} catch (SQLException e) {
				Logger.error(this, "Error while getting index tree path : " + e.toString());
			}
		}

		return null;
	}

	public String getClientVersion() {
		return ("Thaw " + Main.getVersion());
	}

	/** @return -1 if none */
	public int getCategoryId() {
		try {
			synchronized (db.dbLock) {
				PreparedStatement st;
				st = db.getConnection().prepareStatement("SELECT categoryId " +
						"FROM indexes " +
						"WHERE id = ? LIMIT 1");
				st.setInt(1, id);

				ResultSet set = st.executeQuery();

				if (!set.next()) {
					st.close();
					return -1;
				}

				Object o = set.getObject("categoryId");

				if (o == null) {
					st.close();
					return -1;
				}

				int i = set.getInt("categoryId");
				st.close();
				return i;
			}
		} catch (SQLException e) {
			Logger.error(this,
					"Unable to get the category of the index because : " +
							e.toString());
		}

		return -1;
	}

	public String getCategory() {
		try {
			synchronized (db.dbLock) {
				PreparedStatement st;
				st = db.getConnection().prepareStatement("SELECT categories.name AS name " +
						"FROM categories INNER JOIN indexes " +
						" ON categories.id = indexes.categoryId " +
						"WHERE indexes.id = ? LIMIT 1");
				st.setInt(1, id);

				ResultSet set = st.executeQuery();

				if (!set.next()) {
					st.close();
					return null;
				}

				String r = set.getString("name").toLowerCase();
				st.close();
				return r;
			}
		} catch (SQLException e) {
			Logger.error(this,
					"Unable to get the category of the index because : " +
							e.toString());
		}

		return null;
	}

	public static String cleanUpCategoryName(String category) {
		if (category == null)
			return null;

		category = category.trim();

		category = category.toLowerCase();

		String oldCat;

		do {
			oldCat = category;
			category = category.replaceAll("//", "/");
		} while (!oldCat.equals(category));

		if ("".equals(category))
			return null;

		return category;
	}

	/** create it if it doesn't exist */
	protected int getCategoryId(String cat) {
		cat = cleanUpCategoryName(cat);

		if (cat == null)
			return -1;

		try {
			synchronized (db.dbLock) {
				PreparedStatement st;
				ResultSet set;

				int catId = 1;

				st = db.getConnection().prepareStatement("SELECT id FROM categories " +
						"WHERE name = ? LIMIT 1");
				st.setString(1, cat);

				set = st.executeQuery();

				/* if it doesn't exist, we create it */
				if (!set.next()) {

					st = db.getConnection().prepareStatement("SELECT id FROM categories " +
							"ORDER by id DESC LIMIT 1");
					set = st.executeQuery();
					if (set.next())
						catId = set.getInt("id") + 1;

					st.close();

					/* insertion */
					st = db.getConnection().prepareStatement("INSERT INTO categories " +
							"(id, name) VALUES (?, ?)");
					st.setInt(1, catId);
					st.setString(2, cat);
					st.execute();
					st.close();

					return catId;
				} else {
					/* else we return the existing id */
					int i = set.getInt("id");
					st.close();
					return i;
				}
			}
		} catch (SQLException e) {
			Logger.error(this, "Can't create/find the category '" + cat + "'");
		}

		return -1;
	}

	/** create it if it doesn't exist */
	public void setCategory(String category) {

		cleanUpCategoryName(category);

		if (category == null || "".equals(category))
			return;

		try {

			synchronized (db.dbLock) {
				int catId = getCategoryId(category);

				if (catId < 0)
					return;

				/* set the categoryId of the index */

				PreparedStatement st = db.getConnection().prepareStatement("UPDATE indexes SET categoryId = ? " +
						"WHERE id = ?");
				st.setInt(1, catId);
				st.setInt(2, id);
				st.execute();
				st.close();
			}

		} catch (SQLException e) {
			Logger.error(this, "Can't set the category because : " + e.toString());
		}
	}

	public boolean downloadSuccessful() {
		return successful;
	}

	public void setClientVersion(String str) {
		/* only used if it's the Thaw index who was updated */

		final String thawIndexPart = FreenetURIHelper.getComparablePart(thaw.plugins.IndexBrowser.DEFAULT_INDEXES[0]);
		final String thisIndexPart = FreenetURIHelper.getComparablePart(getPublicKey());

		if (!thawIndexPart.equals(thisIndexPart))
			return;

		try {
			if (!str.startsWith("Thaw ")) { /* not made with Thaw ?! */
				Logger.notice(this, "Can't parse the Thaw version in the index '" + toString(false) + "' ?!");
				return;
			}

			str = str.substring(5);

			int spacePos = -1;

			if ((spacePos = str.indexOf(" ")) < 0) { /* hu ? */
				Logger.notice(this, "Can't parse the Thaw version in the index '" + toString(false) + "' ?!");
				return;
			}

			str = str.substring(0, spacePos);
			Version version = Version.valueOf(str);

			if (version.compareTo(Main.getVersion()) > 0) {
				/* quick and dirty way to warn the user */
				Logger.warning(this, I18n.getMessage("thaw.plugins.index.newThawVersion").replaceAll("X", version.toString()));
			}

		} catch (Exception e) {
			Logger.notice(this, "Unable to parse the client string of the index '" + toString(false) +
					"' because : " + e.toString());
			e.printStackTrace();
		}
	}
}
