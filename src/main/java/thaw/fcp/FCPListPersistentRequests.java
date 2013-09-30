package thaw.fcp;

import static thaw.fcp.FCPQuery.Type.OTHER;

public class FCPListPersistentRequests implements FCPQuery {

	private final FCPQueryManager queryManager;

	public FCPListPersistentRequests(FCPQueryManager queryManager) {
		this.queryManager = queryManager;
	}

	public boolean start() {
		final FCPMessage newMessage = new FCPMessage();

		newMessage.setMessageName("ListPersistentRequests");

		queryManager.writeMessage(newMessage);

		return true;
	}

	public boolean stop() {
		return true;
	}

	public Type getQueryType() {
		return OTHER;
	}

}
