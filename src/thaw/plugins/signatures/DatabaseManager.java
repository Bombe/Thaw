package thaw.plugins.signatures;

import java.sql.SQLException;

import thaw.core.Config;
import thaw.core.Logger;
import thaw.core.SplashScreen;
import thaw.plugins.Hsqldb;

public class DatabaseManager {

	private DatabaseManager() {

	}

	public static boolean init(Hsqldb db, Config config, SplashScreen splashScreen) {
		boolean newDb;

		newDb = false;

		if (config.getValue("signaturesDatabaseVersion") == null) {
			newDb = true;
			config.setValue("signaturesDatabaseVersion", "2");
		} else {

			/* CONVERTIONS */

			if ("1".equals(config.getValue("signaturesDatabaseVersion"))) {
				if (splashScreen != null)
					splashScreen.setStatus("Converting database ...");
				if (convertDatabase_1_to_2(db))
					config.setValue("signaturesDatabaseVersion", "2");
			}

		}

		createTables(db);

		addDevs(db);

		return newDb;
	}

	protected static boolean sendQuery(final Hsqldb db, final String query) {
		try {
			db.executeQuery(query);
			return true;
		} catch (final SQLException e) {
			Logger.notice(new DatabaseManager(), "While (re)creating sql tables: " + e.toString());
			return false;
		}
	}

	public static void createTables(Hsqldb db) {
		sendCreateTableQuery(db, "CREATE CACHED TABLE signatures ("
				+ "id INTEGER IDENTITY NOT NULL, "
				+ "nickName VARCHAR(255) NOT NULL, "
				+ "publicKey VARCHAR(400) NOT NULL, " /* publicKey */
				+ "privateKey VARCHAR(400) DEFAULT NULL, " /* privateKey */
				+ "isDup BOOLEAN DEFAULT FALSE NOT NULL, "
				+ "trustLevel TINYINT DEFAULT 0 NOT NULL)"); /* See Identity.java */
	}

	public static void addDev(Hsqldb db,
							  String nick,
							  String publicKey) {
		Identity identity;

		if ((identity = Identity.getIdentity(db, nick, publicKey, false /* dontCreate */)) == null) {
			identity = new Identity(db, -1,
					nick, publicKey, null,
					false,
					Identity.trustLevelInt[0] /* dev */);
			identity.insert();
			return;
		}

		/* TODO : Find a nicer way to update someone to the rank of developper */
		if (identity.getTrustLevel() >= 0)
			identity.setTrustLevel(Identity.trustLevelInt[0]);
	}

	public static void addDevs(Hsqldb db) {
		String[][] devs = thaw.plugins.Signatures.DEVS;

		for (int i = 0; i < devs.length; i++) {
			addDev(db, devs[i][0], devs[i][1]);
		}
	}


	/* dropTables is not implemented because signatures may be VERY important */
	/* (anyway, because of the foreign key, it would probably fail */

	protected static boolean convertDatabase_1_to_2(Hsqldb db) {
		if (!sendQuery(db, "DELETE FROM indexComments")
				|| !sendQuery(db, "DELETE FROM signatures")
				|| !sendQuery(db, "ALTER TABLE signatures DROP y")
				|| !sendQuery(db, "ALTER TABLE signatures DROP x")
				|| !sendQuery(db, "ALTER TABLE signatures ADD publicKey VARCHAR(400) NOT NULL")
				|| !sendQuery(db, "ALTER TABLE signatures ADD privateKey VARCHAR(400)"))
			return false;

		return true;
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
}
