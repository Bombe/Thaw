package thaw.plugins.miniFrost.frostKSK;

import java.util.Vector;
import javax.swing.JOptionPane;

import java.sql.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Date;

import thaw.core.Core;
import thaw.core.Logger;
import thaw.core.I18n;

import thaw.plugins.Hsqldb;
import thaw.plugins.MiniFrost;



public class KSKBoardFactory
	implements thaw.plugins.miniFrost.interfaces.BoardFactory {

	/* must correspond at the position in the array of boardfactory in miniFrost */
	public final static int BOARD_FACTORY_ID = 0;

	public final static String[] DEFAULT_BOARDS = new String[] {
		"freenet",
		"thaw",
		"frost",
		"fuqid",
		"successful",
		"unsuccessful",
		"Thaw-indexes",
		"de.freenet",
		"fr.accueil",
		"fr.boards",
		"fr.discussion",
		"fr.freenet",
		"boards",
		"public",
		"sites",
		"test"
	};


	private Hsqldb db;
	private Core core;
	private MiniFrost plugin;

	private HashMap boards;

	public KSKBoardFactory() {

	}


	public boolean init(Hsqldb db, Core core, MiniFrost plugin) {
		this.db = db;
		this.core = core;
		this.plugin = plugin;

		boolean firstStart = (core.getConfig().getValue("frostKSKDatabaseVersion") == null);

		convertExistingTables();

		createTables();

		if (firstStart)
			addDefaultBoards();

		boards = new HashMap();

		return true;
	}

	public MiniFrost getPlugin() {
		return plugin;
	}

	public Core getCore() {
		return core;
	}

	public Hsqldb getDb() {
		return db;
	}


	protected boolean sendQuery(String query) {
		return sendQuery(db, query);
	}

	protected static boolean sendQuery(final Hsqldb db, final String query) {
		try {
			db.executeQuery(query);
			return true;
		} catch(final SQLException e) {
			Logger.notice(e, "While (re)creating sql tables: "+e.toString());
			return false;
		}
	}


	protected void convertExistingTables() {
		if (core.getConfig().getValue("frostKSKDatabaseVersion") == null)
			return;

		if ("0".equals(core.getConfig().getValue("frostKSKDatabaseVersion"))) {
			if (convertDatabase_0_to_1())
				core.getConfig().setValue("frostKSKDatabaseVersion", "2");
		}
	}

	protected boolean convertDatabase_0_to_1() {
		if (!sendQuery("ALTER TABLE frostKSKMessages ADD COLUMN encryptedFor INTEGER DEFAULT NULL NULL")
		    || !sendQuery("ALTER TABLE frostKSKMessages ADD FOREIGN KEY (encryptedFor) REFERENCES signatures (id)")) {

			Logger.error(this, "Error while converting the board database from version 0 to 1");
			return false;

		}

		return true;
	}


	protected void createTables() {
		if (core.getConfig().getValue("frostKSKDatabaseVersion") != null)
			core.getConfig().setValue("frostKSKDatabaseVersion", "1");

		sendQuery("CREATE CACHED TABLE frostKSKBoards ("
			  + "id INTEGER IDENTITY NOT NULL, "
			  + "name VARCHAR(128) NOT NULL, "
			  + "lastUpdate DATE DEFAULT NULL NULL)");

		sendQuery("CREATE CACHED TABLE frostSSKBoards ("
			  + "id INTEGER IDENTITY NOT NULL, "
			  + "publicKey VARCHAR(256) NOT NULL, "
			  + "privateKey VARCHAR(256) NULL, "
			  + "kskBoardId INTEGER NOT NULL, "
			  + "FOREIGN KEY (kskBoardId) REFERENCES frostKSKBoards (id))");

		sendQuery("CREATE CACHED TABLE frostKSKMessages ("
			  + "id INTEGER IDENTITY NOT NULL, "
			  + "subject VARCHAR(512) NULL, "
			  + "nick VARCHAR(128) NOT NULL, "
			  + "sigId INTEGER NULL, "
			  + "content VARCHAR(32768) NOT NULL, "
			  + "date TIMESTAMP NOT NULL, "
			  + "msgId VARCHAR(128) NOT NULL, "
			  + "inReplyToId VARCHAR(128) NULL, "
			  + "inReplyTo INTEGER NULL, "
			  + "rev INTEGER NOT NULL, "
			  + "read BOOLEAN DEFAULT FALSE NOT NULL, "
			  + "archived BOOLEAN DEFAULT FALSE NOT NULL, "
			  + "encryptedFor INTEGER DEFAULT NULL NULL, "
			  + "boardId INTEGER NOT NULL, "
			  + "FOREIGN KEY (boardId) REFERENCES frostKSKBoards (id), "
			  + "FOREIGN KEY (inReplyTo) REFERENCES frostKSKMessages (id), "
			  + "FOREIGN KEY (sigId) REFERENCES signatures (id), "
			  + "FOREIGN KEY (encryptedFor) REFERENCES signatures (id))");

		sendQuery("CREATE CACHED TABLE frostKSKAttachmentFiles ("
			  + "id INTEGER IDENTITY NOT NULL, "
			  + "filename VARCHAR(256) NOT NULL, "
			  + "size BIGINT NOT NULL, "
			  + "key VARCHAR(512) NOT NULL, "
			  + "messageId INTEGER NOT NULL, "
			  + "FOREIGN KEY (messageId) REFERENCES frostKSKMessages (id))");

		sendQuery("CREATE CACHED TABLE frostKSKAttachmentBoards ("
			  + "id INTEGER IDENTITY NOT NULL, "
			  + "name VARCHAR(128) NOT NULL, "
			  + "publicKey VARCHAR(256) NULL, "
			  + "privateKey VARCHAR(256) NULL, "
			  + "description VARCHAR(512) NULL, "
			  + "messageId INTEGER NOT NULL, "
			  + "FOREIGN KEY (messageId) REFERENCES frostKSKMessages (id))");
	}


	protected void addDefaultBoards() {
		for (int i = 0 ; i < DEFAULT_BOARDS.length ; i++) {
			createBoard(DEFAULT_BOARDS[i], false);
		}
	}

	protected int countNewMessages(int boardId) {
		int count = 0;
		try {
			PreparedStatement subSt
				= db.getConnection().prepareStatement("SELECT count(id)"+
								      "FROM frostKSKMessages "+
								      "WHERE boardId = ? "+
								      "AND read = FALSE AND archived = FALSE");
			subSt.setInt(1, boardId);

			ResultSet subRes = subSt.executeQuery();

			if (subRes.next())
				count = subRes.getInt(1);
		} catch(SQLException e) {
			Logger.error(this, "Can't count the number of new message on the board because : "+e.toString());
		}

		return count;
	}



	public Vector getBoards() {
		Vector v = new Vector();

		try {
			synchronized(db.dbLock) {
				PreparedStatement st
					= db.getConnection().prepareStatement("SELECT frostKSKBoards.id, "+
									      "       frostKSKBoards.name, "+
									      "       frostKSKBoards.lastUpdate "+
									      "FROM frostKSKBoards LEFT OUTER JOIN frostSSKBoards "+
									      "  ON frostKSKBoards.id = frostSSKBoards.kskBoardId "+
									      "WHERE frostSSKBoards.id IS NULL "+
				                                              "ORDER BY LOWER(name)");
				ResultSet set = st.executeQuery();

				while(set.next()) {
					int id = set.getInt("id");
					String name = set.getString("name");
					Date lastUpdate = set.getDate("lastUpdate");

					if (boards.get(name) != null)
						v.add(boards.get(name));
					else {
						int count = countNewMessages(id);

						KSKBoard board = new KSKBoard(this,
									      id, name, lastUpdate,
									      count);

						v.add(board);
						boards.put(name, board);
					}
				}
			}
		} catch(SQLException e) {
			Logger.error(this, "Can't get the board list because : "+e.toString());
		}

		return v;
	}



	protected KSKBoard getBoard(int id) {
		for (Iterator it = boards.values().iterator();
		     it.hasNext();) {
			KSKBoard board = (KSKBoard)it.next();

			if (board.getId() == id)
				return board;
		}

		return null;
	}



	public Vector getAllMessages(String[] keywords, int orderBy,
				     boolean desc, boolean archived,
				     boolean unsigned, int minTrustLevel) {
		return KSKBoard.getMessages(-1, this, null, keywords,
					    orderBy, desc, archived,
					    unsigned, minTrustLevel, true);
	}



	public void createBoard(thaw.core.MainWindow mainWindow) {
		String name = JOptionPane.showInputDialog(mainWindow.getMainFrame(),
							  I18n.getMessage("thaw.plugin.miniFrost.boardName"),
							  I18n.getMessage("thaw.plugin.miniFrost.boardName"),
							  JOptionPane.QUESTION_MESSAGE);
		if (name == null)
			return;

		createBoard(name);
	}

	protected void createBoard(String name) {
		createBoard(name, true);
	}

	protected void createBoard(String name, boolean warningIfExisting) {
		try {
			synchronized(db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT frostKSKBoards.id "+
									 "FROM frostKSKBoards LEFT OUTER JOIN frostSSKBoards "+
									 "  ON frostKSKBoards.id = frostSSKBoards.kskBoardId "+
									 "WHERE frostSSKBoards.id IS NULL "+
									 "AND LOWER(frostKSKBoards.name) = ? "+
									 "LIMIT 1");

				st.setString(1, name.toLowerCase());

				ResultSet set = st.executeQuery();

				if (set.next()) {
					if (warningIfExisting)
						Logger.warning(this, "Board already added");
					return;
				}

				st = db.getConnection().prepareStatement("INSERT INTO frostKSKBoards (name) "+
									 "VALUES (?)");
				st.setString(1, name.toLowerCase());
				st.execute();
			}
		} catch(SQLException e) {
			Logger.error(this, "Can't add board because: "+e.toString());
		}

	}


	protected void createBoard(String name, String publicKey, String privateKey) {
		createBoard(name, publicKey, privateKey, true);
	}

	/**
	 * Put here to make my life simpler with the KSKBoardAttachment.
	 */
	protected void createBoard(String name, String publicKey, String privateKey,
				   boolean warningIfExisting) {

		if (!thaw.fcp.FreenetURIHelper.isAKey(publicKey)) {
			Logger.error(this, "Invalid publicKey");
			return;
		}

		if (privateKey != null && "".equals(privateKey))
			privateKey = null;


		try {
			synchronized(db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT id "+
									 "FROM frostSSKBoards "+
									 "WHERE publicKey = ?");
				st.setString(1, publicKey);
				ResultSet set = st.executeQuery();

				if (set.next()) {
					if (warningIfExisting)
						Logger.warning(this, "Board already added");
					return;
				}

				/* we must get the id first, else we will mix up things */

				int id = 0;

				st = db.getConnection().prepareStatement("SELECT id FROM frostKSKBoards "+
									 "ORDER by id DESC LIMIT 1");
				set = st.executeQuery();

				if (set.next())
					id = set.getInt("id") + 1;


				name = name.toLowerCase();

				st = db.getConnection().prepareStatement("INSERT INTO frostKSKBoards "+
									 "(id, name) VALUES (?, ?)");

				st.setInt(1, id);
				st.setString(2, name);

				st.execute();


				st = db.getConnection().prepareStatement("INSERT INTO frostSSKBoards "+
									 "(publicKey, privateKey, kskBoardId) "+
									 "VALUES (?, ?, ?)");
				st.setString(1, publicKey);
				if (privateKey != null)
					st.setString(2, privateKey);
				else
					st.setNull(2, Types.VARCHAR);
				st.setInt(3, id);

				st.execute();

			}
		} catch(SQLException e) {
			Logger.error(this, "Can't add the board because : "+e.toString());
		}
	}


	public String toString() {
		return I18n.getMessage("thaw.plugin.miniFrost.FrostKSK");
	}
}
