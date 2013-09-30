package thaw.fcp;

import static thaw.fcp.FCPQuery.Type.OTHER;

public class FCPModifyConfig implements FCPQuery {

	private String name;

	private String value;

	private final FCPQueryManager queryManager;

	public FCPModifyConfig(String name, String newValue, FCPQueryManager queryManager) {
		this.name = name;
		this.value = newValue;
		this.queryManager = queryManager;
	}

	public Type getQueryType() {
		return OTHER;
	}

	public boolean start() {
		FCPMessage msg = new FCPMessage();
		msg.setMessageName("ModifyConfig");
		msg.setValue(name, value);

		queryManager.writeMessage(msg);

		return true;
	}

	public boolean stop() {
		return false;
	}

}
