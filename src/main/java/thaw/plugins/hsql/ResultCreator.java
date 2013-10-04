package thaw.plugins.hsql;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A result creator is responsible for creating an object from a single row of
 * a {@link ResultSet}.
 *
 * @param <T>
 * 		The type of object being created
 * 	@author David ‘Bombe’ Roden
 */
public interface ResultCreator<T> {

	/**
	 * Creates an object from the current row of the given result set.
	 *
	 * @param resultSet
	 * 		The result set to create an object from
	 * @return The created object
	 * @throws SQLException
	 * 		if an SQL error occurs
	 */
	public T createResult(ResultSet resultSet) throws SQLException;

}
