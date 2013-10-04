package thaw.plugins.hsql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Predicate;

/**
 * {@link ResultSetProcessor} implementation that uses a {@link ResultCreator}
 * to convert the result set into values that can be retrieved after the query
 * has finished.
 *
 * @param <T>
 * 		The type of the values being extracted
 * @author David ‘Bombe’ Roden
 */
public class ResultExtractor<T> implements ResultSetProcessor, Iterable<T> {

	/** The result creator. */
	private final ResultCreator<T> resultCreator;

	/** Predicate for abortion. */
	private final Predicate<T> abortionPredicate;

	/** The created result. */
	private List<T> results = new ArrayList<T>();

	/**
	 * Creates a new result extractor.
	 *
	 * @param resultCreator
	 * 		The result creator
	 */
	public ResultExtractor(ResultCreator<T> resultCreator) {
		this(resultCreator, null);
	}

	/**
	 * Creates a new result extractor.
	 *
	 * @param resultCreator
	 * 		The result creator
	 * @param abortionPredicate
	 * 		The abortion predicate
	 */
	public ResultExtractor(ResultCreator<T> resultCreator, Predicate<T> abortionPredicate) {
		this.resultCreator = resultCreator;
		this.abortionPredicate = abortionPredicate;
	}

	/**
	 * Resets this result extractor.
	 *
	 * @return The reset result extractor
	 */
	public ResultExtractor<T> reset() {
		results.clear();
		return this;
	}

	/**
	 * Returns the extracted results.
	 *
	 * @return The extracted results
	 */
	public List<T> getResults() {
		return results;
	}

	//
	// RESULTSETPROCESSOR METHODS
	//

	@Override
	public boolean processResultSet(ResultSet resultSet) throws SQLException {
		T result = resultCreator.createResult(resultSet);
		results.add(result);
		return (abortionPredicate == null) || abortionPredicate.apply(result);
	}

	//
	// ITERABLE METHODS
	//

	@Override
	public Iterator<T> iterator() {
		return results.iterator();
	}

}
