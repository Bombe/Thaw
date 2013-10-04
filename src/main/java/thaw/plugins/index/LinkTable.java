package thaw.plugins.index;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;

import thaw.core.Config;
import thaw.core.I18n;
import thaw.fcp.FCPQueueManager;
import thaw.gui.IconBox;
import thaw.gui.Table;
import thaw.plugins.ToolbarModifier;
import thaw.plugins.index.LinkManagementHelper.BasicLinkAction;
import thaw.plugins.index.LinkManagementHelper.LinkAction;
import thaw.plugins.transferLogs.TransferManagementHelper.TransferAction;

public class LinkTable implements MouseListener, KeyListener, ActionListener {

	private JPanel panel;

	private Table table;

	private LinkListModel linkListModel = null;

	private LinkList linkList = null;

	private IndexBrowserPanel indexBrowser;

	private JPopupMenu rightClickMenu;

	private final List<LinkAction> rightClickActions = new ArrayList<LinkAction>();

	private JMenuItem gotoItem;

	private JMenuItem gotoCorrespondingItem;

	private ToolbarModifier toolbarModifier;

	private final List<LinkAction> toolbarActions = new ArrayList<LinkAction>();

	private int[] selectedRows;

	private Link firstSelectedLink = null;

	private int firstSelectedLinkCorrespondingIndexId = -1; /* hmm .. I should make it shorter ... */

	private final static String unknownStr = I18n.getMessage("thaw.common.unknown");

	public LinkTable(final FCPQueueManager queueManager, IndexBrowserPanel indexBrowser,
					 Config config) {
		this.indexBrowser = indexBrowser;

		linkListModel = new LinkListModel();
		table = new Table(config, "index_link_table", linkListModel);
		table.setShowGrid(false);
		table.setIntercellSpacing(new Dimension(0, 0));

		panel = new JPanel();
		panel.setLayout(new BorderLayout());

		panel.add(new JLabel(I18n.getMessage("thaw.plugin.index.linkList")), BorderLayout.NORTH);
		panel.add(new JScrollPane(table));

		table.addMouseListener(this);

		rightClickMenu = new JPopupMenu();

		toolbarModifier = new ToolbarModifier(indexBrowser.getMainWindow());

		JMenuItem item;
		JButton button;

		/* https://bugs.freenetproject.org/view.php?id=1625:
		 *  --- 0001625: Added index not in the correct category
		 *  If you add an index from the link list instead of the unknown index list,
		 *  it's added in the same folder than the index instead of "recently added".
		 *  ==> addToParent == false
		 */
		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.addIndexesFromLink"), IconBox.minAdd);
		button = new JButton(IconBox.indexReuse);
		button.setToolTipText(I18n.getMessage("thaw.plugin.index.addIndexesFromLink"));
		toolbarActions.add(new LinkManagementHelper.IndexAdder(button, queueManager,
				indexBrowser, false));

		toolbarModifier.addButtonToTheToolbar(button);
		rightClickMenu.add(item);
		rightClickActions.add(new LinkManagementHelper.IndexAdder(item, queueManager,
				indexBrowser, false));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.copyKeys"), IconBox.minCopy);
		rightClickMenu.add(item);
		rightClickActions.add(new LinkManagementHelper.PublicKeyCopier(item));

		item = new JMenuItem(I18n.getMessage("thaw.common.remove"), IconBox.minDelete);
		button = new JButton(IconBox.delete);
		button.setToolTipText(I18n.getMessage("thaw.common.remove"));
		toolbarActions.add(new LinkManagementHelper.LinkRemover(indexBrowser, button));
		toolbarModifier.addButtonToTheToolbar(button);
		rightClickMenu.add(item);
		rightClickActions.add(new LinkManagementHelper.LinkRemover(indexBrowser, item));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.editBlackList"));
		rightClickMenu.add(item);
		rightClickActions.add(new LinkManagementHelper.BlackListDisplayer(item, indexBrowser.getBlackList()));

		item = new JMenuItem(I18n.getMessage("thaw.plugin.index.addToBlackList"), IconBox.minStop);
		button = new JButton(IconBox.stop);
		button.setToolTipText(I18n.getMessage("thaw.plugin.index.addToBlackList"));
		toolbarActions.add(new LinkManagementHelper.ToBlackListAdder(button, indexBrowser));
		toolbarModifier.addButtonToTheToolbar(button);
		rightClickMenu.add(item);
		rightClickActions.add(new LinkManagementHelper.ToBlackListAdder(item, indexBrowser));

		gotoItem = new JMenuItem(I18n.getMessage("thaw.plugin.index.gotoIndex"));
		rightClickMenu.add(gotoItem);
		gotoItem.addActionListener(this);

		gotoCorrespondingItem = new JMenuItem(I18n.getMessage("thaw.plugin.index.gotoCorrespondingIndex"));
		rightClickMenu.add(gotoCorrespondingItem);
		gotoCorrespondingItem.addActionListener(this);

		updateRightClickMenu(null);
	}

	public ToolbarModifier getToolbarModifier() {
		return toolbarModifier;
	}

	public JPanel getPanel() {
		return panel;
	}

	protected void updateRightClickMenu(final List<Link> selectedLinks) {
		firstSelectedLink = selectedLinks != null && selectedLinks.size() > 0 ?
				selectedLinks.get(0) : null;

		for (LinkAction action : rightClickActions) {
			action.setTarget(selectedLinks);
		}

		gotoItem.setEnabled((linkList != null) && !(linkList instanceof Index));

		if (firstSelectedLink != null)
			firstSelectedLinkCorrespondingIndexId =
					Index.isAlreadyKnown(indexBrowser.getDb(),
							firstSelectedLink.getPublicKey());
		else
			firstSelectedLinkCorrespondingIndexId = -1;

		gotoCorrespondingItem.setEnabled((linkList != null)
				&& !(linkList instanceof Index)
				&& firstSelectedLinkCorrespondingIndexId >= 0);
	}

	protected void updateToolbar(final Vector selectedLinks) {
		LinkManagementHelper.LinkAction action;

		for (final Iterator it = toolbarActions.iterator();
			 it.hasNext(); ) {
			action = (LinkManagementHelper.LinkAction) it.next();
			action.setTarget(selectedLinks);
		}
	}

	protected Vector getSelectedLinks(final int[] selectedRows) {
		//final Vector srcList = linkList.getLinkList(null, false);
		final List<Link> srcList = linkListModel.getLinks();
		final Vector links = new Vector();

		for (int i = 0; i < selectedRows.length; i++) {
			final Link link = srcList.get(selectedRows[i]);
			links.add(link);
		}

		return links;
	}

	public void setLinkList(final LinkList linkList) {
		this.linkList = linkList;
		linkListModel.reloadLinkList(linkList);

		if (linkList != null)
			indexBrowser.getUnknownIndexList().addLinks(linkList);
	}

	public LinkList getLinkList() {
		return linkList;
	}

	public void mouseClicked(final MouseEvent e) {
		Vector selection;

		if (linkList == null) {
			selectedRows = null;
			return;
		}

		selectedRows = table.getSelectedRows();
		selection = getSelectedLinks(selectedRows);

		if ((e.getButton() == MouseEvent.BUTTON1) && linkList != null) {
			updateToolbar(selection);
			toolbarModifier.displayButtonsInTheToolbar();
		}

		if ((e.getButton() == MouseEvent.BUTTON3)
				&& (linkList != null)) {
			updateRightClickMenu(selection);
			rightClickMenu.show(e.getComponent(), e.getX(), e.getY());
		}
	}

	public void mouseEntered(final MouseEvent e) {
	}

	public void mouseExited(final MouseEvent e) {
	}

	public void mousePressed(final MouseEvent e) {
	}

	public void mouseReleased(final MouseEvent e) {
	}

	public void keyPressed(final KeyEvent e) {
	}

	public void keyReleased(final KeyEvent e) {
	}

	public void keyTyped(final KeyEvent e) {
	}

	public void actionPerformed(final ActionEvent e) {

		if (linkList == null) // don't forget that linkList == Index most of the time
			return;

		if (e.getSource() == gotoItem) {
			if (selectedRows.length <= 0)
				return;

			if (firstSelectedLink != null)
				indexBrowser.selectIndex(firstSelectedLink.getParentId());

			return;
		}

		if (e.getSource() == gotoCorrespondingItem) {
			if (selectedRows.length <= 0)
				return;

			if (firstSelectedLinkCorrespondingIndexId > 0) {
				indexBrowser.selectIndex(firstSelectedLinkCorrespondingIndexId);
			}
		}
	}

	public void refresh() {
		linkListModel.refresh();
	}

	public class LinkListModel extends AbstractTableModel implements Observer {

		private static final long serialVersionUID = 1L;

		public Vector columnNames;

		public List<Link> links = null; /* thaw.plugins.index.Link Vector */

		public LinkList linkList;

		public LinkListModel() {
			super();

			columnNames = new Vector();

			columnNames.add(I18n.getMessage("thaw.plugin.index.index"));
			columnNames.add(I18n.getMessage("thaw.plugin.index.category"));
			//columnNames.add(I18n.getMessage("thaw.common.key"));
		}

		public void reloadLinkList(final LinkList newLinkList) {

			linkList = newLinkList;
			links = null;

			this.refresh();

		}

		public List<Link> getLinks() {
			return links;
		}

		public int getRowCount() {
			if (links == null)
				return 0;

			return links.size();
		}

		public int getColumnCount() {
			return columnNames.size();
		}

		public String getColumnName(final int column) {
			return (String) columnNames.get(column);
		}

		public Object getValueAt(final int row, final int column) {
			final Link link = links.get(row);

			switch (column) {
				case (0):
					return link.getIndexName();
				case (1):
					return (link.getCategory() != null ? link.getCategory() : unknownStr);
				//case(2): return link.getPublicKey();
				default:
					return null;
			}
		}

		public void refresh() {
			if (linkList != null) {
				links = linkList.getLinkList(null, false);
			} else
				links = null;

			final TableModelEvent event = new TableModelEvent(this);
			this.refresh(event);
		}

		public void refresh(final int row) {
			final TableModelEvent event = new TableModelEvent(this, row);
			this.refresh(event);
		}

		public void refresh(final TableModelEvent e) {
			fireTableChanged(e);
		}

		public void update(final Observable o, final Object param) {
			if (param instanceof Link) {

				//link.deleteObserver(this);
				//link.addObserver(this);
			}

			this.refresh(); /* TODO : Do it more nicely ... :) */
		}
	}

}

