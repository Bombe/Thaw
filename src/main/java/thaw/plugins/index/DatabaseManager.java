package thaw.plugins.index;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Iterator;
import java.util.Vector;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import thaw.core.Config;
import thaw.core.Logger;
import thaw.core.SplashScreen;
import thaw.fcp.FCPQueueManager;
import thaw.fcp.FreenetURIHelper;
import thaw.plugins.Hsqldb;

/* DOM */
/* SAX */

/**
 * Creates all the tables used to save the indexes, manages structure changes if
 * needed, etc.
 * <p/>
 * <br/> "Comprenne qui pourra" :P
 * <p/>
 * <pre>
 * indexFolders (id, name, positionInTree)
 *  |-- indexFolders (id, name, positionInTree)
 *  | |-- [...]
 *  |
 *  |-- indexes (id, realName, displayName, publicKey, [privateKey], ...)
 *    |-- links (id, indexName, indexPublicKey)
 *    |-- files (id, filename, publicKey, mime, size)
 *      |-- metadatas (id, name, value)
 *
 * indexParents(indexId, folderId) # table de jointure
 * folderParents(folderId, parentId) # table de jointure
 *
 * indexBlackList(id, key) # key are complete USK@
 *
 * </pre>
 * <p/>
 * positionInTree == position in its JTree branch.
 */
public class DatabaseManager {

	private DatabaseManager() {

	}

	/**
	 * @return true if database is a new one
	 * @splashScreen can be null
	 */
	public static boolean init(Hsqldb db, Config config, SplashScreen splashScreen) {
		boolean newDb;

		newDb = false;

		if (config.getValue("indexDatabaseVersion") == null) {
			newDb = true;
			config.setValue("indexDatabaseVersion", "10");
		} else {

			/* CONVERTIONS */

			if ("1".equals(config.getValue("indexDatabaseVersion"))) {
				if (splashScreen != null)
					splashScreen.setStatus("Converting database ...");
				if (convertDatabase_1_to_2(db))
					config.setValue("indexDatabaseVersion", "2");
				/* else
				 * TODO : Put a warning here and stop the plugin loading
				 */
			}

			if ("2".equals(config.getValue("indexDatabaseVersion"))) {
				if (splashScreen != null)
					splashScreen.setStatus("Converting database ...");
				if (convertDatabase_2_to_3(db))
					config.setValue("indexDatabaseVersion", "3");
			}

			if ("3".equals(config.getValue("indexDatabaseVersion"))) {
				if (splashScreen != null)
					splashScreen.setStatus("Converting database ...");
				if (convertDatabase_3_to_4(db))
					config.setValue("indexDatabaseVersion", "4");
			}

			if ("4".equals(config.getValue("indexDatabaseVersion"))) {
				if (splashScreen != null)
					splashScreen.setStatus("Converting database ...");
				if (convertDatabase_4_to_5(db))
					config.setValue("indexDatabaseVersion", "5");
			}

			if ("5".equals(config.getValue("indexDatabaseVersion"))) {
				if (splashScreen != null)
					splashScreen.setStatus("Converting database ...");
				if (convertDatabase_5_to_6(db))
					config.setValue("indexDatabaseVersion", "6");
			}

			if ("6".equals(config.getValue("indexDatabaseVersion"))) {
				if (splashScreen != null)
					splashScreen.setStatus("Converting database ...");
				if (convertDatabase_6_to_7(db))
					config.setValue("indexDatabaseVersion", "7");
			}

			if ("7".equals(config.getValue("indexDatabaseVersion"))) {
				if (splashScreen != null)
					splashScreen.setStatus("Converting database ...");
				if (convertDatabase_7_to_8(db))
					config.setValue("indexDatabaseVersion", "8");
			}

			if ("8".equals(config.getValue("indexDatabaseVersion"))) {
				if (splashScreen != null)
					splashScreen.setStatus("Converting database ...");
				if (convertDatabase_8_to_9(db))
					config.setValue("indexDatabaseVersion", "9");
			}

			if ("9".equals(config.getValue("indexDatabaseVersion"))) {
				if (splashScreen != null)
					splashScreen.setStatus("Converting database ...");
				if (convertDatabase_9_to_10(db))
					config.setValue("indexDatabaseVersion", "10");
			}

			/* ... */
		}

		createTables(db);

		if (splashScreen != null)
			splashScreen.setStatus("Cleaning up categories ...");
		cleanUpCategories(db);

		if (splashScreen != null)
			splashScreen.setStatus("Updating link categories ...");
		updateLinkCategories(db);

		if (splashScreen != null)
			splashScreen.setStatus("Loading index browser ...");

		return newDb;
	}

	public static void cleanUpCategories(Hsqldb db) {
		try {
			synchronized (db.dbLock) {
				PreparedStatement st;
				PreparedStatement countSt;
				PreparedStatement countBisSt;
				PreparedStatement deleteSt;

				st = db.getConnection().prepareStatement("SELECT id FROM categories");
				countSt = db.getConnection().prepareStatement("SELECT id FROM indexes " +
						"WHERE categoryId = ? LIMIT 1");
				countBisSt = db.getConnection().prepareStatement("SELECT id FROM links " +
						"WHERE category = ? LIMIT 1");
				deleteSt = db.getConnection().prepareStatement("DELETE FROM categories " +
						"WHERE id = ?");

				ResultSet set = st.executeQuery();

				while (set.next()) {
					int id = set.getInt("id");

					countSt.setInt(1, id);

					ResultSet aSet = countSt.executeQuery();

					if (!aSet.next()) {
						countBisSt.setInt(1, id);
						aSet = countBisSt.executeQuery();

						if (!aSet.next()) {
							deleteSt.setInt(1, id);
							deleteSt.execute();
						}
					}
				}

				st.close();
				countSt.close();
				countBisSt.close();
				deleteSt.close();
			}
		} catch (SQLException e) {
			Logger.error(new DatabaseManager(), "Can't cleanup the unused categories because: " + e.toString());
		}
	}

	public static void updateLinkCategories(Hsqldb db) {
		try {
			synchronized (db.dbLock) {
				PreparedStatement selectLinks;
				PreparedStatement selectIndex;
				PreparedStatement updateLink;

				selectLinks = db.getConnection().prepareStatement("SELECT id, publicKey FROM links WHERE category IS NULL");
				selectIndex = db.getConnection().prepareStatement("SELECT categoryId FROM indexes wHERE LOWER(publicKey) LIKE ? LIMIT 1");
				updateLink = db.getConnection().prepareStatement("UPDATE links SET category = ? WHERE id = ?");

				ResultSet linksWithoutCategory = selectLinks.executeQuery();

				while (linksWithoutCategory.next()) {
					int linkId = linksWithoutCategory.getInt("id");
					String linkKey = linksWithoutCategory.getString("publicKey");

					selectIndex.setString(1, FreenetURIHelper.getComparablePart(linkKey) + "%");

					ResultSet possibleCorrespondingIndex = selectIndex.executeQuery();

					if (possibleCorrespondingIndex.next()) {
						Object o = possibleCorrespondingIndex.getObject("categoryId");

						if (o != null) {
							int catId = possibleCorrespondingIndex.getInt("categoryId");

							updateLink.setInt(1, catId);
							updateLink.setInt(2, linkId);
							updateLink.execute();
						}
					}
				}

				selectLinks.close();
				selectIndex.close();
				updateLink.close();
			}
		} catch (SQLException e) {
			Logger.error(new DatabaseManager(), "Can't cleanup the unused categories because: " + e.toString());
		}
	}

	/** Can be safely called, even if the tables already exist. */
	public static void createTables(final Hsqldb db) {
		//sendQuery(db,
		//	  "SET IGNORECASE TRUE");

		/* category syntax:
		 *  "folder[/subfolder[/subsubfolder]]"
		 */
		sendCreateTableQuery(db,
				"CREATE CACHED TABLE categories ("
						+ "id INTEGER IDENTITY NOT NULL,"
						+ "name VARCHAR(255) NOT NULL)");

		sendCreateTableQuery(db,
				"CREATE CACHED TABLE indexFolders ("
						+ "id INTEGER IDENTITY NOT NULL,"
						+ "name VARCHAR(255) NOT NULL,"
						+ "positionInTree INTEGER NOT NULL,"
						+ "modifiableIndexes BOOLEAN NOT NULL," /* Obsolete */
						+ "parent INTEGER,"
						+ "FOREIGN KEY (parent) REFERENCES indexFolders (id))");

		sendCreateTableQuery(db,
				"CREATE CACHED TABLE indexes ("
						+ "id INTEGER IDENTITY NOT NULL, "
						+ "originalName VARCHAR(255) NOT NULL, "
						+ "displayName VARCHAR(255) DEFAULT NULL, "
						+ "publicKey VARCHAR(255) NOT NULL, "
						+ "privateKey VARCHAR(255) DEFAULT NULL, "
						+ "publishPrivateKey BOOLEAN DEFAULT FALSE NOT NULL, "
						+ "author VARCHAR(255) DEFAULT NULL, "
						+ "positionInTree INTEGER NOT NULL, "
						+ "revision INTEGER NOT NULL, "
						+ "insertionDate DATE DEFAULT NULL, "
						+ "categoryId INTEGER DEFAULT NULL, "
						+ "newRev BOOLEAN DEFAULT FALSE NOT NULL, "
						+ "newComment BOOLEAN DEFAULT FALSE NOT NULL, "
						+ "parent INTEGER, " /* direct parent */
						+ "FOREIGN KEY (parent) REFERENCES indexFolders (id), "
						+ "FOREIGN KEY (categoryId) REFERENCES categories (id))");

		/* direct AND indirect parents */
		sendCreateTableQuery(db, /* this table avoid some horrible recursivities */
				"CREATE CACHED TABLE indexParents ("
						+ "indexId INTEGER NOT NULL,"
						+ "folderId INTEGER)");
		//+ "FOREIGN KEY (indexId) REFERENCES indexes (id)"
		//+ "FOREIGN KEY (folderId) REFERENCES indexFolders (id))");


		/* direct AND indirect parents */
		sendCreateTableQuery(db, /* this table avoid some horrible recursivities */
				"CREATE CACHED TABLE folderParents ("
						+ "folderId INTEGER NOT NULL,"
						+ "parentId INTEGER)");
		//+ "FOREIGN KEY (folderId) REFERENCES indexFolders (id)"
		//+ "FOREIGN KEY (parentId) REFERENCES indexFolders (id))");

		sendCreateTableQuery(db,
				"CREATE CACHED TABLE files ("
						+ "id INTEGER IDENTITY NOT NULL,"
						+ "filename VARCHAR(255) NOT NULL,"
						+ "publicKey VARCHAR(350) NOT NULL," // key ~= 100 + filename == 255 max => 350
						+ "localPath VARCHAR(500) DEFAULT NULL,"
						+ "mime VARCHAR(50) DEFAULT NULL,"
						+ "size BIGINT NOT NULL,"
						+ "category INTEGER," // TODO : This field is unused, to remove ?
						+ "indexParent INTEGER NOT NULL,"
						+ "toDelete BOOLEAN DEFAULT FALSE NOT NULL,"
						+ "dontDelete BOOLEAN DEFAULT FALSE NOT NULL,"
						+ "FOREIGN KEY (indexParent) REFERENCES indexes (id),"
						+ "FOREIGN KEY (category) REFERENCES categories (id))");

		sendCreateTableQuery(db,
				"CREATE CACHED TABLE links ("
						+ "id INTEGER IDENTITY NOT NULL,"
						+ "publicKey VARCHAR(350) NOT NULL," // key ~= 100 + filename == 255 max
						+ "mark INTEGER NOT NULL,"
						+ "comment VARCHAR(512) NOT NULL,"
						+ "indexParent INTEGER NOT NULL,"
						+ "indexTarget INTEGER,"
						+ "toDelete BOOLEAN DEFAULT false NOT NULL,"
						+ "dontDelete BOOLEAN DEFAULT false NOT NULL,"
						+ "blackListed BOOLEAN DEFAULT false NOT NULL,"
						+ "category INTEGER DEFAULT NULL, "
						+ "FOREIGN KEY (indexParent) REFERENCES indexes (id),"
						+ "FOREIGN KEY (indexTarget) REFERENCES indexes (id),"
						+ "FOREIGN KEY (category) REFERENCES categories (id))");

		sendCreateTableQuery(db,
				"CREATE CACHED TABLE metadataNames ("
						+ "id INTEGER IDENTITY NOT NULL,"
						+ "name VARCHAR(255) NOT NULL)");

		sendCreateTableQuery(db,
				"CREATE CACHED TABLE metadatas ("
						+ "id INTEGER IDENTITY NOT NULL,"
						+ "nameId INTEGER NOT NULL,"
						+ "value VARCHAR(255) NOT NULL,"
						+ "fileId INTEGER NOT NULL,"
						+ "FOREIGN KEY (fileId) REFERENCES files (id),"
						+ "FOREIGN KEY (nameId) REFERENCES metadataNames (id))");

		sendCreateTableQuery(db,
				"CREATE CACHED TABLE indexBlackList ("
						+ "id INTEGER IDENTITY NOT NULL,"
						+ "publicKey VARCHAR(350) NOT NULL,"
						+ "name VARCHAR(255) NOT NULL)");

		sendCreateTableQuery(db,
				"CREATE CACHED TABLE indexCommentKeys ("
						+ "id INTEGER IDENTITY NOT NULL,"
						+ "publicKey VARCHAR(255) NOT NULL,"
						+ "privateKey VARCHAR(255) NOT NULL,"
						+ "indexId INTEGER NOT NULL,"
						+ "FOREIGN KEY (indexId) REFERENCES indexes (id))");

		sendCreateTableQuery(db,
				"CREATE CACHED TABLE indexComments ("
						+ "id INTEGER IDENTITY NOT NULL,"
						+ "authorId INTEGER NOT NULL,"
						+ "text VARCHAR(16384) NOT NULL," /* 16 KB */
						+ "rev INTEGER NOT NULL,"
						+ "indexId INTEGER NOT NULL,"
						+ "sig VARCHAR(400) NOT NULL," /* signature */
						+ "FOREIGN KEY (indexId) REFERENCES indexes (id),"
						+ "FOREIGN KEY (authorId) REFERENCES signatures (id))");

		/**
		 * black listed comments should not be fetched.
		 * and if they are already fetched, they will be ignored at display time
		 */
		sendCreateTableQuery(db,
				"CREATE CACHED TABLE indexCommentBlackList ("
						+ "id INTEGER IDENTITY NOT NULL,"
						+ "rev INTEGER NOT NULL,"
						+ "indexId INTEGER NOT NULL,"
						+ "FOREIGN KEY (indexId) REFERENCES indexes (id))");
	}

	public static void dropTables(final Hsqldb db) {
		/* TODO : Add a warning here */

		sendQuery(db, "DROP TABLE metadatas");
		sendQuery(db, "DROP TABLE metadataNames");

		sendQuery(db, "DROP TABLE files");
		sendQuery(db, "DROP TABLE links");

		sendQuery(db, "DROP TABLE indexBlackList");

		sendQuery(db, "DROP TABLE indexCommentKeys");
		sendQuery(db, "DROP TABLE indexComments");
		sendQuery(db, "DROP TABLE indexCommentBlackList");

		sendQuery(db, "DROP TABLE indexes");
		sendQuery(db, "DROP TABLE indexFolders");

		sendQuery(db, "DROP TABLE indexParents");
		sendQuery(db, "DROP TABLE folderParents");

		sendQuery(db, "DROP TABLE categories");
	}

	/**
	 * Returns no error / Throws no exception.
	 *
	 * @return false if an exception happened
	 */
	protected static boolean sendQuery(final Hsqldb db, final String query) {
		try {
			db.executeQuery(query);
			return true;
		} catch (final SQLException e) {
			Logger.notice(new DatabaseManager(), "While (re)creating sql tables: " + e.toString());
			return false;
		}
	}

	/**
	 * Given a CREATE TABLE expression, determines if the table exists. If the
	 * table does not exist, calls sendQuery(db,query).
	 */
	protected static boolean sendCreateTableQuery(final Hsqldb db, final String query) {
		String tableName;

		tableName = db.getTableNameFromCreateTable(query);

		if (tableName != null) {
			if (!db.tableExists(tableName)) {
				Logger.warning(new DatabaseManager(), "Creating table " + tableName);
				return sendQuery(db, query);
			} else {
				return true;
			}
		} else {
			return false;
		}
	}

	/** try to use the auto increment instead */
	public static int getNextId(Hsqldb db, String table) {
		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("select id+1 from " +
						table +
						" order by id desc limit 1");
				ResultSet res = st.executeQuery();
				int r;

				if (res.next()) {
					r = res.getInt(1);
				} else
					r = 1;

				st.close();

				return r;
			}
		} catch (SQLException e) {
			Logger.error(new DatabaseManager(), "Unable to get next id because: " + e.toString());
		}

		return -1;
	}

	public static int getNmbIndexes(Hsqldb db) {
		int nmb;

		try {
			synchronized (db.dbLock) {
				final Connection c = db.getConnection();
				PreparedStatement st;

				st = c.prepareStatement("SELECT count(id) FROM indexes");
				st.execute();

				try {
					final ResultSet answer = st.getResultSet();
					answer.next();
					nmb = answer.getInt(1);
				} catch (final SQLException e) {
					nmb = 0;
				}

				st.close();
			}

		} catch (final SQLException e) {
			Logger.error(new DatabaseManager(), "Unable to insert the new index category in the db, because: " + e.toString());

			return 0;
		}

		return nmb;
	}

	public static void exportDatabase(java.io.File dest, Hsqldb db, IndexTree indexTree, boolean withContent) {
		//int nmbIndexes = getNmbIndexes(db);

		Logger.info(new DatabaseManager(), "Generating export ...");

		FileOutputStream outputStream;

		try {
			outputStream = new FileOutputStream(dest);
		} catch (final FileNotFoundException e) {
			Logger.warning(new DatabaseManager(), "Unable to create file '" + dest.toString() + "' ! not generated !");
			return;
		}

		final StreamResult streamResult = new StreamResult(outputStream);
		Document xmlDoc;

		final DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder xmlBuilder;

		try {
			xmlBuilder = xmlFactory.newDocumentBuilder();
		} catch (final ParserConfigurationException e) {
			Logger.error(new DatabaseManager(), "Unable to generate the index because : " + e.toString());
			return;
		}

		final DOMImplementation impl = xmlBuilder.getDOMImplementation();

		xmlDoc = impl.createDocument(null, "indexDatabase", null);

		final Element rootEl = xmlDoc.getDocumentElement();

		rootEl.appendChild(indexTree.getRoot().do_export(xmlDoc, withContent));


		/* Serialization */
		final DOMSource domSource = new DOMSource(xmlDoc);
		final TransformerFactory transformFactory = TransformerFactory.newInstance();

		Transformer serializer;

		try {
			serializer = transformFactory.newTransformer();
		} catch (final TransformerConfigurationException e) {
			Logger.error(new DatabaseManager(), "Unable to save index because: " + e.toString());
			return;
		}

		serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		serializer.setOutputProperty(OutputKeys.INDENT, "yes");

		/* final step */
		try {
			serializer.transform(domSource, streamResult);
		} catch (final TransformerException e) {
			Logger.error(new DatabaseManager(), "Unable to save index because: " + e.toString());
			return;
		}

		try {
			outputStream.close();
		} catch (IOException e) {
			Logger.warning(new DatabaseManager(), "Can't close the export file cleanly");
		}

		Logger.info(new DatabaseManager(), "Export done");
	}

	protected static class DatabaseHandler extends DefaultHandler {

		private final Hsqldb db;

		private IndexBrowserPanel indexBrowser;

		private IndexFolder importFolder;

		private IndexFolder folders[] = new IndexFolder[64];

		private int folderLevel = 0;

		public DatabaseHandler(IndexBrowserPanel indexBrowser) {
			this.db = indexBrowser.getDb();
			this.indexBrowser = indexBrowser;
		}

		/** @see ContentHandler#setDocumentLocator(Locator) */
		public void setDocumentLocator(Locator value) {

		}

		/**
		 * Called when parsing is started
		 *
		 * @see ContentHandler#startDocument()
		 */
		public void startDocument() throws SAXException {
			importFolder = indexBrowser.getIndexTree().getRoot().getNewImportFolder(db);
			folders[0] = importFolder;
			folderLevel = 0;
		}

		/**
		 * Called when parsing is finished
		 *
		 * @see ContentHandler#endDocument()
		 */
		public void endDocument() throws SAXException {
			/* \_o< */
		}

		/**
		 * Called when starting to parse in a specific name space
		 *
		 * @param prefix
		 * 		name space prefix
		 * @param URI
		 * 		name space URI
		 * @see ContentHandler#startPrefixMapping(String, String)
		 */
		public void startPrefixMapping(String prefix, String URI) throws SAXException {
			/* \_o< */
		}

		/**
		 * @param prefix
		 * 		name space prefix
		 * @see ContentHandler#endPrefixMapping(String)
		 */
		public void endPrefixMapping(String prefix) throws SAXException {
			/* \_o< */
		}

		/**
		 * if null, then not in an index else all the tag found will be sent to this
		 * handler
		 */
		private IndexParser.IndexHandler indexHandler = null;

		/**
		 * Called when the parsed find an opening tag
		 *
		 * @param localName
		 * 		local tag name
		 * @param rawName
		 * 		rawName (the one used here)
		 * @see ContentHandler#startElement(String, String, String, Attributes)
		 */
		public void startElement(String nameSpaceURI, String localName,
								 String rawName, Attributes attrs) throws SAXException {
			if (rawName == null) {
				rawName = localName;
			}

			if (rawName == null)
				return;

			if ("indexCategory".equals(rawName)) {
				/* should be indexFolder ... but because of the backward compatibility ... */

				if (attrs.getValue("name") == null) {
					/* this should never happen, but the exporter is a little
					 * b0rked, and I'm a little bit lazy, so it happens
					 */
					return;
				}

				folderLevel++;

				folders[folderLevel] =
						IndexManagementHelper.addIndexFolder(indexBrowser,
								folders[folderLevel - 1],
								attrs.getValue("name"));

				return;
			}

			if ("fullIndex".equals(rawName)) {
				Index index = IndexManagementHelper.reuseIndex(null, indexBrowser,
						folders[folderLevel],
						attrs.getValue("publicKey"),
						attrs.getValue("privateKey"),
						false, false);
				if (index != null) {
					index.rename(attrs.getValue("displayName"));

					indexHandler = new IndexParser(index).getIndexHandler();

					indexHandler.startDocument();
				} else {
					/* returned null because it already exists in the db ? */
					/* if yes, we will just update the private key */

					String publicKey = attrs.getValue("publicKey");
					String privateKey = attrs.getValue("privateKey");

					if (privateKey == null || "".equals(privateKey)) {
						return;
					}

					int id = Index.isAlreadyKnown(db, publicKey, true);

					if (id < 0)
						return;
					else
						Index.setPrivateKey(db, id, privateKey);
				}

				return;
			}

			if (indexHandler != null) {
				indexHandler.startElement(nameSpaceURI, localName,
						rawName, attrs);
			}
		}

		/**
		 * Called when a closing tag is met
		 *
		 * @see ContentHandler#endElement(String, String, String)
		 */
		public void endElement(String nameSpaceURI, String localName,
							   String rawName) throws SAXException {
			if (rawName == null) {
				rawName = localName;
			}

			if (rawName == null)
				return;

			if ("indexCategory".equals(rawName)) {
				folderLevel--;
				return;
			}

			if ("fullIndex".equals(rawName)) {
				if (indexHandler != null)
					indexHandler.endDocument();
				indexHandler = null;
				return;
			}

			if (indexHandler != null)
				indexHandler.endElement(nameSpaceURI, localName, rawName);
		}

		/**
		 * Called when a text between two tag is met
		 *
		 * @param ch
		 * 		text
		 * @param start
		 * 		position
		 * @param end
		 * 		position
		 * @see ContentHandler#characters(char[], int, int)
		 */
		public void characters(char[] ch, int start, int end) throws SAXException {
			if (indexHandler != null)
				indexHandler.characters(ch, start, end);

			/* ignore unkwown stuffs */

		}

		public void ignorableWhitespace(char[] ch, int start, int end) throws SAXException {

		}

		public void processingInstruction(String target, String data) throws SAXException {

		}

		/** @see ContentHandler#skippedEntity(String) */
		public void skippedEntity(String arg0) throws SAXException {

		}

	}

	public static void importDatabase(java.io.File source, IndexBrowserPanel indexBrowser, FCPQueueManager queueManager) {
		InputStream input;

		Logger.info(new DatabaseManager(), "Importing ...");

		try {
			input = new FileInputStream(source);
		} catch (final FileNotFoundException e) {
			Logger.error(new DatabaseManager(), "Unable to load XML: FileNotFoundException ('" + source.getPath() + "') ! : " + e.toString());
			return;
		}

		DatabaseHandler handler = new DatabaseHandler(indexBrowser);

		/* and remember kids, always lock the database before parsing an index ! */
		synchronized (indexBrowser.getDb().dbLock) {
			try {
				// Use the default (non-validating) parser
				SAXParserFactory factory = SAXParserFactory.newInstance();

				// Parse the input
				SAXParser saxParser = factory.newSAXParser();
				saxParser.parse(input, handler);
			} catch (ParserConfigurationException e) {
				Logger.error(new DatabaseManager(), "Error (1) while importing database : " + e.toString());
			} catch (SAXException e) {
				Logger.error(new DatabaseManager(), "Error (2) while importing database : " + e.toString());
			} catch (IOException e) {
				Logger.error(new DatabaseManager(), "Error (3) while importing database : " + e.toString());
			}
		}

		try {
			input.close();
		} catch (IOException e) {
			Logger.warning(new DatabaseManager(), "Unable to close cleanly the xml file");
		}

		indexBrowser.getIndexTree().getRoot().forceReload();
		indexBrowser.getIndexTree().refresh();

		Logger.info(new DatabaseManager(), "Import done");

	}

	/** used by convertDatabase_1_to_2() */
	private static boolean insertChildIn(Hsqldb db, int folderId) throws SQLException {
		Vector<Integer> results;
		int i = 0, j;

		Logger.notice(new DatabaseManager(), "Expanding folder " + Integer.toString(folderId));

		synchronized (db.dbLock) {
			PreparedStatement st;

			st = db.getConnection().prepareStatement("SELECT id FROM indexFolders WHERE " +
					((folderId >= 0) ? "parent = ?" : "parent IS NULL"));

			if (folderId >= 0)
				st.setInt(1, folderId);

			ResultSet set = st.executeQuery();
			results = new Vector<Integer>();

			while (set.next()) {
				results.add(set.getInt("id"));
			}

			st.close();
		}

		synchronized (db.dbLock) {
			PreparedStatement getChildFolders = db.getConnection().prepareStatement("SELECT folderId FROM folderParents WHERE parentId = ?");
			PreparedStatement setChildFolders = db.getConnection().prepareStatement("INSERT INTO folderParents (folderId, parentId) VALUES (?, ?)");
			PreparedStatement getChildIndexes = db.getConnection().prepareStatement("SELECT indexId FROM indexParents WHERE folderId = ?");
			PreparedStatement setChildIndexes = db.getConnection().prepareStatement("INSERT INTO indexParents (indexId, folderId) VALUES (?, ?)");

			for (int nextId : results) {
				if (!insertChildIn(db, nextId)) {
					Logger.error(new DatabaseManager(), "halted");
					getChildFolders.close();
					setChildFolders.close();
					getChildIndexes.close();
					setChildIndexes.close();
					return false;
				}

				i++;

				getChildFolders.setInt(1, nextId);

				Vector childFolders = new Vector();

				j = 0;

				ResultSet rs = getChildFolders.executeQuery();

				while (rs.next()) {
					j++;
					childFolders.add(new Integer(rs.getInt("folderId")));
				}

				for (Iterator ite = childFolders.iterator();
					 ite.hasNext(); ) {
					Integer a = (Integer) ite.next();

					setChildFolders.setInt(1, a.intValue());
					if (folderId < 0)
						setChildFolders.setNull(2, Types.INTEGER);
					else
						setChildFolders.setInt(2, folderId);

					setChildFolders.execute();
				}

				getChildIndexes.setInt(1, nextId);

				Vector childIndexes = new Vector();

				rs = getChildIndexes.executeQuery();

				while (rs.next()) {
					j++;
					childIndexes.add(new Integer(rs.getInt("indexId")));
				}

				if (j == 0) {
					Logger.warning(new DatabaseManager(), "empty folder (id = " + Integer.toString(nextId) + ") ?");
				}

				for (Iterator ite = childIndexes.iterator();
					 ite.hasNext(); ) {
					Integer a = (Integer) ite.next();

					setChildIndexes.setInt(1, a.intValue());
					if (folderId < 0)
						setChildIndexes.setNull(2, Types.INTEGER);
					else
						setChildIndexes.setInt(2, folderId);

					setChildIndexes.execute();
				}
			}

			getChildFolders.close();
			setChildFolders.close();
			getChildIndexes.close();
			setChildIndexes.close();
		}

		Logger.notice(new DatabaseManager(), Integer.toString(i) + " child folder found for folder " + Integer.toString(folderId));

		return true;
	}

	public static boolean convertDatabase_1_to_2(Hsqldb db) {
		if (!sendQuery(db, "ALTER TABLE indexCategories RENAME TO indexFolders")) {
			Logger.error(new DatabaseManager(), "Error while converting the database (1 to 2) (renaming table indexCategories to indexfolders)");
			return false;
		}

		if (!sendQuery(db, "ALTER TABLE indexes ADD COLUMN newRev BOOLEAN DEFAULT false")) {
			Logger.error(new DatabaseManager(), "Error while converting the database (1 to 2) ! (adding column 'newRev' to the index table)");
			return false;
		}

		if (!sendQuery(db, "ALTER TABLE indexes ADD COLUMN publishPrivateKey BOOLEAN DEFAULT false")) {
			Logger.error(new DatabaseManager(), "Error while converting the database (1 to 2) ! (adding column 'publishPrivateKey' to the index table)");
			return false;
		}

		if (!sendQuery(db, "ALTER TABLE links ADD COLUMN toDelete BOOLEAN DEFAULT false")) {
			Logger.error(new DatabaseManager(), "Error while converting the database (1 to 2) ! (adding column to link table)");
			return false;
		}

		if (!sendQuery(db, "ALTER TABLE files ADD COLUMN toDelete BOOLEAN DEFAULT false")) {
			Logger.error(new DatabaseManager(), "Error while converting the database (1 to 2) ! (adding column to file table)");
			return false;
		}

		/* direct AND indirect parents */
		if (!sendQuery(db, /* this table avoid some horrible recusirvities */
				"CREATE CACHED TABLE indexParents ("
						+ "indexId INTEGER NOT NULL,"
						+ "folderId INTEGER)")) {
			Logger.error(new DatabaseManager(), "Unable to create table because");
			return false;
		}

		/* direct AND indirect parents */
		if (!sendQuery(db, /* this table avoid some horrible recursivities */
				"CREATE CACHED TABLE folderParents ("
						+ "folderId INTEGER NOT NULL,"
						+ "parentId INTEGER)")) {
			Logger.error(new DatabaseManager(), "Unable to create table because");
			return false;
		}

		if (!sendQuery(db,
				"INSERT INTO folderParents (folderId, parentId) " +
						"SELECT id, parent FROM indexFolders")) {
			Logger.error(new DatabaseManager(), "Error while converting (1_2_1)");
			return false;
		}

		if (!sendQuery(db,
				"INSERT INTO indexParents (indexId, folderId) " +
						"SELECT id, parent FROM indexes")) {
			Logger.error(new DatabaseManager(), "Error while converting (1_2_2)");
			return false;
		}

		try {
			insertChildIn(db, -1);
		} catch (SQLException e) {
			Logger.error(new DatabaseManager(), "Error while converting : " + e.toString());
			return false;
		}


		/* convert SSK into USK */

		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT id, publicKey, originalName, revision FROM indexes");

				PreparedStatement subSt;
				subSt = db.getConnection().prepareStatement("UPDATE indexes " +
						"SET publicKey = ? " +
						"WHERE id = ?");

				ResultSet set = st.executeQuery();

				while (set.next()) {
					String key = set.getString("publicKey");

					if (key != null && key.startsWith("SSK@")) {
						int id = set.getInt("id");
						String name = set.getString("originalName");
						int rev = set.getInt("revision");

						String newKey;

						if (key.endsWith("/"))
							newKey = key.replaceFirst("SSK@", "USK@") + name + "/" + rev + "/" + name + ".frdx";
						else
							newKey = key.replaceFirst("SSK@", "USK@") + "/" + name + "/" + rev + "/" + name + ".frdx";

						Logger.notice(new DatabaseManager(), "Replacing " + key + " with " + newKey);

						subSt.setString(1, newKey);
						subSt.setInt(2, id);

						subSt.execute();
					}
				}

				st.close();
				subSt.close();
			}
		} catch (SQLException e) {
			Logger.error(new DatabaseManager(), "Error while converting SSK into USK : " + e.toString());
			return false;
		}

		return true;
	}

	public static boolean convertDatabase_2_to_3(Hsqldb db) {
		if (!sendQuery(db, "ALTER TABLE links ADD COLUMN dontDelete BOOLEAN DEFAULT false")) {
			Logger.error(new DatabaseManager(), "Error while converting the database (2 to 3) ! (adding column to link table)");
			return false;
		}

		if (!sendQuery(db, "ALTER TABLE files ADD COLUMN dontDelete BOOLEAN DEFAULT false")) {
			Logger.error(new DatabaseManager(), "Error while converting the database (2 to 3) ! (adding column to file table)");
			return false;
		}

		return true;
	}

	public static boolean convertDatabase_3_to_4(Hsqldb db) {
		if (!sendQuery(db, "ALTER TABLE links ADD COLUMN blackListed BOOLEAN DEFAULT false")) {
			Logger.error(new DatabaseManager(), "Error while converting the database (3 to 4) ! (adding column to link table)");
			return false;
		}

		return true;
	}

	public static boolean convertDatabase_4_to_5(Hsqldb db) {
		if (!sendQuery(db, "ALTER TABLE indexes ADD COLUMN newComment BOOLEAN DEFAULT false")) {
			Logger.error(new DatabaseManager(), "Error while converting the database (3 to 4) ! (adding column to index table)");
			return false;
		}

		return true;
	}

	public static boolean convertDatabase_5_to_6(Hsqldb db) {
		if (!sendQuery(db, "DELETE FROM indexComments")) {
			Logger.error(new DatabaseManager(), "Error while removing all the known comments");
		}

		if (!sendQuery(db, "ALTER TABLE indexComments DROP COLUMN author")) {
			Logger.error(new DatabaseManager(), "Error while altering the indexComments table (1)");
		}

		if (!sendQuery(db, "ALTER TABLE indexComments ADD COLUMN authorId INTEGER NOT NULL")) {
			Logger.error(new DatabaseManager(), "Error while altering the indexComments table (2)");
		}

		if (!sendQuery(db, "ALTER TABLE indexComments ADD FOREIGN KEY (authorId) REFERENCES signatures (id)")) {
			Logger.error(new DatabaseManager(), "Error while altering the indexComments table (3)");
		}

		if (!sendQuery(db, "ALTER TABLE indexComments ADD COLUMN r VARBINARY(400) NOT NULL")) {
			Logger.error(new DatabaseManager(), "Error while altering the indexComments table (4)");
		}

		if (!sendQuery(db, "ALTER TABLE indexComments ADD COLUMN s VARBINARY(400) NOT NULL")) {
			Logger.error(new DatabaseManager(), "Error while altering the indexComments table (5)");
		}

		return true;
	}

	public static boolean convertDatabase_6_to_7(Hsqldb db) {
		if (!sendQuery(db, "ALTER TABLE indexes ADD COLUMN insertionDate DATE DEFAULT NULL")) {
			Logger.error(new DatabaseManager(), "Error while converting the database (6 to 7) ! (adding column to index table)");
			return false;
		}

		return true;
	}

	public static boolean convertDatabase_7_to_8(Hsqldb db) {
		if (!sendQuery(db, "DELETE FROM indexComments")
				|| !sendQuery(db, "ALTER TABLE indexComments DROP r")
				|| !sendQuery(db, "ALTER TABLE indexComments DROP s")
				|| !sendQuery(db, "ALTER TABLE indexComments ADD COLUMN sig VARCHAR(400) NOT NULL")) {

			Logger.error(new DatabaseManager(), "Error while converting the database (7 to 8) !");
			return false;
		}

		return true;
	}

	public static boolean convertDatabase_8_to_9(Hsqldb db) {
		if (!sendQuery(db, "ALTER TABLE indexes ADD COLUMN categoryId INTEGER DEFAULT NULL")
				|| !sendQuery(db, "ALTER TABLE indexes ADD FOREIGN KEY (categoryId) REFERENCES categories (id)")) {

			Logger.error(new DatabaseManager(), "Error while converting the database (8 to 9) !");
			return false;
		}

		return true;

	}

	public static boolean convertDatabase_9_to_10(Hsqldb db) {
		if (!sendQuery(db, "ALTER TABLE links ADD COLUMN category INTEGER DEFAULT NULL")
				|| !sendQuery(db, "ALTER TABLE links ADD FOREIGN KEY (category) REFERENCES categories (id)")) {

			Logger.error(new DatabaseManager(), "Error while converting the database (8 to 9) !");
			return false;
		}

		return true;

	}
}
