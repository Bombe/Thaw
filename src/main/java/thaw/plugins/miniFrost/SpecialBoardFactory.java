package thaw.plugins.miniFrost;

import java.util.Vector;

import thaw.core.Logger;
import thaw.gui.MainWindow;
import thaw.plugins.Hsqldb;
import thaw.plugins.MiniFrost;
import thaw.plugins.WebOfTrust;
import thaw.plugins.miniFrost.interfaces.BoardFactory;

public class SpecialBoardFactory
		implements BoardFactory {

	private MiniFrost miniFrost;

	public SpecialBoardFactory() {

	}

	/** Init */
	public boolean init(Hsqldb db, thaw.core.Core core,
						WebOfTrust wot,
						MiniFrost miniFrost) {
		this.miniFrost = miniFrost;
		return true;
	}

	public boolean cleanUp(int archiveAfter, int deleteAfter) {
		/* nothing to do */
		return true;
	}

	/** @return all the boards managed by this factory */
	public Vector getBoards() {
		Vector v = new Vector();

		v.add(new SentMessages(miniFrost));
		v.add(new Outbox(miniFrost));

		return v;
	}

	public Vector getAllMessages(String[] keywords, int orderBy,
								 boolean desc, boolean archived,
								 boolean read,
								 boolean unsigned, int minTrustLevel) {
		/* NADA */
		return new Vector();
	}

	public Vector getSentMessages() {
		/* NADA */
		return new Vector();
	}

	/**
	 * display the dialog asking for a name, etc. the tree will be reloaded after
	 * that
	 */
	public void createBoard(MainWindow mainWindow /*BoardFolder parent*/) {
		Logger.warning(this, "NI !");
	}

	public Vector getAllKnownBoards() {
		return new Vector();
	}

	/** For example 'frost boards' ; Use I18n ... */
	public String toString() {
		return null;
	}

}
