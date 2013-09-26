package thaw.fcp;

import java.util.Observable;
import java.util.Observer;

import thaw.core.Logger;

/**
 * http://wiki.freenetproject.org/FreenetFCPSpec2Point0 See "ClientHello" and
 * "NodeHello". Note: This query disconnect you if node answer
 * CloseConnectionDuplicateClientName and start() returns false.
 */
public class FCPClientHello implements FCPQuery, Observer {

	public final static int NODEHELLO_TIMEOUT = 60; /* in seconds */

	private final static String FCP_EXPECTED_VERSION = "2.0";

	private String id;

	private String connectionId;

	private String nodeFCPVersion;

	private String nodeVersion;

	private String nodeName = null;

	private boolean testnet = false; /* Hmm, in fact, we shouldn't have to bother about this one */

	private int nmbCompressionCodecs = -1;

	private String[] codecs;

	private boolean receiveAnswer = false;

	private final FCPQueryManager queryManager;

	/** Need to know the id of the application (see FCP specs). */
	public FCPClientHello(final FCPQueryManager queryManager, final String id) {
		this.id = id;
		this.queryManager = queryManager;
	}

	public void setID(final String id) {
		this.id = id;
	}

	public String getNodeFCPVersion() {
		return nodeFCPVersion;
	}

	public String getNodeVersion() {
		return nodeVersion;
	}

	public String getNodeName() {
		return nodeName;
	}

	public boolean isOnTestnet() {
		return testnet;
	}

	public int getNmbCompressionCodecs() {
		return nmbCompressionCodecs;
	}

	/** Warning: This query is blocking (only this one) ! */
	public boolean start() {

		queryManager.getConnection().registerClientHello(this);

		final FCPMessage message = new FCPMessage();

		message.setMessageName("ClientHello");
		message.setValue("Name", id);
		message.setValue("ExpectedVersion", FCPClientHello.FCP_EXPECTED_VERSION);

		queryManager.addObserver(this);

		if (!queryManager.writeMessage(message)) {
			Logger.warning(this, "Unable to say hello ... ;(");
			queryManager.deleteObserver(this);
			return false;
		}

		int count = 0;

		while (!receiveAnswer && count < (NODEHELLO_TIMEOUT * 2)) {
			try {
				Thread.sleep(500);
			} catch (final InterruptedException e) {
				/* \_o< */
			}
			count++;
		}

		/* Cover our bases in case a timeout occurred...  Usually redundant. */
		queryManager.deleteObserver(this);

		if (nodeName != null) {
			Logger.info(this, "Hello " + nodeName + ", I'm Thaw :)");
		} else if (count >= (NODEHELLO_TIMEOUT * 2)) {
			Logger.warning(this, "Unable to connect, timeout ...");
			return false;
		} else {
			Logger.warning(this, "ID already used !");
			return false;
		}

		return true;
	}

	public void update(final Observable o, final Object arg) {
		if (arg == null)
			return;

		final FCPMessage answer = (FCPMessage) arg;

		if (o == queryManager) {

			if ("NodeHello".equals(answer.getMessageName())) {
				Logger.info(this, "Received a nodeHello");

				connectionId = answer.getValue("ConnectionIdentifier");

				if (connectionId == null)
					Logger.error(this, "** no connection identifier **");

				nodeFCPVersion = answer.getValue("FCPVersion");
				nodeVersion = answer.getValue("Version");
				nodeName = answer.getValue("Node");
				testnet = Boolean.valueOf(answer.getValue("Testnet")).booleanValue();
				codecs = answer.getValue("CompressionCodecs").split(" ");
				nmbCompressionCodecs = Integer.parseInt(codecs[0]);

				queryManager.deleteObserver(this);

				receiveAnswer = true;
				synchronized (this) {
					notify();
				}
			}

			if ("CloseConnectionDuplicateClientName".equals(answer.getMessageName())) {
				/* Damn ... ! */
				Logger.warning(this, "According to the node, Thaw ID is already used. Please change it in the configuration (in advanced mode)");
				queryManager.deleteObserver(this);
				receiveAnswer = true;
				synchronized (this) {
					notify();
				}
			}
		}

		if (!receiveAnswer) {
			Logger.warning(this, "This message wasn't for us ?! : " + answer.getMessageName());
		}
	}

	/** Not used. */
	public boolean stop() {
		return true;
	}

	public int getQueryType() {
		return 0;
	}

	public String getConnectionId() {
		return connectionId;
	}

	public String getCodec(int i) {
		if (i < nmbCompressionCodecs) {
			return codecs[i + 2].split("\\(")[0];
		} else {
			return "";
		}
	}
}

