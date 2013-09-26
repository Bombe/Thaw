package thaw.core;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Vector;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import thaw.fcp.FCPClientGet;
import thaw.fcp.FCPQueueManager;
import thaw.fcp.FCPTransferQuery;

/**
 * Used when Thaw start and stop: Save the query not running (-> waiting in the
 * Thaw queue)
 */
public class QueueKeeper {

	private final static int MIN_PRIORITY = 6;

	/** Used to be able to call Logger functions. */
	private QueueKeeper() {

	}

	private static void loadQuery(final FCPQueueManager queueManager, final Element queryEl, final boolean runningQueue) {
		FCPTransferQuery newQuery = null;
		final HashMap<String, String> params = new HashMap<String, String>();

		final NodeList paramList = queryEl.getElementsByTagName("param");

		for (int i = 0; i < paramList.getLength(); i++) {
			final Node param = paramList.item(i);

			if ((param != null) && (param.getNodeType() == Node.ELEMENT_NODE)) {
				final Element paramEl = (Element) param;

				params.put(paramEl.getAttribute("name"),
						paramEl.getAttribute("value"));
			}
		}

		FCPClientGet.Builder getBuilder = new FCPClientGet.Builder(queueManager).setParameters(params);

		if ((queryEl.getAttribute("type") == null)
				|| "1".equals(queryEl.getAttribute("type")))
			getBuilder = getBuilder.setIsNewRequest(false);

		newQuery = getBuilder.build();

		if (runningQueue)
			queueManager.addQueryToTheRunningQueue(newQuery, false);
		else
			queueManager.addQueryToThePendingQueue(newQuery);

	}

	private static void loadQueries(final FCPQueueManager queueManager, final Element queriesEl, final boolean runningQueue) {
		final NodeList queries = queriesEl.getElementsByTagName("query");

		for (int i = 0; i < queries.getLength(); i++) {
			final Node queryNode = queries.item(i);

			if ((queryNode != null) && (queryNode.getNodeType() == Node.ELEMENT_NODE)) {
				QueueKeeper.loadQuery(queueManager, (Element) queryNode, runningQueue);
			}
		}
	}

	public static boolean loadQueue(final FCPQueueManager queueManager, final String fileName) {
		final File file = new File(fileName);

		if (!file.exists() || !file.canRead()) {
			Logger.info(new QueueKeeper(), "Unable to find previous queue state file '" + file.getPath() + "' => Not reloaded from file.");
			return false;
		}

		Document xmlDoc = null;
		DocumentBuilderFactory xmlFactory = null;
		DocumentBuilder xmlBuilder = null;

		Element rootEl = null;

		xmlFactory = DocumentBuilderFactory.newInstance();

		try {
			xmlBuilder = xmlFactory.newDocumentBuilder();
		} catch (final ParserConfigurationException e) {
			Logger.warning(new QueueKeeper(), "Unable to load queue because: " + e.toString());
			return false;
		}

		try {
			xmlDoc = xmlBuilder.parse(file);
		} catch (final SAXException e) {
			Logger.warning(new QueueKeeper(), "Unable to load queue because: " + e.toString());
			return false;
		} catch (final IOException e) {
			Logger.warning(new QueueKeeper(), "Unable to load queue because: " + e.toString());
			return false;
		}

		rootEl = xmlDoc.getDocumentElement();

		final NodeList runningQueues = rootEl.getElementsByTagName("runningQueue");

		for (int i = 0; i < runningQueues.getLength(); i++) {

			final Node runningQueueNode = runningQueues.item(i);

			if ((runningQueueNode != null) && (runningQueueNode.getNodeType() == Node.ELEMENT_NODE)) {
				QueueKeeper.loadQueries(queueManager, (Element) runningQueueNode, true);
			}
		}

		final NodeList pendingQueues = rootEl.getElementsByTagName("pendingQueue");

		for (int i = 0; i < pendingQueues.getLength(); i++) {

			final Node pendingQueueNode = pendingQueues.item(i);

			if ((pendingQueueNode != null) && (pendingQueueNode.getNodeType() == Node.ELEMENT_NODE)) {
				QueueKeeper.loadQueries(queueManager, (Element) pendingQueueNode, false);
			}
		}

		return true;
	}

	private static Element saveQuery(final FCPTransferQuery query, final Document xmlDoc) {
		if (!query.isPersistent())
			return null;

		if (query.isPersistent() && (query.isRunning() || query.isFinished()))
			return null;

		final HashMap<String, String> params = query.getParameters();

		final Element queryEl = xmlDoc.createElement("query");

		queryEl.setAttribute("type", Integer.toString(query.getQueryType()));

		Iterable<String> paramsKeySet = params.keySet();
		for (String key : paramsKeySet) {
			final Element paramEl = xmlDoc.createElement("param");

			paramEl.setAttribute("name", key);
			paramEl.setAttribute("value", (params.get(key)));

			queryEl.appendChild(paramEl);
		}

		return queryEl;
	}

	public static boolean saveQueue(final FCPQueueManager queueManager, final String fileName) {
		boolean needed = false;

		final Vector<Vector<FCPTransferQuery>> pendingQueues = queueManager.getPendingQueues();

		for (Vector<FCPTransferQuery> pendingQueue : pendingQueues) {
			if (pendingQueue.size() > 0) {
				needed = true;
				break;
			}
		}

		final File file = new File(fileName);

		if (!needed) {
			Logger.info(new QueueKeeper(), "Nothing in the pending queue to save.");
			file.delete(); // Else we may reload something that we shouldn't when restarting
			return true;
		}

		StreamResult fileOut;

		try {
			if ((!file.exists() && !file.createNewFile())
					|| !file.canWrite()) {
				Logger.warning(new QueueKeeper(), "Unable to write config file '" + file.getPath() + "' (can't write)");
				return false;
			}
		} catch (final IOException e) {
			Logger.warning(new QueueKeeper(), "Error while checking perms to save config: " + e);
		}

		fileOut = new StreamResult(file);

		Document xmlDoc = null;
		DocumentBuilderFactory xmlFactory = null;
		DocumentBuilder xmlBuilder = null;
		DOMImplementation impl = null;

		Element rootEl = null;

		xmlFactory = DocumentBuilderFactory.newInstance();

		try {
			xmlBuilder = xmlFactory.newDocumentBuilder();
		} catch (final ParserConfigurationException e) {
			Logger.error(new QueueKeeper(), "Unable to save queue because: " + e.toString());
			return false;
		}

		impl = xmlBuilder.getDOMImplementation();

		xmlDoc = impl.createDocument(null, "queue", null);

		rootEl = xmlDoc.getDocumentElement();

		final Element pendingQueueEl = xmlDoc.createElement("pendingQueue");

		for (int i = 0; i <= QueueKeeper.MIN_PRIORITY; i++) {
			for (FCPTransferQuery query : pendingQueues.get(i)) {
				final Element toSave = QueueKeeper.saveQuery(query, xmlDoc);

				if (toSave != null)
					pendingQueueEl.appendChild(toSave);
			}
		}

		rootEl.appendChild(pendingQueueEl);

		/* Serialization */
		final DOMSource domSource = new DOMSource(xmlDoc);
		final TransformerFactory transformFactory = TransformerFactory.newInstance();

		Transformer serializer;

		try {
			serializer = transformFactory.newTransformer();
		} catch (final TransformerConfigurationException e) {
			Logger.error(new QueueKeeper(), "Unable to save queue because: " + e.toString());
			return false;
		}

		serializer.setOutputProperty(OutputKeys.ENCODING, "ISO-8859-1");
		serializer.setOutputProperty(OutputKeys.INDENT, "yes");

		/* final step */
		try {
			serializer.transform(domSource, fileOut);
		} catch (final TransformerException e) {
			Logger.error(new QueueKeeper(), "Unable to save queue because: " + e.toString());
			return false;
		}

		return true;
	}

}
