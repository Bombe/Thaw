package thaw.plugins.peerMonitor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Observable;
import java.util.Vector;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;

import thaw.core.Config;
import thaw.core.I18n;
import thaw.fcp.FCPQueryManager;
import thaw.gui.GUIHelper;
import thaw.gui.IconBox;
import thaw.gui.MainWindow;
import thaw.plugins.PeerMonitor;
import thaw.plugins.ToolbarModifier;

/**
 * In fact, here is two panels : A panel with the peer list and a panel with
 * various details (ref, etc)
 */
public class PeerMonitorPanel extends Observable implements ActionListener, MouseListener {

	public final static String[] STR_STATUS = {
			"CONNECTED",
			"BACKED OFF",
			"TOO OLD",
			"TOO NEW",
			"DISCONNECTED",
			"NEVER CONNECTED"
	};

	public final static Color[] COLOR_STATUS = {
			new Color(0, 164, 0),  /* CONNECTED */
			Color.ORANGE,          /* BACKED OFF */
			Color.RED,             /* TOO OLD */
			Color.BLUE,            /* TOO NEW */
			Color.GRAY,            /* DISCONNECTED */
			Color.PINK             /* NEVER CONNECTED */
	};

	public final static Color JLIST_NODE_STAT_BACKGROUND = Color.WHITE;

	public final static Color JLIST_PEER_BACKGROUND = new Color(240, 240, 240);

	public final static int STR_INFO_MAX_LNG = 55;

	public final static int STR_NODENAME_MAX_LNG = 15;

	private JPanel peerPanel;

	private JPanel tabPanel;

	private JPanel mainPanel;

	private JList peerList;

	private JProgressBar thawMemBar;

	private JProgressBar nodeMemBar;

	private JLabel nodeThreads;

	private JLabel thawThreads;

	private JLabel detailsLabel;

	private JPanel detailsPanel;

	private JPopupMenu rightClickMenu;

	private Vector buttonActions;

	private JButton closeTabButton;

	private PeerMonitor peerMonitor;

	private ToolbarModifier toolbarModifier;

	private JButton foldButton;

	private final static String nodeMemoryStr = I18n.getMessage("thaw.plugin.peerMonitor.infos.nodeMemory");

	private final static String thawMemoryStr = I18n.getMessage("thaw.plugin.peerMonitor.infos.thawMemory");

	private final static String nodeThreadsStr = I18n.getMessage("thaw.plugin.peerMonitor.infos.nodeThreads");

	private final static String thawThreadsStr = I18n.getMessage("thaw.plugin.peerMonitor.infos.thawThreads");

	private final static String nodeStatsStr = I18n.getMessage("thaw.plugin.peerMonitor.nodeStats");

	public PeerMonitorPanel(PeerMonitor peerMonitor,
							FCPQueryManager queryManager,
							Config config,
							MainWindow mainWindow) {
		buttonActions = new Vector();

		this.peerMonitor = peerMonitor;

		toolbarModifier = new ToolbarModifier(mainWindow);

		tabPanel = new JPanel(new BorderLayout(10, 10));

		peerPanel = new JPanel(new BorderLayout());

		peerList = new JList();

		peerList.setCellRenderer(new PeerCellRenderer());
		//peerList.addListSelectionListener(this);

		Vector v = new Vector();

		v.add(I18n.getMessage("thaw.plugin.peerMonitor.nodeStats"));

		peerList.setListData(v);

		JLabel peerListLabel = new JLabel(I18n.getMessage("thaw.plugin.peerMonitor.peerList"));
		peerListLabel.setIcon(IconBox.peers);

		/* We are not the listener */
		/* te listener will be PeerMonitor */
		foldButton = new JButton(">");

		nodeMemBar = new JProgressBar(0, 100);
		thawMemBar = new JProgressBar(0, 100);
		nodeMemBar.setStringPainted(true);
		thawMemBar.setStringPainted(true);

		setMemBar(0, 134217728);

		nodeThreads = new JLabel("");
		thawThreads = new JLabel("");

		JPanel southSouth = new JPanel(new BorderLayout(5, 5));

		JPanel threadPanel = new JPanel(new GridLayout(2, 1));
		threadPanel.add(nodeThreads);
		threadPanel.add(thawThreads);

		southSouth.add(threadPanel, BorderLayout.CENTER);

		JPanel littleButtonPanel = new JPanel(new GridLayout(1, 2));

		JButton littleButton;

		littleButton = new JButton(IconBox.minAdd);
		littleButton.setToolTipText(I18n.getMessage("thaw.plugin.peerMonitor.addPeer"));
		buttonActions.add(new PeerHelper.PeerAdder(queryManager, mainWindow, littleButton));
		littleButtonPanel.add(littleButton);

		littleButton = new JButton(IconBox.minDelete);
		littleButton.setToolTipText(I18n.getMessage("thaw.plugin.peerMonitor.removePeer"));
		buttonActions.add(new PeerHelper.PeerRemover(queryManager, littleButton));
		littleButtonPanel.add(littleButton);

		southSouth.add(littleButtonPanel, BorderLayout.WEST);

		JPanel titlePanel = new JPanel(new BorderLayout(5, 5));
		titlePanel.add(peerListLabel, BorderLayout.CENTER);
		titlePanel.add(foldButton, BorderLayout.EAST);

		peerPanel.add(titlePanel, BorderLayout.NORTH);
		peerPanel.add(new JScrollPane(peerList), BorderLayout.CENTER);

		JPanel memPanel = new JPanel(new GridLayout(3, 1));
		memPanel.add(southSouth);
		memPanel.add(nodeMemBar);
		memPanel.add(thawMemBar);

		peerPanel.add(memPanel, BorderLayout.SOUTH);

		peerPanel.setPreferredSize(new Dimension(250, 200));

		mainPanel = new JPanel(new GridLayout(1, 1, 10, 10)); /* obsolete */

		detailsLabel = new JLabel();
		detailsPanel = new JPanel();

		JPanel globalDetailsPanel = new JPanel(new BorderLayout(5, 5));

		globalDetailsPanel.add(detailsLabel, BorderLayout.NORTH);
		globalDetailsPanel.add(new JScrollPane(detailsPanel), BorderLayout.CENTER);

		mainPanel.add(globalDetailsPanel);

		tabPanel.add(mainPanel, BorderLayout.CENTER);

		closeTabButton = new JButton(IconBox.minClose);
		closeTabButton.setBorderPainted(false);
		closeTabButton.addActionListener(this);

		JPanel headPanel = new JPanel(new BorderLayout());
		headPanel.add(new JLabel(""), BorderLayout.CENTER);
		headPanel.add(closeTabButton, BorderLayout.EAST);

		tabPanel.add(headPanel, BorderLayout.NORTH);

		rightClickMenu = new JPopupMenu();

		JMenuItem item;

		item = new JMenuItem(I18n.getMessage("thaw.plugin.peerMonitor.addPeer"),
				IconBox.minAdd);
		buttonActions.add(new PeerHelper.PeerAdder(queryManager, mainWindow, item));
		rightClickMenu.add(item);

		item = new JMenuItem(I18n.getMessage("thaw.plugin.peerMonitor.removePeer"),
				IconBox.minDelete);
		buttonActions.add(new PeerHelper.PeerRemover(queryManager, item));
		rightClickMenu.add(item);

		peerList.addMouseListener(this);

		JButton toolbarButton;

		toolbarButton = new JButton(IconBox.add);
		toolbarButton.setToolTipText(I18n.getMessage("thaw.plugin.peerMonitor.addPeer"));
		buttonActions.add(new PeerHelper.PeerAdder(queryManager, mainWindow, toolbarButton));
		toolbarModifier.addButtonToTheToolbar(toolbarButton);

		toolbarButton = new JButton(IconBox.delete);
		toolbarButton.setToolTipText(I18n.getMessage("thaw.plugin.peerMonitor.removePeer"));
		buttonActions.add(new PeerHelper.PeerRemover(queryManager, toolbarButton));
		toolbarModifier.addButtonToTheToolbar(toolbarButton);
	}

	public JButton getFoldButton() {
		return foldButton;
	}

	public void setMemBar(long used, long max) {
		int pourcent;

		/* node mem bar */

		pourcent = (int) ((used * 100) / max);

		nodeMemBar.setString(nodeMemoryStr + ": "
				+ GUIHelper.getPrintableSize(used)
				+ " / "
				+ GUIHelper.getPrintableSize(max));

		nodeMemBar.setValue(pourcent);


		/* thaw mem bar */

		max = Runtime.getRuntime().maxMemory();

		if (max == Long.MAX_VALUE) {
			max = Runtime.getRuntime().totalMemory();
		}

		used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

		pourcent = (int) ((used * 100) / max);

		thawMemBar.setString(thawMemoryStr + ": "
				+ GUIHelper.getPrintableSize(used)
				+ " / "
				+ GUIHelper.getPrintableSize(max));

		thawMemBar.setValue(pourcent);
	}

	public void setNmbThreads(int nmbNodeThreads) {
		nodeThreads.setText(nodeThreadsStr
				+ " : " + Integer.toString(nmbNodeThreads));
		thawThreads.setText(thawThreadsStr
				+ " : " + Integer.toString(Thread.activeCount()));
	}

	protected class PeerCellRenderer extends JLabel implements ListCellRenderer {

		/**
		 *
		 */
		private static final long serialVersionUID = 2631236843498183587L;

		public PeerCellRenderer() {
			setOpaque(true);
		}

		public Component
		getListCellRendererComponent(JList list,
									 Object value, // value to display
									 int index,    // cell index
									 boolean iss,  // is selected
									 boolean chf)  // cell has focus?
		{
			if (value instanceof String) {
				setText((String) value);
				setForeground(Color.BLACK);
				setBackground(JLIST_NODE_STAT_BACKGROUND);
			}

			if (value instanceof Peer) {
				String txt = value.toString();

				/*
				if (txt.length() > 25) {
					txt = txt.substring(0, 25) + "(...)";
				}
				*/

				setText(txt);
				setForeground(((Peer) value).getTextColor());
				setBackground(JLIST_PEER_BACKGROUND);
			}

			// Set a border if the
			//list item is selected
			if (iss)
				setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
			else
				setBorder(BorderFactory.createLineBorder(list.getBackground(), 1));

			return this;
		}
	}

	private Vector peers;

	private Hashtable nodeInfos = null;

	/** \param peers : Hashtable containing Hashtable containing the parameter list */
	public synchronized void setPeerList(Hashtable pL) {
		peers = new Vector();

		peers.add(nodeStatsStr);

		/* TODO : dirty : should use comparator, etc */
		for (int i = 0; i < STR_STATUS.length; i++) {
			for (Enumeration e = pL.elements();
				 e.hasMoreElements(); ) {
				Hashtable ht = (Hashtable) e.nextElement();

				if (STR_STATUS[i].equals(ht.get("volatile.status"))) {
					peers.add(new Peer(ht));
				}
			}
		}

		/* can it happen ?! */
		if (peerList != null && peers != null)
			peerList.setListData(peers);
	}

	public synchronized void setNodeInfos(Hashtable infos) {
		if (nodeInfos == null) { /* first time */
			displayInfos(I18n.getMessage("thaw.plugin.peerMonitor.nodeInfos"), infos);
		}

		nodeInfos = infos;
	}

	public JPanel getTabPanel() {
		return tabPanel;
	}

	public JPanel getPeerListPanel() {
		return peerPanel;
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == closeTabButton) {
			peerMonitor.hideTab();
		}
	}

	/**
	 * @return null if it must not be displayed ; else an array with two elements
	 *         (key translated + value translated)
	 */
	public String[] getTranslation(String key, String value) {
		String[] result = null;

		/* PEERS */

		if ("volatile.lastRoutingBackoffReason".equals(key))
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.peer.lastRoutingBackoffReason"),
					value
			};

		else if ("volatile.routingBackoffPercent".equals(key))
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.peer.routingBackoffPercent"),
					value + "%"
			};

		else if ("version".equals(key)) /* used also for the node */
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.peer.version"),
					value
			};

		else if ("volatile.status".equals(key))
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.peer.status"),
					value
			};

		else if ("myName".equals(key))
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.peer.myName"),
					value
			};

		else if ("physical.udp".equals(key)) /* used also for the node */
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.peer.physical.udp"),
					value
			};

		else if ("volatile.averagePingTime".equals(key)) /* used also for the node */
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.peer.averagePingTime"),
					Integer.toString(new Float(value).intValue()) + " ms"
			};

		else if ("volatile.idle".equals(key))
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.peer.idle"),
					"~" + GUIHelper.getPrintableTime(Long.parseLong(value) / 1000)
			};

		else if ("volatile.totalBytesOut".equals(key)) {
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.peer.totalBytesOut"),
					"~" + GUIHelper.getPrintableSize(Long.parseLong(value))
			};
		} else if ("volatile.totalBytesIn".equals(key)) {
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.peer.totalBytesIn"),
					"~" + GUIHelper.getPrintableSize(Long.parseLong(value))
			};
		} else if ("volatile.overloadProbability".equals(key)) {
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.peer.overloadProbability"),
					value
			};
		}

		/* NODE */

		else if ("volatile.overallSize".equals(key))
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.node.overallSize"),
					"~" + GUIHelper.getPrintableSize(Long.parseLong(value))
			};

		else if ("volatile.uptimeSeconds".equals(key)) {
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.node.uptimeSeconds"),
					"~" + GUIHelper.getPrintableTime(Long.parseLong(value))
			};
		} else if ("volatile.networkSizeEstimateSession".equals(key)) {
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.node.networkSizeEstimateSession"),
					value
			};
		} else if ("volatile.runningThreadCount".equals(key)) {
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.node.runningThreads"),
					value
			};
		} else if ("myName".equals(key)) {
			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.node.myName"),
					value
			};
		} else if ("volatile.totalInputBytes".equals(key)) {

			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.totalInputBytes"),
					GUIHelper.getPrintableSize(Long.parseLong(value))
			};
		} else if ("volatile.backedOffPercent".equals(key)) {

			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.backedOffPercent"),
					value + "%"
			};
		} else if ("volatile.totalOutputBytes".equals(key)) {

			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.totalOutputBytes"),
					GUIHelper.getPrintableSize(Long.parseLong(value))
			};

		} else if ("volatile.swapsPerMinute".equals(key)) {

			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.swapPerMinute"),
					value
			};

		} else if ("volatile.locationChangePerMinute".equals(key)) {

			result = new String[] {
					I18n.getMessage("thaw.plugin.peerMonitor.infos.locationChangePerMinute"),
					value
			};

		}


		/*
		  if (advanced) {
		    if (result == null)
		      result = new String[] { key, value };
		    else
		      result[0] = result[0] + " ("+key+")";
		  }
		*/

		return result;
	}

	private static JLabel makeInfoLabel(String txt) {
		if (txt.length() > STR_INFO_MAX_LNG)
			txt = txt.substring(0, STR_INFO_MAX_LNG) + "(...)";

		JLabel lb = new JLabel(txt);
		return lb;
	}

	public void displayInfos(String title, Hashtable ht) {
		if (ht == null)
			return;

		Vector v = new Vector();

		for (Enumeration e = ht.keys();
			 e.hasMoreElements(); ) {
			String key = (String) e.nextElement();

			String[] val = getTranslation(key, (String) ht.get(key));

			if (val != null)
				v.add(val);
		}

		detailsPanel.removeAll();

		detailsPanel.setLayout(new GridLayout(v.size() + 1, 2));

		detailsLabel.setText(title);
		detailsLabel.setIcon(IconBox.computer);

		for (Iterator i = v.iterator();
			 i.hasNext(); ) {
			String[] val = (String[]) i.next();

			detailsPanel.add(makeInfoLabel(val[0] + ":"));
			detailsPanel.add(makeInfoLabel(val[1]));
		}

		mainPanel.validate();
		detailsPanel.validate();
	}

	private void clicked() {

		if (peerList.getSelectedValue() instanceof Peer) {
			updateButtonState(((Peer) peerList.getSelectedValue()));
		} else
			updateButtonState(null);

		if (peerList.getSelectedValue() == null
				|| !(peerList.getSelectedValue() instanceof Peer)) {
			displayInfos(I18n.getMessage("thaw.plugin.peerMonitor.nodeInfos"), nodeInfos);
		} else {
			Peer peer;

			if ((peer = ((Peer) peerList.getSelectedValue())) != null) {

				String peerName = peer.toString();

				displayInfos(I18n.getMessage("thaw.plugin.peerMonitor.peerInfos") + " '" + peerName + "':",
						peer.getParameters());
			}
		}

		setChanged();
		notifyObservers();
	}

	public void updateButtonState(Peer target) {
		for (Iterator it = buttonActions.iterator();
			 it.hasNext(); ) {
			PeerHelper.PeerAction a = ((PeerHelper.PeerAction) it.next());
			a.setTarget(target);
		}
	}

	public void mouseClicked(final MouseEvent e) {
		clicked();
	}

	public void mouseEntered(final MouseEvent e) {
	}

	public void mouseExited(final MouseEvent e) {
	}

	public void mousePressed(final MouseEvent e) {
		showPopupMenu(e);
	}

	public void mouseReleased(final MouseEvent e) {
		showPopupMenu(e);
	}

	protected void showPopupMenu(final MouseEvent e) {
		if (e.isPopupTrigger()) {
			rightClickMenu.show(e.getComponent(), e.getX(), e.getY());
		}
	}

	public void showToolbarButtons() {
		toolbarModifier.displayButtonsInTheToolbar();
	}

	public void hideToolbarButtons() {
		toolbarModifier.hideButtonsInTheToolbar();
	}

}
