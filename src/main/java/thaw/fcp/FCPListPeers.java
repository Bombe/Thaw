package thaw.fcp;

import static thaw.fcp.FCPQuery.Type.OTHER;

import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

public class FCPListPeers extends Observable implements FCPQuery, Observer {

	private boolean withMetadata;

	private boolean withVolatile;

	private final Map<String, Map<String, String>> peers = new HashMap<String, Map<String, String>>();

	private boolean endList;

	private final FCPQueryManager queryManager;

	public FCPListPeers(boolean withMetadata, boolean withVolatile, FCPQueryManager queryManager) {
		this.queryManager = queryManager;
		endList = true;

		this.withMetadata = withMetadata;
		this.withVolatile = withVolatile;
	}

	public boolean start() {
		endList = false;
		/* FIXME – this is utterly, completely wrong. every instance of ListPeers should get its own replies. */
		peers.clear();

		FCPMessage msg = new FCPMessage();

		msg.setMessageName("ListPeers");
		msg.setValue("WithMetadata", Boolean.toString(withMetadata));
		msg.setValue("WithVolatile", Boolean.toString(withVolatile));

		queryManager.addObserver(this);

		return queryManager.writeMessage(msg);
	}

	public boolean stop() {
		queryManager.deleteObserver(this);
		return true;
	}

	public void update(Observable o, Object param) {
		if (o instanceof FCPQueryManager) {
			final FCPMessage msg = (FCPMessage) param;

			if (msg.getMessageName() == null)
				return;

			if (msg.getMessageName().equals("Peer")) {
				peers.put(msg.getValue("identity"), msg.getValues());
			}

			if (msg.getMessageName().equals("EndListPeers")) {
				endList = true;
				setChanged();
				notifyObservers(this);
			}
		}
	}

	public boolean hasEnded() {
		return endList;
	}

	public Map<String, Map<String, String>> getPeers() {
		return peers;
	}

	public Type getQueryType() {
		return OTHER;
	}
}
