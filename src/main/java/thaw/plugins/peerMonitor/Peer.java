package thaw.plugins.peerMonitor;

import static java.awt.Color.BLUE;
import static java.awt.Color.GRAY;
import static java.awt.Color.LIGHT_GRAY;
import static java.awt.Color.ORANGE;
import static java.awt.Color.PINK;
import static java.awt.Color.RED;
import static java.lang.String.format;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import thaw.core.Logger;

public class Peer {

	/**
	 * The status of a peer.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	public enum Status {

		CONNECTED("CONNECTED", new Color(0, 164, 0)),
		BACKED_OFF("BACKED OFF", ORANGE),
		LISTEN_ONLY("LISTEN ONLY", new Color(128, 128, 255)),
		TOO_OLD("TOO OLD", RED),
		TOO_NEW("TOO NEW", BLUE),
		DISCONNECTING("DISCONNECTING", GRAY),
		DISCONNECTED("DISCONNECTED", LIGHT_GRAY),
		NEVER_CONNECTED("NEVER CONNECTED", PINK),
		UNKNOWN("UNKNOWN", new Color(224, 192, 192));

		/** The status text. */
		private final String text;

		/** The color of the status. */
		private final Color color;

		/**
		 * Creates a new status.
		 *
		 * @param text
		 * 		The text of the status
		 * @param color
		 * 		The color of the status
		 */
		private Status(String text, Color color) {
			this.text = text;
			this.color = color;
		}

		//
		// ACCESSORS
		//

		/**
		 * Returns the text of the status.
		 *
		 * @return The text of the status
		 */
		public String getText() {
			return text;
		}

		/**
		 * Returns the color of the status.
		 *
		 * @return The color of the status
		 */
		public Color getColor() {
			return color;
		}

		//
		// ACTIONS
		//

		/**
		 * Parses the given string into a status.
		 *
		 * @param text
		 * 		The text to parse
		 * @return The parsed status
		 */
		public static Status parse(String text) {
			for (Status status : values()) {
				if (status.name().equals(text) || status.getText().equals(text)) {
					return status;
				}
			}
			Logger.warning(Status.class, format("Could not parse “%s” as peer status.", text));
			return UNKNOWN;
		}

	}

	private final Status status;

	private String displayName = null;

	private String identity = null;

	/** Information about the peer. */
	private final Map<String, String> parameters = new HashMap<String, String>();

	public Peer(Map<String, String> parameters) {
		this.parameters.putAll(parameters);

		displayName = parameters.get("myName");

		if (displayName == null)
			displayName = parameters.get("physical.udp");

		identity = parameters.get("identity");

		status = Status.parse(parameters.get("volatile.status"));
	}

	public Color getTextColor() {
		return status.getColor();
	}

	public Map<String, String> getParameters() {
		return parameters;
	}

	public String getIdentity() {
		return identity;
	}

	public String toString() {
		return displayName;
	}
}
