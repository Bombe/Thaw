package thaw.plugins;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Observable;
import java.util.Observer;
import javax.swing.ImageIcon;
import javax.swing.JButton;

import thaw.core.Core;
import thaw.core.I18n;
import thaw.core.Logger;
import thaw.core.Plugin;
import thaw.core.ThawRunnable;
import thaw.core.ThawThread;
import thaw.fcp.FCPGetNode;
import thaw.fcp.FCPListPeers;
import thaw.fcp.FCPQueryManager;
import thaw.gui.IconBox;
import thaw.plugins.peerMonitor.PeerMonitorPanel;

public class PeerMonitor implements Plugin, Observer, ActionListener {

	public final static int DEFAULT_REFRESH_RATE = 10; /* in sec */

	private PeerMonitorPanel peerPanel;

	private Core core;

	private boolean running = false;

	private JButton unfoldButton;

	private boolean folded = false;

	public PeerMonitor() {

	}

	protected class DisplayRefresher implements Observer, ThawRunnable {

		private FCPGetNode getNode = null;

		private FCPListPeers listPeers = null;

		public DisplayRefresher() {

		}

		public void run() {
			while (running) {
				if (getNode == null) {
					getNode = new FCPGetNode(false /* private */, true /* volatile */, core.getQueueManager().getQueryManager());
					getNode.addObserver(this);
				}

				getNode.start();

				if (listPeers == null) {
					listPeers = new FCPListPeers(false /* metadata */, true/* volatile */, core.getQueueManager().getQueryManager());
					listPeers.addObserver(this);
				}

				if (listPeers.hasEnded())
					listPeers.start();

				try {
					Thread.sleep(DEFAULT_REFRESH_RATE * 1000);
				} catch (InterruptedException e) {
					/* \_o< \_o< \_o< */
				}

				if (!running)
					return;
			}
		}

		public void update(Observable o, Object param) {
			if (!running)
				return;

			if (o instanceof FCPGetNode) {

				FCPGetNode gN = (FCPGetNode) o;

				peerPanel.setMemBar(gN.getUsedJavaMemory(), gN.getMaxJavaMemory());
				peerPanel.setNmbThreads(gN.getNmbThreads());
				peerPanel.setNodeInfos(gN.getAllParameters());

			}

			if (o instanceof FCPListPeers) {

				FCPListPeers lP = (FCPListPeers) o;

				peerPanel.setPeerList(lP.getPeers());
			}
		}

		public void stop() {
			running = false;
		}
	}

	public boolean run(Core core) {
		FCPQueryManager queryManager = core.getQueueManager().getQueryManager();
		this.core = core;

		core.getConfig().addListener("advancedMode", this);

		unfoldButton = new JButton("<");
		unfoldButton.addActionListener(this);

		peerPanel = new PeerMonitorPanel(this, queryManager, core.getConfig(),
				core.getMainWindow());

		peerPanel.addObserver(this);
		peerPanel.getFoldButton().addActionListener(this);

		core.getMainWindow().addComponent(peerPanel.getPeerListPanel(),
				BorderLayout.EAST);

		running = true;
		Thread th = new Thread(new ThawThread(new DisplayRefresher(),
				"Peer monitor refresh",
				this));
		th.start();

		if (core.getConfig().getValue("peerMonitorFolded") != null) {
			boolean f = Boolean.valueOf(core.getConfig().getValue("peerMonitorFolded"));
			if (f)
				foldPanel();
		}

		return true;
	}

	public void stop() {
		hideTab();
		if (!folded)
			core.getMainWindow().removeComponent(peerPanel.getPeerListPanel());
		else
			core.getMainWindow().removeComponent(unfoldButton);

		core.getConfig().setValue("peerMonitorFolded", Boolean.toString(folded));

		running = false;
	}

	public String getNameForUser() {
		return I18n.getMessage("thaw.plugin.peerMonitor.peerMonitor");
	}

	public ImageIcon getIcon() {
		return IconBox.peers;
	}

	private boolean tabVisible = false;

	public void update(Observable o, Object param) {
		core.getMainWindow().addTab(I18n.getMessage("thaw.plugin.peerMonitor.peerMonitor"),
				IconBox.peers,
				peerPanel.getTabPanel());
		core.getMainWindow().setSelectedTab(peerPanel.getTabPanel());

		tabVisible = true;

		peerPanel.showToolbarButtons();
	}

	public void hideTab() {
		if (tabVisible) {
			peerPanel.hideToolbarButtons();

			core.getMainWindow().removeTab(peerPanel.getTabPanel());
			tabVisible = false;
		}
	}

	public void foldPanel() {
		Logger.info(this, "Folding peer monitor panel");
		core.getMainWindow().removeComponent(peerPanel.getPeerListPanel());
		core.getMainWindow().getMainFrame().validate();
		core.getMainWindow().addComponent(unfoldButton,
				BorderLayout.EAST);
		core.getMainWindow().getMainFrame().validate();
		folded = true;
	}

	public void unfoldPanel() {
		Logger.info(this, "Unfolding peer monitor panel");
		core.getMainWindow().removeComponent(unfoldButton);
		core.getMainWindow().getMainFrame().validate();
		core.getMainWindow().addComponent(peerPanel.getPeerListPanel(),
				BorderLayout.EAST);
		core.getMainWindow().getMainFrame().validate();
		folded = false;
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == peerPanel.getFoldButton())
			foldPanel();
		else if (e.getSource() == unfoldButton)
			unfoldPanel();
	}

	public void setNodeVersion(String version) {

	}

	/** check if it's not more recent than the node version */
	public void checkPeerVersion(String version) {

	}
}
