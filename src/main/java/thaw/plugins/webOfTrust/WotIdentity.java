package thaw.plugins.webOfTrust;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.Vector;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import thaw.core.Logger;
import thaw.fcp.FreenetURIHelper;
import thaw.plugins.Hsqldb;
import thaw.plugins.signatures.Identity;
import thaw.plugins.signatures.TrustListParser;

public class WotIdentity extends Identity implements TrustListParser.TrustListContainer {

	protected WotIdentity() {
	}

	protected WotIdentity(Identity i) {
		super(i.getDb(), i.getId(), i.getNick(), i.getPublicKey(), i.getPrivateKey(), i.isDup(), i.getTrustLevel());
	}

	public int getTrustLevel() {
		int t;

		if ((t = getUserTrustLevel()) != 0)
			return t;

		return getWotTrustLevel();
	}

	public int getWotTrustLevel() {
		/* TODO */
		return 0;
	}

	public int getUserTrustLevel() {
		return super.getTrustLevel();
	}

	public String getUserTrustLevelStr() {
		return super.getTrustLevelStr();
	}

	/** @return true if lastUpdate in the db is == NULL */
	public boolean currentTrustListHasAlreadyBeenDownloaded() {
		try {
			synchronized (getDb().dbLock) {
				PreparedStatement st = getDb().getConnection().prepareStatement("SELECT lastDownload FROM wotKeys WHERE sigId = ? LIMIT 1");
				st.setInt(1, getId());

				ResultSet set = st.executeQuery();

				boolean b = false;

				if (set.next()) {
					b = (set.getTimestamp("lastDownload") != null);
				}

				st.close();

				return b;
			}
		} catch (SQLException e) {
			Logger.error(this, "Error while checking if we already have the trust list of the identity '" + toString() + "'");
			e.printStackTrace();
		}

		return false;
	}

	/**
	 * @param db
	 * @return null if unknown
	 */
	public static WotIdentity getIdentity(Hsqldb db, String wotPublicKey) {
		try {
			synchronized (db.dbLock) {
				/* TODO : Optimize ! sluggard ! */
				PreparedStatement st = db.getConnection().prepareStatement("SELECT sigId FROM wotKeys WHERE LOWER(publicKey) LIKE ? LIMIT 1");
				st.setString(1, FreenetURIHelper.getComparablePart(wotPublicKey) + "%");

				ResultSet set = st.executeQuery();

				if (!set.next()) {
					st.close();
					return null;
				}

				WotIdentity wi = new WotIdentity(Identity.getIdentity(db, set.getInt("sigId")));
				st.close();
				return wi;
			}
		} catch (SQLException e) {
			Logger.error(new WotIdentity(), "Error while getting the identity corresponding to a wot public key: " + e.toString());
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * TODO : Check and update uploadDate
	 *
	 * @author jflesch
	 */
	protected class TrustListSecurityHandler extends TrustListParser.TrustListHandler implements TrustListParser.TrustListContainer {

		private TrustListSecurityHandler() {
			super(null /* java doesn't accept 'this' as argument here */);
			setTrustListContainer(this);
		}

		private boolean trustListOwnerTag = false;

		private boolean nickTag = false;

		private boolean publicKeyTag = false;

		private String nick = null;

		private String publicKey = null;

		public void startElement(String nameSpaceURI, String localName,
								 String rawName, Attributes attrs) throws SAXException {
			if (rawName == null) {
				rawName = localName;
			}

			if (rawName == null)
				return;

			if ("trustListOwner".equals(rawName)) {
				trustListOwnerTag = true;
			} else if (trustListOwnerTag && "nick".equals(rawName)) {
				nickTag = true;
			} else if (trustListOwnerTag && "publicKey".equals(rawName)) {
				publicKeyTag = true;
			} else
				super.startElement(nameSpaceURI, localName, rawName, attrs);
		}

		public void characters(char[] ch, int start, int end) throws SAXException {
			String txt = new String(ch, start, end);

			if (trustListOwnerTag && nickTag)
				nick = txt;
			else if (trustListOwnerTag && publicKeyTag)
				publicKey = txt;
			else
				super.characters(ch, start, end);
		}

		public void endElement(String nameSpaceURI, String localName,
							   String rawName) throws SAXException {
			if (rawName == null) {
				rawName = localName;
			}

			if (rawName == null)
				return;

			if ("trustListOwner".equals(rawName)) {
				trustListOwnerTag = false;
			} else if (trustListOwnerTag && "nick".equals(rawName)) {
				nickTag = false;
			} else if (trustListOwnerTag && "publicKey".equals(rawName)) {
				publicKeyTag = false;
			} else
				super.endElement(nameSpaceURI, localName, rawName);
		}

		public void updateIdentity(Identity i) {
			/* TODO : Check the signature of the trust list */
		}

		public boolean trustListIsValid() {
			boolean hasExpectedOwner = false;

			hasExpectedOwner = (getNick().equals(nick)) && (getPublicKey().equals(publicKey));

			return hasExpectedOwner;
		}

		public void start() {
		}

		public void end() {
		}
	}

	public boolean doSecurityChecks(File trustList) {
		TrustListSecurityHandler handler = new TrustListSecurityHandler();

		TrustListParser.importTrustList((TrustListParser.TrustListHandler) handler, trustList);

		return handler.trustListIsValid();
	}

	public boolean loadTrustList(File trustList) {
		try {
			TrustListParser.importTrustList(this, trustList);
		} catch (Exception e) {
			Logger.error(this, "Exception while parsing the trust list of '" + toString() + "' : '" + e.toString() + "' => trust list ignored");
			purgeTrustList();
			return false;
		}
		return true;
	}

	public void start() {
		purgeTrustList();
	}

	public void purgeTrustList() {
		try {
			synchronized (getDb().dbLock) {
				PreparedStatement st;

				st = getDb().getConnection().prepareStatement("DELETE FROM wotTrustLists WHERE source = ?");
				st.setInt(1, getId());
				st.execute();
				st.close();
			}
		} catch (SQLException e) {
			Logger.error(this, "Error while updating a trust list in the db (1) : " + e.toString());
			e.printStackTrace();
		}
	}

	public void end() {
	}

	public void updateIdentity(Identity i) {

		if (i.getTrustLevel() == 0
				|| i.getTrustLevel() < Identity.trustLevelInt[Identity.trustLevelInt.length - 1]
				|| i.getTrustLevel() > Identity.trustLevelInt[1]) {
			Logger.error(this, "Invalid trust level in the trust list of '" + toString() + "' !");
			throw new RuntimeException("Invalid trust level in the trust list of '" + toString() + "' !");
		}

		Identity target = Identity.getIdentity(getDb(), i.getNick(), i.getPublicKey());

		if (target == null) {
			Logger.notice(this, "Minor problem while parsing the trust list (1)");
			return;
		}

		try {
			synchronized (getDb().dbLock) {
				PreparedStatement insertTrustLinkSt = getDb().getConnection().prepareStatement("INSERT INTO wotTrustLists (source, destination, trustLevel) VALUES (?, ?, ?)");
				insertTrustLinkSt.setInt(1, getId());
				insertTrustLinkSt.setInt(2, target.getId());
				insertTrustLinkSt.setInt(3, i.getTrustLevel());
				insertTrustLinkSt.execute();
				insertTrustLinkSt.close();
			}
		} catch (SQLException e) {
			Logger.error(this, "Error while updating a trust list in the db (2) : " + e.toString());
			e.printStackTrace();
		}
	}

	public void updateInfos(String wotPublicKey, java.util.Date lastDownload) {
		try {
			synchronized (getDb().dbLock) {
				PreparedStatement st;

				st = getDb().getConnection().prepareStatement("UPDATE wotKeys SET publicKey = ?, keyDate = ?, lastDownload = ? WHERE sigId = ?");
				st.setString(1, wotPublicKey);
				st.setTimestamp(2, new Timestamp(new java.util.Date().getTime()));
				st.setTimestamp(3, new Timestamp(lastDownload.getTime()));
				st.setInt(4, getId());

				st.execute();
				st.close();
			}
		} catch (SQLException e) {
			Logger.error(this, "Error while updating infos in the wotKeys table : " + e.toString());
			e.printStackTrace();
		}
	}

	public String getWoTPublicKey() {
		try {
			synchronized (getDb().dbLock) {
				PreparedStatement st;

				st = getDb().getConnection().prepareStatement("SELECT publicKey FROM wotKeys WHERE sigId = ? LIMIT 1");

				st.setInt(1, getId());

				ResultSet set = st.executeQuery();

				if (!set.next()) {
					st.close();
					return null;
				}

				String r = set.getString("publicKey");
				st.close();
				return r;
			}
		} catch (SQLException e) {
			Logger.error(this, "Unable to check if an identity is in the WoT because : " + e.toString());
		}

		return null;
	}

	/**
	 * Just a container
	 *
	 * @author jflesch
	 */
	public static class TrustLink {

		private WotIdentity src;

		private WotIdentity dst;

		private Identity destinationAsSeenByTheSource;

		public TrustLink(Hsqldb db, WotIdentity src, WotIdentity dst, int linkTrustLevel) {
			this.src = src;
			this.dst = dst;
			destinationAsSeenByTheSource = new Identity(db, dst.getId(),
					dst.getNick(), dst.getPublicKey(),
					dst.getPrivateKey(), dst.isDup(),
					linkTrustLevel);
		}

		public WotIdentity getSource() {
			return src;
		}

		public WotIdentity getDestination() {
			return dst;
		}

		public int getLinkTrustLevel() {
			return destinationAsSeenByTheSource.getTrustLevel();
		}

		public Identity getDestinationAsSeenByTheSource() {
			return destinationAsSeenByTheSource;
		}
	}

	public Vector getTrustList() {
		return getTrustList(getDb(), this);
	}

	/**
	 * @param db
	 * @param idSrc
	 * @return a vector of TrustLink
	 */
	public static Vector getTrustList(Hsqldb db, Identity idSrc) {
		Vector v = new Vector();

		WotIdentity src = new WotIdentity(idSrc);

		if (idSrc.getPrivateKey() != null) {

			/* mode lazy bastard => on */
			Vector oids = getOtherWotIdentities(db);

			for (Iterator it = oids.iterator(); it.hasNext(); ) {
				WotIdentity id = (WotIdentity) it.next();
				v.add(new TrustLink(db, src, id, id.getUserTrustLevel()));
			}

			return v;
		}

		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT signatures.id AS id, " +
						" signatures.nickname AS nickname, " +
						" signatures.publicKey AS publicKey, " +
						" signatures.privateKey AS privateKey, " +
						" signatures.isDup AS isDup, " +
						" signatures.trustLevel AS yourTrustLevel, " +
						" wotTrustLists.trustLevel AS linkTrustLevel " +
						"FROM wotTrustLists INNER JOIN signatures ON wotTrustLists.destination = signatures.id " +
						"WHERE wotTrustLists.source = ?");
				st.setInt(1, idSrc.getId());

				ResultSet set = st.executeQuery();

				while (set.next()) {
					WotIdentity dst = new WotIdentity(new Identity(db,
							set.getInt("id"),
							set.getString("nickName"),
							set.getString("publicKey"),
							set.getString("privateKey"),
							set.getBoolean("isDup"),
							set.getInt("yourTrustLevel")));
					int linkTrustLevel = set.getInt("linkTrustLevel");

					TrustLink l = new TrustLink(db, src, dst, linkTrustLevel);

					v.add(l);
				}

				st.close();
			}
		} catch (SQLException e) {
			Logger.error(new WotIdentity(), "Error while getting trust list : " + e.toString());
			e.printStackTrace();
		}

		return v;
	}

	/** Returns only the identities with a trust > 0 */
	public static Vector getOtherWotIdentities(Hsqldb db) {
		Vector v = new Vector();

		try {
			synchronized (db.dbLock) {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT signatures.id AS id, " +
						" signatures.nickname AS nickname, " +
						" signatures.publicKey AS publicKey, " +
						" signatures.privateKey AS privateKey, " +
						" signatures.isDup AS isDup, " +
						" signatures.trustLevel AS trustLevel " +
						"FROM wotKeys INNER JOIN signatures ON wotKeys.sigId = signatures.id " +
						"WHERE signatures.privateKey IS NULL " +
						"AND signatures.trustLevel >= 0");

				ResultSet set = st.executeQuery();

				/* TODO : Optimize if possible */

				while (set.next()) {
					WotIdentity wi = new WotIdentity(new Identity(db,
							set.getInt("id"),
							set.getString("nickName"),
							set.getString("publicKey"),
							set.getString("privateKey"),
							set.getBoolean("isDup"),
							set.getInt("trustLevel")));
					if (wi.getTrustLevel() > 0 && wi.getTrustLevel() != Identity.trustLevelInt[0])
						v.add(wi);
				}

				st.close();
			}
		} catch (SQLException e) {
			Logger.error(new WotIdentity(), "Error while gettings identities used in the WoT : " + e.toString());
			e.printStackTrace();
		}

		return v;
	}
}
