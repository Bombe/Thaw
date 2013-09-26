package thaw.plugins.index;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import thaw.core.Config;
import thaw.core.Core;
import thaw.core.I18n;
import thaw.core.Logger;
import thaw.fcp.FCPQueueManager;
import thaw.gui.MainWindow;
import thaw.plugins.Hsqldb;

public class IndexBrowserPanel implements TreeSelectionListener {

	private IndexTree indexTree;

	private Tables tables;

	private DetailPanel detailPanel;

	private UnknownIndexList unknownList;

	private BlackList blackList;

	private CommentTab commentTab;

	private JSplitPane split;

	private JPanel listAndDetails;

	private JSplitPane leftSplit;

	private JPanel globalPanel;

	private Hsqldb db;

	private FCPQueueManager queueManager;

	private Config config;

	private MainWindow mainWindow;

	private Core core;

	public IndexBrowserPanel(final Hsqldb db, final Core core) {
		this.db = db;
		this.core = core;
		this.queueManager = core.getQueueManager();
		this.config = core.getConfig();
		this.mainWindow = core.getMainWindow();

		blackList = new BlackList(db, core, this);

		unknownList = new UnknownIndexList(queueManager, this);

		indexTree = new IndexTree(I18n.getMessage("thaw.plugin.index.indexes"),
				false, queueManager, this, config);

		commentTab = new CommentTab(core.getConfig(),
				core.getQueueManager(),
				this);

		leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
				indexTree.getPanel(),
				unknownList.getPanel());

		listAndDetails = new JPanel();
		listAndDetails.setLayout(new BorderLayout(10, 10));

		tables = new Tables(false, queueManager, this, config);
		detailPanel = new DetailPanel(queueManager, this);

		listAndDetails.add(tables.getPanel(), BorderLayout.CENTER);
		listAndDetails.add(detailPanel.getPanel(), BorderLayout.SOUTH);

		split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
				leftSplit, listAndDetails);

		indexTree.addTreeSelectionListener(this);

		globalPanel = new JPanel(new BorderLayout());
		globalPanel.add(split, BorderLayout.CENTER);
	}

	public Core getCore() {
		return core;
	}

	public void restoreState() {
		if (config.getValue("indexBrowserPanelSplitPosition") != null)
			split.setDividerLocation(Integer.parseInt(config.getValue("indexBrowserPanelSplitPosition")));

		leftSplit.setSize(400, MainWindow.DEFAULT_SIZE_Y - 400);
		leftSplit.setResizeWeight(0.5);

		if (config.getValue("indexTreeUnknownListSplitLocation") == null) {
			leftSplit.setDividerLocation((0.5));
		} else {
			try {
				leftSplit.setDividerLocation(Integer.parseInt(config.getValue("indexTreeUnknownListSplitLocation")));
			} catch (IllegalArgumentException e) { /* TODO: Find why it happens */
				Logger.error(this, "Exception while setting indexTree split location");
			}
		}

		leftSplit.setResizeWeight(0.5);

		tables.restoreState();
	}

	public Hsqldb getDb() {
		return db;
	}

	public Config getConfig() {
		return config;
	}

	public Tables getTables() {
		return tables;
	}

	public IndexTree getIndexTree() {
		return indexTree;
	}

	public UnknownIndexList getUnknownIndexList() {
		return unknownList;
	}

	public DetailPanel getDetailPanel() {
		return detailPanel;
	}

	public CommentTab getCommentTab() {
		return commentTab;
	}

	public MainWindow getMainWindow() {
		return mainWindow;
	}

	public BlackList getBlackList() {
		return blackList;
	}

	public JPanel getPanel() {
		return globalPanel;
	}

	public void stopAllThreads() {
		tables.stopRefresh();
		blackList.hidePanel();
	}

	public void saveState() {
		config.setValue("indexBrowserPanelSplitPosition", Integer.toString(split.getDividerLocation()));
		int splitLocation;

		splitLocation = leftSplit.getDividerLocation();

		config.setValue("indexTreeUnknownListSplitLocation",
				Integer.toString(splitLocation));

		tables.saveState();
	}

	protected void setList(final FileAndLinkList l) {
		tables.setList(l);

		detailPanel.setTarget(l);

		if (l instanceof Index) {
			commentTab.setIndex((Index) l);
		} else {
			commentTab.setIndex(null);
		}
	}

	protected void setFileList(final FileList l) {
		tables.setFileList(l);
	}

	protected void setLinkList(final LinkList l) {
		tables.setLinkList(l);
	}

	public void valueChanged(final TreeSelectionEvent e) {
		final TreePath path = e.getPath();

		setList(null);

		if (path == null) {
			Logger.notice(this, "Path null ?");
			return;
		}

		final IndexTreeNode node = (IndexTreeNode) (path.getLastPathComponent());

		if (node == null) {
			Logger.notice(this, "Node null ?");
			return;
		}

		if (node instanceof FileAndLinkList) {
			Logger.debug(this, "FileAndLinkList !");
			setList((FileAndLinkList) node);
		} else {
			if (node instanceof FileList) {
				Logger.debug(this, "FileList !");
				setFileList((FileList) node);
			}

			if (node instanceof LinkList) {
				Logger.debug(this, "LinkList !");
				setLinkList((LinkList) node);
			}
		}

		tables.getSearchBar().clear();
	}

	/** Called by IndexBrowser when the panel become visible */
	public void setVisible(boolean visibility) {
		if (visibility) {
			indexTree.getToolbarModifier().displayButtonsInTheToolbar();
		} else {
			// one of these foor may be the buttons owner ?
			indexTree.getToolbarModifier().hideButtonsInTheToolbar();
			tables.getLinkTable().getToolbarModifier().hideButtonsInTheToolbar();
			tables.getFileTable().getToolbarModifier().hideButtonsInTheToolbar();
			unknownList.getToolbarModifier().hideButtonsInTheToolbar();
		}
	}

	/**
	 * will call IndexTree.selectIndex(id) and next tell to filetable and linktable
	 * to display the content of the specified index
	 */
	public boolean selectIndex(int id) {
		Index index = indexTree.selectIndex(id);

		if (index != null) {
			setList(index);
			return true;
		} else
			return false;
	}

}
