package thaw.plugins.hsql;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Interface for a result set processor. This processor is called for every row
 * of a {@link ResultSet} unless {@link #processResultSet(ResultSet)} returns
 * {@code false}.
 *
 * @author David ‘Bombe’ Roden
 */
public interface ResultSetProcessor {

	/**
	 * Processes the given result set.
	 *
	 * @param resultSet
	 * 		The result set to process
	 * @return {@code true} if the next row should be processed, {@code false} to
	 *         stop processing
	 * @throws SQLException
	 * 		if an SQL error occurs
	 */
	public boolean processResultSet(ResultSet resultSet) throws SQLException;

}
