package thaw.fcp;

import static thaw.fcp.FCPQuery.Type.OTHER;

public class FCPAddPeer implements FCPQuery {

	private String ref;

	private final FCPQueryManager queryManager;

	/** Ref can be a real ref, or URL=http://where.to-get-the-ref-on-the.net/ */
	public FCPAddPeer(String ref, FCPQueryManager queryManager) {
		this.ref = ref;
		this.queryManager = queryManager;
	}

	public boolean start() {
		FCPMessage msg = new FCPMessage();

		msg.setMessageName("AddPeer");

		String[] lines = ref.split("\n");

		for (int i = 0; i < lines.length; i++) {
			String[] elements = lines[i].split("=");

			if (elements.length < 2) /* may happen for the word 'end' at the end of the ref */
				continue;

			String optName = elements[0];
			String optValue = "";

			for (int j = 1; j < elements.length; j++)
				optValue += elements[j];

			msg.setValue(optName, optValue);
		}

		return queryManager.writeMessage(msg);
	}

	public boolean stop() {
		/* can't stop */
		return false;
	}

	public Type getQueryType() {
		return OTHER;
	}
}
