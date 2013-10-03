package thaw.plugins;

import javax.swing.ImageIcon;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import thaw.core.Core;
import thaw.core.I18n;
import thaw.core.LibraryPlugin;
import thaw.core.Logger;
import thaw.gui.IconBox;

public class Hsqldb extends LibraryPlugin {

	private Core core;

	public final Object dbLock;

	public Hsqldb() {
		dbLock = new Object();
	}

	public boolean run(final Core core) {
		this.core = core;

		try {
			Class.forName("org.hsqldb.jdbcDriver");
		} catch (final Exception e) {
			Logger.error(this, "ERROR: failed to load HSQLDB JDBC driver.");
			e.printStackTrace();
			System.exit(1);
			return false;
		}

		return true;
	}

	public void realStart() {
		Logger.info(this, "Connecting to the database ...");

		if (core.getConfig().getValue("hsqldb.url") == null)
			core.getConfig().setValue("hsqldb.url", "jdbc:hsqldb:file:thaw.db;shutdown=true");

		try {
			executeQuery("SET LOGSIZE 50;");
		} catch (final SQLException e) {
			/* Newer versions of HSQLDB have an alternate log size property */
			try {
				executeQuery("SET FILES LOG SIZE 50;");
			} catch (SQLException e1) {
				e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
			}
		}

		try {
			executeQuery("SET CHECKPOINT DEFRAG 50;");
		} catch (final SQLException e) {
			/* Newer versions of HSQLDB use a different property */
			try {
				executeQuery("SET FILES DEFRAG 50;");
			} catch (SQLException e1) {
				e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
			}
		}

		try {
			executeQuery("SET PROPERTY \"hsqldb.nio_data_file\" FALSE");
		} catch (SQLException e) {
			e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
		}
	}

	@Override
	public void realStop() {
		stop();
	}

	public Connection getConnection() throws SQLException {
		if (core.getConfig().getValue("hsqldb.url") == null)
			core.getConfig().setValue("hsqldb.url", "jdbc:hsqldb:file:thaw.db");

		Connection connection = connection = DriverManager.getConnection(core.getConfig().getValue("hsqldb.url"), "sa", "");

		return connection;
	}

	public void stop() {
		/* \_o< */
		try {
			executeQuery("SHUTDOWN");
		} catch (SQLException sqle1) {
			Logger.error(this, "Could not shutdown database! " + sqle1.toString());
		}
	}

	public String getNameForUser() {
		return I18n.getMessage("thaw.plugin.hsqldb.database");
	}

	public ImageIcon getIcon() {
		return IconBox.database;
	}

	/**
	 * Determines if the table exists in the database.
	 *
	 * @param tableName
	 * 		Name of the table to test.
	 * @return True if the table exists, else false.
	 */
	public boolean tableExists(final String tableName) {
		try {
			executeQuery("SELECT COUNT(1) FROM " + tableName);
			/* The table exists */
			return true;
		} catch (final SQLException e) {
			return false;
		}
	}

	/**
	 * Given a "CREATE TABLE" query, extracts the table name.
	 * <p/>
	 * TODO: Where should this go?  It doesn't use this class...
	 *
	 * @param query
	 * 		Create table query
	 * @return Table name contained in the create table query.
	 */
	public String getTableNameFromCreateTable(final String query) {
		try {
			Pattern findTablePattern = Pattern.compile("(?i)\\A\\s*CREATE\\s(?:MEMORY|CACHED|GLOBAL|TEMPORARY|TEMP|TEXT|\\s)+\\sTABLE\\s([\\w]+).*", Pattern.MULTILINE);
			Matcher findTableMatcher = findTablePattern.matcher(query);
			if (findTableMatcher.find()) {
				return findTableMatcher.group(1);
			} else {
				Logger.warning(this, "No table name found in query: " + query);
				return null;
			}
		} catch (PatternSyntaxException ex) {
			// Syntax error in the regular expression
			Logger.error(this, "PatternSyntaxException: " + ex.getMessage());
			return null;
		}
	}

	/**
	 * Executes the given query.
	 *
	 * @param query
	 * 		The query to execute
	 * @throws SQLException
	 * 		if the query can not be executed
	 */
	public void executeQuery(String query) throws SQLException {
		Statement statement = null;
		Connection connection = null;
		try {
			connection = getConnection();
			statement = connection.createStatement();
			statement.execute(query);
		} finally {
			if (statement != null) {
				statement.close();
			}
			if (connection != null) {
				connection.close();
			}
		}
	}

	/**
	 * Executes the given query, allowing to set parameters on the {@link
	 * PreparedStatement} generated for it.
	 *
	 * @param query
	 * 		The query to execute
	 * @param statementProcessor
	 * 		The prepared statement processor
	 * @return The number of updated rows
	 * @throws SQLException
	 * 		if the query can not be executed
	 */
	public int executeUpdate(String query, StatementProcessor statementProcessor) throws SQLException {
		Connection connection = null;
		try {
			connection = getConnection();
			return executeUpdate(connection, query, statementProcessor);
		} finally {
			close(connection);
		}
	}

	/**
	 * Executes the given query, allowing to set parameters on the {@link
	 * PreparedStatement} generated for it.
	 *
	 * @param connection
	 * 		The connection on which to run the query
	 * @param query
	 * 		The query to execute
	 * @param statementProcessor
	 * 		The prepared statement processor
	 * @return The number of updated rows
	 * @throws SQLException
	 * 		if the query can not be executed
	 */
	public int executeUpdate(Connection connection, String query, StatementProcessor statementProcessor) throws SQLException {
		PreparedStatement preparedStatement = null;
		try {
			preparedStatement = connection.prepareStatement(query);
			statementProcessor.processStatement(preparedStatement);
			return preparedStatement.executeUpdate();
		} finally {
			close(preparedStatement);
		}
	}

	/**
	 * Executes the given query.
	 *
	 * @param query
	 * 		The query to execute
	 * @param statementProcessor
	 * 		Processor for the statement
	 * @param resultSetProcessor
	 * 		Processor for the result set
	 * @throws SQLException
	 * 		if an SQL error occurs
	 */
	public void executeQuery(String query, StatementProcessor statementProcessor, ResultSetProcessor resultSetProcessor) throws SQLException {
		Connection connection = null;
		try {
			connection = getConnection();
			executeQuery(connection, query, statementProcessor, resultSetProcessor);
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
	}

	/**
	 * Executes the given query.
	 *
	 * @param connection
	 * 		The connection to execute the query on
	 * @param query
	 * 		The query to execute
	 * @param statementProcessor
	 * 		Processor for the statement
	 * @param resultSetProcessor
	 * 		Processor for the result set
	 * @throws SQLException
	 * 		if an SQL error occurs
	 */
	public void executeQuery(Connection connection, String query, StatementProcessor statementProcessor, ResultSetProcessor resultSetProcessor) throws SQLException {
		PreparedStatement preparedStatement = null;
		ResultSet resultSet = null;
		try {
			preparedStatement = connection.prepareStatement(query);
			statementProcessor.processStatement(preparedStatement);
			resultSet = preparedStatement.executeQuery();
			while (resultSet.next()) {
				resultSetProcessor.processResultSet(resultSet);
			}
		} finally {
			close(resultSet);
			close(preparedStatement);
		}
	}

	/**
	 * Closes the given connection.
	 *
	 * @param connection
	 * 		The connection to close (may be {@code null})
	 */
	public static void close(Connection connection) {
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException sqle1) {
				/* swallow. TODO - log it? */
			}
		}
	}

	/**
	 * Closes the given statement.
	 *
	 * @param statement
	 * 		The statement to close (may be {@code null})
	 */
	public static void close(Statement statement) {
		if (statement != null) {
			try {
				statement.close();
			} catch (SQLException sqle1) {
				/* swallow. TODO - log it? */
			}
		}
	}

	/**
	 * Closes the given result set.
	 *
	 * @param resultSet
	 * 		The result set to close (may be {@code null})
	 */
	public static void close(ResultSet resultSet) {
		if (resultSet != null) {
			try {
				resultSet.close();
			} catch (SQLException sqle1) {
				/* swallow. TODO - log it? */
			}
		}
	}

	/**
	 * Returns a statement processor that runs all the given statement processors
	 * in the order they are given.
	 *
	 * @param statementProcessors
	 * 		The statement processors to run
	 * @return A statement processor that runs all given statement processors
	 */
	public static StatementProcessor queue(final StatementProcessor... statementProcessors) {
		return new StatementProcessor() {
			@Override
			public void processStatement(PreparedStatement preparedStatement) throws SQLException {
				for (StatementProcessor statementProcessor : statementProcessors) {
					statementProcessor.processStatement(preparedStatement);
				}
			}
		};
	}

	/**
	 * Returns a statement processor that will set the parameter at the given index
	 * to a NULL value of the given type.
	 *
	 * @param index
	 * 		The index of the parameter to set
	 * @param type
	 * 		The type of the NULL value
	 * @return A statement processor setting a NULL value
	 */
	public static StatementProcessor setNull(final int index, final int type) {
		return new StatementProcessor() {
			@Override
			public void processStatement(PreparedStatement preparedStatement) throws SQLException {
				preparedStatement.setNull(index, type);
			}
		};
	}

	/**
	 * Returns a statement processor that will set the parameter at the given index
	 * to the given string value.
	 *
	 * @param index
	 * 		The index of the parameter to set
	 * @param value
	 * 		The value to set
	 * @return A statement processor setting the string value
	 */
	public static StatementProcessor setString(final int index, final String value) {
		return new StatementProcessor() {
			@Override
			public void processStatement(PreparedStatement preparedStatement) throws SQLException {
				preparedStatement.setString(index, value);
			}
		};
	}

	/**
	 * Returns a statement processor that will set the parameter at the given index
	 * to the given integer value.
	 *
	 * @param index
	 * 		The index of the parameter to set
	 * @param value
	 * 		The value to set
	 * @return A statement processor setting the integer value
	 */
	public static StatementProcessor setInt(final int index, final int value) {
		return new StatementProcessor() {
			@Override
			public void processStatement(PreparedStatement preparedStatement) throws SQLException {
				preparedStatement.setInt(index, value);
			}
		};
	}

	/** Interface for a {@link PreparedStatement} processor. */
	public interface StatementProcessor {

		/**
		 * Sets parameters on the given prepared statement before execution.
		 *
		 * @param preparedStatement
		 * 		The prepared statement to set parameters on
		 * @throws SQLException
		 * 		if an SQL error occurs
		 */
		public void processStatement(PreparedStatement preparedStatement) throws SQLException;

	}

	/**
	 * Interface for a result set processor. This processor is called for every row
	 * of a {@link ResultSet}.
	 */
	public interface ResultSetProcessor {

		/**
		 * Processes the given result set.
		 *
		 * @param resultSet
		 * 		The result set to process
		 * @throws SQLException
		 * 		if an SQL error occurs
		 */
		public void processResultSet(ResultSet resultSet) throws SQLException;

	}

}
