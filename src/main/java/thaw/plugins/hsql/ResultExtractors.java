package thaw.plugins.hsql;

import com.google.common.base.Predicate;

/**
 * Helpers for {@link ResultExtractor}s.
 *
 * @author David ‘Bombe’ Roden
 */
public class ResultExtractors {

	/**
	 * Predicate that will always stop a {@link ResultExtractor} after the first
	 * item.
	 *
	 * @param <T>
	 * 		The type of the results being extracted
	 * @return A predicate that will stop after the first extracted item
	 */
	public static final <T> Predicate<T> stopOnFirst() {
		return new Predicate<T>() {
			@Override
			public boolean apply(T input) {
				return true;
			}
		};
	}

}
