package thaw.fcp;

import static thaw.fcp.FCPQuery.Type.OTHER;

import java.io.File;
import java.util.Observable;
import java.util.Observer;

import thaw.core.Logger;

/**
 * Reload the queue from the queue node. Send himself the
 * ListPersistentRequests. It remains active to receive and add the
 * persistentGet/Put receive during the execution
 */
public class FCPQueueLoader implements FCPQuery, Observer {

	private final FCPQueueManager queueManager;

	private final FCPQueryManager queryManager;

	private String thawId;

	public FCPQueueLoader(final String thawId, FCPQueueManager queueManager) {
		this.thawId = thawId;
		this.queueManager = queueManager;
		this.queryManager = queueManager.getQueryManager();
	}

	public boolean start() {
		queryManager.addObserver(this);

		final FCPListPersistentRequests listPersistent = new FCPListPersistentRequests(queryManager);
		return listPersistent.start();

		//if(ret)
		//	queueManager.getQueryManager().getConnection().addToWriterQueue();
	}

	public boolean stop() {
		queryManager.deleteObserver(this);
		return true;
	}

	public Type getQueryType() {
		return OTHER;
	}

	public void update(final Observable o, final Object param) {
		final FCPMessage msg = (FCPMessage) param;

		if ("PersistentGet".equals(msg.getMessageName())) {
			Logger.info(this, "Resuming from PersistentGet");

			int persistence = FCPClientGet.PERSISTENCE_FOREVER;

			if ("reboot".equals(msg.getValue("PersistenceType")))
				persistence = FCPClientGet.PERSISTENCE_UNTIL_NODE_REBOOT;
			else if ("connection".equals(msg.getValue("PersistenceType")))
				persistence = FCPClientGet.PERSISTENCE_UNTIL_DISCONNECT;

			boolean global = true;

			if (msg.getValue("Global") != null) {
				global = Boolean.valueOf(msg.getValue("Global")).booleanValue();
			}

			String destinationDir = null;

			if (msg.getValue("Identifier").startsWith(thawId))
				destinationDir = msg.getValue("ClientToken");

			final int priority = Integer.parseInt(msg.getValue("PriorityClass"));

			final FCPClientGet clientGet = new FCPClientGet.Builder(queueManager)
					.setIsNewRequest(false)
					.setIdentifier(msg.getValue("Identifier"))
					.setKey(msg.getValue("URI"))
					.setPriority(priority)
					.setPersistence(persistence)
					.setGlobalQueue(global)
					.setDestinationDir(destinationDir)
					.setStatus("Fetching")
					.setTransferStatus(TransferStatus.RUNNING)
					.setMaxRetries(-1)
					.build();

			if (queueManager.addQueryToTheRunningQueue(clientGet, false))
				queryManager.addObserver(clientGet);
			else
				Logger.info(this, "Already in the running queue");

		}

		if ("PersistentPut".equals(msg.getMessageName())) {
			Logger.info(this, "Resuming from PersistentPut");

			int persistence = FCPClientGet.PERSISTENCE_FOREVER;

			if ("reboot".equals(msg.getValue("PersistenceType")))
				persistence = FCPClientGet.PERSISTENCE_UNTIL_NODE_REBOOT;
			else if ("connection".equals(msg.getValue("PersistenceType")))
				persistence = FCPClientGet.PERSISTENCE_UNTIL_DISCONNECT;

			boolean global = true;

			if ("false".equals(msg.getValue("Global")))
				global = false;

			final int priority = Integer.parseInt(msg.getValue("PriorityClass"));

			long fileSize = 0;

			if (msg.getValue("DataLength") != null)
				fileSize = Long.parseLong(msg.getValue("DataLength"));

			String filePath = null;

			if (msg.getValue("Identifier").startsWith(thawId))
				filePath = msg.getValue("ClientToken");

			String fileName = null;

			if ((fileName = msg.getValue("TargetFilename")) == null) {
				if (msg.getValue("Identifier").startsWith(thawId)) {
					fileName = (new File(filePath)).getName();
				} else /* this is not out insertion, and we don't have the filename
					  so we can't resume it */
					return;
			}

			final FCPClientPut clientPut = new FCPClientPut.Builder(queueManager)
					.setIdentifier(msg.getValue("Identifier"))
					.setPublicKey(msg.getValue("URI"))
					.setPriority(priority)
					.setPersistence(persistence)
					.setGlobal(global)
					.setFileName(fileName)
					.setStatus("Inserting")
					.setFileSize(fileSize)
					.build();

			if (queueManager.addQueryToTheRunningQueue(clientPut, false))
				queryManager.addObserver(clientPut);
			else
				Logger.info(this, "Already in the running queue");

			return;
		}

		if ("EndListPersistentRequests".equals(msg.getMessageName())) {
			Logger.info(this, "End Of ListPersistentRequests.");
			//queueManager.getQueryManager().getConnection().removeFromWriterQueue();
			queueManager.setQueueCompleted();
			return;
		}
	}
}
