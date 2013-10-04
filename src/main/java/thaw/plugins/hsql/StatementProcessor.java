package thaw.plugins.hsql;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Interface for a {@link PreparedStatement} processor. The statement processor
 * is used after creating a {@link PreparedStatement} from a query to fill in
 * parameter values.
 *
 * @author David ‘Bombe’ Roden
 */
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
