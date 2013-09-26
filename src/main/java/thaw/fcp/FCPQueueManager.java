package thaw.fcp;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import thaw.core.Logger;
import thaw.core.ThawRunnable;
import thaw.core.ThawThread;

/**
 * Manage a running and a pending queue of FCPTransferQuery. Please notice that
 * runningQueue contains too finished queries. Notify when: a query is added and
 * when a query change to one queue to another.
 */
public class FCPQueueManager extends Observable implements ThawRunnable, Observer {

	private final static int PRIORITY_MIN = 6; /* So 0 to 6 */

	private final FCPQueryManager queryManager;

	private int maxDownloads, maxInsertions;

	/* offset in the array == priority */
	/* Vector contains FCPQuery */

	private final Vector<Vector<FCPTransferQuery>> pendingQueries = new Vector<Vector<FCPTransferQuery>>(PRIORITY_MIN + 1);

	private final Vector<FCPTransferQuery> runningQueries = new Vector<FCPTransferQuery>();

	private Hashtable<String, FCPTransferQuery> keyTable;

	private Hashtable<String, FCPTransferQuery> filenameTable;

	private Thread scheduler;

	private boolean stopThread = false;

	private int lastId;

	private final String thawId;

	private boolean queueCompleted;

	private final Object lastIDLock = new Object();

	/** Calls setQueryManager() and then resetQueues(). */
	public FCPQueueManager(final FCPQueryManager queryManager,
						   final String thawId,
						   final int maxDownloads, final int maxInsertions) {
		this.queryManager = queryManager;
		pendingQueries.setSize(PRIORITY_MIN + 1);

		lastId = 0;
		queueCompleted = false;

		this.thawId = thawId;
		setMaxDownloads(maxDownloads);
		setMaxInsertions(maxInsertions);

		resetQueues();

		queryManager.getConnection().addObserver(this);
	}

	public boolean isQueueCompletlyLoaded() {
		return queueCompleted;
	}

	public void setQueueCompleted() {
		queueCompleted = true;
	}

	/** Use it if you want to bypass the queue. */
	public FCPQueryManager getQueryManager() {
		return queryManager;
	}

	public void setMaxDownloads(final int maxDownloads) {
		this.maxDownloads = maxDownloads;
	}

	public void setMaxInsertions(final int maxInsertions) {
		this.maxInsertions = maxInsertions;
	}

	/** Will purge the current known queue. */
	public void resetQueues() {
		synchronized (runningQueries) {
			runningQueries.clear();
		}

		synchronized (pendingQueries) {
			for (int i = 0; i <= PRIORITY_MIN; i++) {
				pendingQueries.set(i, new Vector<FCPTransferQuery>());
			}
		}

		keyTable = new Hashtable<String, FCPTransferQuery>();
		filenameTable = new Hashtable<String, FCPTransferQuery>();
	}

	/** Returns a copy of the currently pending queues. */
	public Vector<Vector<FCPTransferQuery>> getPendingQueues() {
		synchronized (pendingQueries) {
			return new Vector<Vector<FCPTransferQuery>>(pendingQueries);
		}
	}

	/**
	 * Take care: Can change while you're using it. The running queue contains
	 * running request, but also finished/failed ones. synchronize on it if you
	 * want to do iterate() on it.
	 */
	public Vector<FCPTransferQuery> getRunningQueue() {
		return new Vector<FCPTransferQuery>(runningQueries);
	}

	/** @return < 0 if no limit */
	public int getMaxDownloads() {
		return maxDownloads;
	}

	/** @return < 0 if no limit */
	public int getMaxInsertions() {
		return maxInsertions;
	}

	/** @return false if already added. */
	public boolean addQueryToThePendingQueue(final FCPTransferQuery query) {
		if (query.getThawPriority() < 0)
			return this.addQueryToTheRunningQueue(query);

		if (isAlreadyPresent(query)) {
			Logger.notice(this, "Key was already in one of the queues : " + query.getFilename());
			return false;
		}

		Logger.notice(this, "Adding query to the pending queue ...");

		synchronized (pendingQueries) {
			pendingQueries.get(query.getThawPriority()).add(query);
		}

		String fileKey = query.getFileKey();
		String filename = query.getFilename();

		if (FreenetURIHelper.isAKey(fileKey))
			keyTable.put(FreenetURIHelper.getComparablePart(fileKey), query);

		filenameTable.put(filename, query);

		setChanged();
		this.notifyObservers(query);

		Logger.notice(this, "Adding done");
		return true;
	}

	/**
	 * will call start() function of the query.
	 *
	 * @return false if already added
	 */
	public boolean addQueryToTheRunningQueue(final FCPTransferQuery query) {
		return this.addQueryToTheRunningQueue(query, true);
	}

	public boolean addQueryToTheRunningQueue(final FCPTransferQuery query, boolean callStart) {
		Logger.debug(this, "Adding query to the running queue ...");

		if (isAlreadyPresent(query)) {
			Logger.notice(this, "Key was already in one of the queues");
			return false;
		}

		if (!callStart
				&& query.getIdentifier() != null
				&& query.getIdentifier().startsWith(thawId)) {
			/* It's a resumed query => We to adapt the next Id
			 * to avoid collisions.
			 */

			/* FIXME (not urgent) : Find a cleaner / safer way. */
			try {
				String[] subId = query.getIdentifier().split("-");
				subId = subId[0].split("_");
				final int id = Integer.parseInt(subId[subId.length - 1]);

				synchronized (lastIDLock) {
					if (id > lastId) {
						lastId = id;
					}
				}
			} catch (final Exception e) {
				Logger.notice(this, "Exception while parsing previous Id (Not really a problem)");
			}
		}

		if (callStart)
			query.start();

		synchronized (runningQueries) {
			runningQueries.add(query);
		}

		String fileKey = query.getFileKey();
		String filename = query.getFilename();

		if (FreenetURIHelper.isAKey(fileKey))
			keyTable.put(FreenetURIHelper.getComparablePart(fileKey), query);

		if (filename != null)
			filenameTable.put(filename, query);

		setChanged();
		this.notifyObservers(query);

		Logger.debug(this, "Adding done");

		return true;
	}

	/** Doesn't* call stop() from the query. */
	public void moveFromRunningToPendingQueue(final FCPTransferQuery query) {
		remove(query);
		addQueryToThePendingQueue(query);
	}

	/**
	 * Restart non-persistent and non-finished queries being in the runninQueue.
	 * Usefull to restart these query when thaw just start.
	 */
	public void restartNonPersistent() {
		Logger.info(this, "Restarting non persistent query");

		final Vector<FCPTransferQuery> runningQueue = getRunningQueue();
		for (final FCPTransferQuery query : runningQueue) {
			if (!query.isPersistent() && !query.isFinished())
				query.start();
		}

		Logger.info(this, "Restart done.");
	}

	/** Don't stop() */
	public void remove(final FCPTransferQuery query) {
		synchronized (runningQueries) {
			runningQueries.remove(query);
		}

		synchronized (pendingQueries) {
			for (int i = 0; i <= FCPQueueManager.PRIORITY_MIN; i++)
				pendingQueries.get(i).remove(query);
		}

		String fileKey = query.getFileKey();
		String filename = query.getFilename();

		if (FreenetURIHelper.isAKey(fileKey))
			keyTable.remove(FreenetURIHelper.getComparablePart(fileKey));

		filenameTable.remove(filename);

		setChanged();
		this.notifyObservers(query);

	}

	private boolean isTheSame(final FCPTransferQuery queryA,
							  final FCPTransferQuery queryB) {
		if (queryA.getQueryType() != queryB.getQueryType())
			return false;

		if ((queryA.getIdentifier() != null) && (queryB.getIdentifier() != null)) {
			if (queryA.getIdentifier().equals(queryB.getIdentifier())) {
				Logger.debug(this, "isTheSame(): Identifier");
				return true;
			}
			return false;
		}

		if ((queryA.getFileKey() != null) && (queryB.getFileKey() != null)) {
			if (queryA.getFileKey().equals(queryB.getFileKey())) {
				Logger.debug(this, "isTheSame(): FileKey");
				return true;
			}
			return false;
		}

		if (queryA.getFilename() != null
				&& queryA.getFilename().equals(queryB.getFilename())) {
			Logger.debug(this, "isTheSame(): Filename");
			return true;
		}
		return false;
	}

	/** Compare only the refs. */
	public boolean isInTheQueues(final FCPTransferQuery query) {
		synchronized (runningQueries) {
			if (runningQueries.contains(query))
				return true;
		}

		synchronized (pendingQueries) {
			for (int i = 0; i <= FCPQueueManager.PRIORITY_MIN; i++) {
				if (pendingQueries.get(i).contains(query))
					return true;
			}
		}

		return false;
	}

	/**
	 * @param key
	 * 		file key or file name if key is unknown
	 */
	public FCPTransferQuery getTransfer(final String key) {
		FCPTransferQuery q;

		if (FreenetURIHelper.isAKey(key)) {
			q = (FCPTransferQuery) keyTable.get(FreenetURIHelper.getComparablePart(key));

			if (q != null)
				return q;

			return (FCPTransferQuery) filenameTable.get(FreenetURIHelper.getFilenameFromKey(key));
		}

		return (FCPTransferQuery) filenameTable.get(key);
	}

	/** Compare using the key. */
	public boolean isAlreadyPresent(final FCPTransferQuery query) {

		Iterator it;

		synchronized (runningQueries) {
			for (it = runningQueries.iterator();
				 it.hasNext(); ) {
				final FCPTransferQuery plop = (FCPTransferQuery) it.next();
				if (isTheSame(plop, query))
					return true;
			}
		}

		synchronized (pendingQueries) {
			for (int i = 0; i <= FCPQueueManager.PRIORITY_MIN; i++) {
				for (it = pendingQueries.get(i).iterator();
					 it.hasNext(); ) {
					final FCPTransferQuery plop = (FCPTransferQuery) it.next();
					if (isTheSame(plop, query))
						return true;
				}
			}
		}

		return false;
	}

	private void schedule() {
			/* We count the running query to see if there is an empty slot */

		int runningInsertions = 0;
		int runningDownloads = 0;

		synchronized (runningQueries) {
			for (final Iterator it = runningQueries.iterator(); it.hasNext(); ) {
				final FCPTransferQuery query = (FCPTransferQuery) it.next();

				if ((query.getQueryType() == 1 /* Download */)
						&& !query.isFinished())
					runningDownloads++;

				if ((query.getQueryType() == 2 /* Insertion */)
						&& !query.isFinished())
					runningInsertions++;
			}
		}


			/* We move queries from the pendingQueue to the runningQueue until we got our quota */
		for (int priority = 0;
			 (priority <= FCPQueueManager.PRIORITY_MIN)
					 && (((maxInsertions <= -1) || (runningInsertions < maxInsertions))
					 || ((maxDownloads <= -1) || (runningDownloads < maxDownloads)));
			 priority++) {

			synchronized (pendingQueries) {
				for (Iterator it = pendingQueries.get(priority).iterator();
					 it.hasNext()
							 && (((maxInsertions <= -1) || (runningInsertions < maxInsertions))
							 || ((maxDownloads <= -1) || (runningDownloads < maxDownloads))); ) {

					final FCPTransferQuery query = (FCPTransferQuery) it.next();

					if (((query.getQueryType() == 1)
							&& ((maxDownloads <= -1) || (runningDownloads < maxDownloads)))
							|| ((query.getQueryType() == 2)
							&& ((maxInsertions <= -1) || (runningInsertions < maxInsertions)))) {

						Logger.debug(this, "Scheduler : Moving a query from pendingQueue to the runningQueue");
						pendingQueries.get(priority).remove(query);

						it = pendingQueries.get(priority).iterator(); /* We reset iterator */

						this.addQueryToTheRunningQueue(query);

						if (query.getQueryType() == 1)
							runningDownloads++;

						if (query.getQueryType() == 2)
							runningInsertions++;

						try {
							Thread.sleep(300);
						} catch (final InterruptedException e) {
						}
					}
				}
			}
		}

	}

	private void updateStats() {
		synchronized (runningQueries) {
			for (FCPTransferQuery query : runningQueries) {
				query.updateStats();
			}
		}
	}

	public void run() {
		try {
			Thread.sleep(5000);
		} catch (final InterruptedException e) {
			/* \_o< */
		}

		while (!stopThread) {
			try {
				Thread.sleep(1000);
			} catch (final InterruptedException e) {
				/* We don't care */
			}

			if (!stopThread) {

				try {

					if (queryManager.getConnection().isConnected()
							&& queueCompleted)
						schedule();

					if (queryManager.getConnection().isConnected())
						updateStats();

				} catch (final Exception e) {
					Logger.error(this, "EXCEPTION FROM FCP SCHEDULER : " + e.toString() + " ; " + e.getMessage());
					e.printStackTrace();
				}

			}
		}

	}

	public void stop() {
		stopThread = true;
	}

	public void startScheduler() {
		scheduler = new Thread(new ThawThread(this, "FCP queue scheduler", this));
		stopThread = false;
		scheduler.start();
	}

	public void stopScheduler() {
		stopThread = true;
	}

	public String getAnID() {
		synchronized (lastIDLock) {
			if (lastId < Integer.MAX_VALUE) {
				lastId++;
			} else {
				lastId = 0;
			}

			return (thawId + "_" + Integer.toString(lastId));
		}
	}

	public boolean isOur(String queryId) {
		return queryId.startsWith(thawId);
	}

	public void update(final Observable o, final Object arg) {
		if ((o == queryManager.getConnection())
				&& !queryManager.getConnection().isConnected()) {

			/* Only the running queue ...
			 * pending queries are specifics to Thaw
			 */
			synchronized (runningQueries) {
				runningQueries.clear();
			}

			setChanged();
			notifyObservers();
		}
	}
}

