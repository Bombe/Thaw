package thaw.fcp;

import java.util.Observable;

/**
 *
 */
public interface FCPQuery {

	/**
	 * The type of the FCP query.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	public enum Type {

		/** The query is neither a download nor an upload. */
		OTHER,

		/** The query is a download. */
		DOWNLOAD,

		/** The query is an upload. */
		UPLOAD

	}

	public boolean start();

	/**
	 * Definitive stop. Transfer is considered as failed.
	 *
	 * @return false if really it *cannot* stop the query.
	 */
	public boolean stop();

	/**
	 * Tell if the query is a download query or an upload query. If the query is
	 * {@link Type#DOWNLOAD} or {@link Type#UPLOAD} then it *must* be {@link
	 * Observable} and implement {@link FCPTransferQuery}.
	 *
	 * @return The type of the query
	 */
	public Type getQueryType();

}
