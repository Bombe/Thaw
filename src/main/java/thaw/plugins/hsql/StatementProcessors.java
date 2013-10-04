package thaw.plugins.hsql;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Common {@link StatementProcessor}s and helper methods.
 *
 * @author David ‘Bombe’ Roden
 */
public class StatementProcessors {

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

	/**
	 * Returns a statement processor that will set the parameter at the given index
	 * to the given long value.
	 *
	 * @param index
	 * 		The index of the parameter to set
	 * @param value
	 * 		The value to set
	 * @return A statement processor setting the long value
	 */
	public static StatementProcessor setLong(final int index, final long value) {
		return new StatementProcessor() {
			@Override
			public void processStatement(PreparedStatement preparedStatement) throws SQLException {
				preparedStatement.setLong(index, value);
			}
		};
	}

	/**
	 * Returns a statement processor that will set the parameter at the given index
	 * to the given boolean value.
	 *
	 * @param index
	 * 		The index of the parameter to set
	 * @param value
	 * 		The value to set
	 * @return A statement processor setting the boolean value
	 */
	public static StatementProcessor setBoolean(final int index, final boolean value) {
		return new StatementProcessor() {
			@Override
			public void processStatement(PreparedStatement preparedStatement) throws SQLException {
				preparedStatement.setBoolean(index, value);
			}
		};
	}

	/**
	 * Returns a statement processor that will set the parameter at the given index
	 * to the given date value.
	 *
	 * @param index
	 * 		The index of the parameter to set
	 * @param value
	 * 		The value to set
	 * @return A statement processor setting the date value
	 */
	public static StatementProcessor setDate(final int index, final Date value) {
		return new StatementProcessor() {
			@Override
			public void processStatement(PreparedStatement preparedStatement) throws SQLException {
				preparedStatement.setDate(index, value);
			}
		};
	}

}
