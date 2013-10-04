package thaw.plugins.webOfTrust;

import static java.util.Collections.emptyList;

import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;
import javax.swing.event.TableModelEvent;

import thaw.core.Config;
import thaw.core.I18n;
import thaw.gui.Table;
import thaw.plugins.Hsqldb;
import thaw.plugins.signatures.Identity;
import thaw.plugins.signatures.IdentityTable;
import thaw.plugins.webOfTrust.WotIdentity.TrustLink;

public class TrustListTable extends Observable implements Observer {

	private Hsqldb db;

	private IdentityTable table;

	private TrustListModel model;

	public TrustListTable(Hsqldb db, Config config) {
		this.db = db;

		model = new TrustListModel();

		table = new IdentityTable(config, "wotIdList_", false);
		table.setModel(model);
		table.addObserver(this);

		refresh(null);
	}

	public static class TrustListModel extends IdentityTable.IdentityModel {

		private static final long serialVersionUID = 2742525676359889703L;

		private List<TrustLink> trustLinks = null;

		public TrustListModel() {
			super(false);
		}

		public static String[] columnNames = {
				I18n.getMessage("thaw.plugin.signature.nickname"),
				I18n.getMessage("thaw.plugin.wot.itsTrustLevel"),
				I18n.getMessage("thaw.plugin.wot.yourTrustLevel"),
				I18n.getMessage("thaw.plugin.wot.wotTrustLevel")
		};

		public void setIdentities(List<TrustLink> i) {
			trustLinks = i;

			final TableModelEvent event = new TableModelEvent(this);
			fireTableChanged(event);
		}

		public List<Identity> getIdentities() {
			return null;
		}

		public int getRowCount() {
			if (trustLinks == null)
				return 0;

			return trustLinks.size();
		}

		public int getColumnCount() {
			return columnNames.length;
		}

		public String getColumnName(final int column) {
			return columnNames[column];
		}

		public Object getValueAt(int row, int column) {
			if (trustLinks == null)
				return null;

			if (column == 0)
				return trustLinks.get(row).getDestination().toString();

			if (column == 1)
				return Identity.getTrustLevelStr(trustLinks.get(row).getLinkTrustLevel());

			if (column == 2)
				return trustLinks.get(row).getDestination().getUserTrustLevelStr();

			if (column == 3)
				return trustLinks.get(row).getDestination().getTrustLevelStr();

			return null;
		}

		public Identity getIdentity(int row) {
			return trustLinks.get(row).getDestinationAsSeenByTheSource();
		}

	}

	public void refresh(Identity src) {
		List<TrustLink> ids;

		if (src != null)
			ids = WotIdentity.getTrustList(db, src);
		else
			ids = emptyList();

		model.setIdentities(ids);
	}

	public Table getTable() {
		return table.getTable();
	}

	public void update(Observable arg0, Object arg1) {
		setChanged();
		notifyObservers(arg1);
	}
}
