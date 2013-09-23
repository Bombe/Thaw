package thaw.fcp;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import thaw.core.Logger;

/**
 * This class is designed to handle a LOT of FCPTransferQuery without
 * overloading the observer/observable model. FCPTransferQueries must be
 * non-persistent ! It's useful when you have a lot of ULPR to handle
 *
 * @author jflesch
 */
public class FCPMetaTransferQuery extends Observable implements Observer {

	private final FCPQueryManager queryManager;

	private final Hashtable idToQuery;

	private final String idField;

	public FCPMetaTransferQuery(FCPQueryManager queryManager) {
		this(queryManager, "Identifier");
	}

	private FCPMetaTransferQuery(FCPQueryManager queryManager, String idField) {
		this.queryManager = queryManager;
		this.idToQuery = new Hashtable();
		this.idField = idField;

		queryManager.addObserver(this);
	}

	private void add(String id, FCPTransferQuery query) {
		synchronized (idToQuery) {
			idToQuery.put(id, query);
		}
	}

	/**
	 * Will call query.start() itself
	 *
	 * @param query
	 * @return
	 */
	public boolean start(FCPTransferQuery query) {
		if (query == null)
			return false;

		/* safety check */
		if (query.isPersistent()) {
			Logger.error(this, "A persistent query was given to FCPMetaTransferQuery ! this should never happen !");
			try {
				throw new Exception("meh");
			} catch (Exception e) {
				e.printStackTrace();
			}

			return false;
		}

		/* safety check */
		if (!(query instanceof Observer)) {
			Logger.error(this, "A non-observer query (" + query.getClass().getName() + ") was given to FCPMetaTransferQuery ! this should never happen !");
			try {
				throw new Exception("meh");
			} catch (Exception e) {
				e.printStackTrace();
			}

			return false;
		}

		/* here we start for real */
		boolean r = query.start();

		if (r) {
			/* Ugly hack to replace the query manager by the metaTransferQuery */
			add(query.getIdentifier(), query);
			query.addObserver(this);
			queryManager.deleteObserver((Observer) query);
		}

		return r;
	}

	private void remove(String id) {
		synchronized (idToQuery) {
			idToQuery.remove(id);
		}
	}

	/**
	 * Will call query.stop() itself Can't work atm on non-persistent requests ....
	 * (node r1111)
	 *
	 * @param query
	 * @return
	 */
	public boolean stop(FCPTransferQuery query) {

		query.deleteObserver(this);

		boolean r = true;

		if (!query.isFinished())
			r = query.stop();

		if (r) {
			remove(query.getIdentifier());
		} else {
			query.addObserver(this);
		}

		return r;
	}

	public void stopAll() {
		Vector queries = new Vector();

		synchronized (idToQuery) {
			for (Iterator it = idToQuery.values().iterator(); it.hasNext(); )
				queries.add(it.next());
		}

		for (Iterator it = queries.iterator(); it.hasNext(); ) {
			FCPTransferQuery query = (FCPTransferQuery) it.next();
			stop(query);
		}
	}

	public void update(Observable o, Object param) {
		if (o instanceof FCPQueryManager) {

			FCPMessage msg = (FCPMessage) param;
			String targetId;

			if (msg != null && (targetId = msg.getValue(idField)) != null) {
				Observer obs;

				synchronized (idToQuery) {
					obs = (Observer) (idToQuery.get(targetId));
				}

				if (obs != null) {
					/* we redirect only to the target FCPTransferQuery */
					obs.update(o, param);
				}
			}

		}
		if (o instanceof FCPTransferQuery) {
			FCPTransferQuery q = (FCPTransferQuery) o;

			if (q.isFinished()) {
				q.deleteObserver(this);
				remove(q.getIdentifier());
			}

			setChanged();
			notifyObservers(o);
		}
	}

}
