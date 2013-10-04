package thaw.plugins.hsql;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Collection of common {@link ResultCreator}s.
 *
 * @author David ‘Bombe’ Roden
 */
public class ResultCreators {

	/**
	 * Returns a {@link ResultCreator} that can create integer values from the
	 * element at the given index of the result set.
	 *
	 * @param index
	 * 		The index of the element to create an integer from
	 * @return The integer result creator
	 */
	public static ResultCreator<Integer> integerResultCreator(final int index) {
		return new ResultCreator<Integer>() {
			@Override
			public Integer createResult(ResultSet resultSet) throws SQLException {
				Integer result = resultSet.getInt(index);
				if (resultSet.wasNull()) {
					return null;
				}
				return result;
			}
		};
	}

	/**
	 * Returns a {@link ResultCreator} that can create {@link String} values from
	 * the element at the given index of the result set.
	 *
	 * @param index
	 * 		The index of the element to create a string from
	 * @return The string result creator
	 */
	public static ResultCreator<String> stringResultCreator(final int index) {
		return new ResultCreator<String>() {
			@Override
			public String createResult(ResultSet resultSet) throws SQLException {
				return resultSet.getString(index);
			}
		};
	}

}
