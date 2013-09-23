package thaw.plugins.peerMonitor;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import thaw.core.I18n;
import thaw.fcp.FCPAddPeer;
import thaw.fcp.FCPQueryManager;
import thaw.fcp.FCPRemovePeer;
import thaw.gui.MainWindow;

public class PeerHelper {

	public interface PeerAction extends ActionListener {

		public void setTarget(Peer peer);
	}

	public static class PeerAdder implements PeerAction {

		private FCPQueryManager queryManager;

		private AbstractButton src;

		private MainWindow mainWindow;

		private JDialog dialog;

		private JButton okButton;

		private JButton cancelButton;

		private JTextArea refArea;

		private JTextField urlField;

		public PeerAdder(FCPQueryManager queryManager, MainWindow mainWindow, AbstractButton actionSource) {
			this.queryManager = queryManager;
			this.src = actionSource;
			this.mainWindow = mainWindow;

			if (actionSource != null)
				actionSource.addActionListener(this);
		}

		public void setTarget(Peer peer) {
			/* we ignore */
		}

		public void actionPerformed(ActionEvent e) {
			if (e.getSource() == src) {
				dialog = new JDialog(mainWindow.getMainFrame(),
						I18n.getMessage("thaw.plugin.peerMonitor.addPeer"));

				dialog.getContentPane().setLayout(new BorderLayout(5, 5));

				JPanel centerPanel = new JPanel(new BorderLayout());

				JPanel buttonPanel = new JPanel(new GridLayout(1, 2));

				okButton = new JButton(I18n.getMessage("thaw.common.ok"));
				okButton.addActionListener(this);
				buttonPanel.add(okButton);

				cancelButton = new JButton(I18n.getMessage("thaw.common.cancel"));
				cancelButton.addActionListener(this);
				buttonPanel.add(cancelButton);

				refArea = new JTextArea("");

				JLabel label = new JLabel(I18n.getMessage("thaw.plugin.peerMonitor.enterRef"));

				centerPanel.add(label, BorderLayout.NORTH);
				centerPanel.add(new JScrollPane(refArea), BorderLayout.CENTER);

				JPanel southPanel = new JPanel(new GridLayout(2, 1));

				JPanel urlPanel = new JPanel(new BorderLayout());
				JLabel urlLabel = new JLabel("URL : ");
				urlField = new JTextField("http://");

				urlPanel.add(urlLabel, BorderLayout.WEST);
				urlPanel.add(urlField, BorderLayout.CENTER);

				southPanel.add(urlPanel);
				southPanel.add(buttonPanel);

				dialog.getContentPane().add(centerPanel, BorderLayout.CENTER);
				dialog.getContentPane().add(southPanel, BorderLayout.SOUTH);

				dialog.setSize(700, 300);
				dialog.setVisible(true);

				return;
			}

			if (e.getSource() == okButton) {
				String ref;

				ref = refArea.getText();

				if (urlField.getText() != null
						&& !"http://".equals(urlField.getText())) {
					ref = "URL=" + urlField.getText();
				}

				ref = ref.trim();

				if (looksValid(ref)) {
					addPeer(queryManager, ref);
					dialog.setVisible(false);
				} else {
					new thaw.gui.WarningWindow(dialog,
							I18n.getMessage("thaw.plugin.peerMonitor.invalidRef"));
				}
			}

			if (e.getSource() == cancelButton) {
				dialog.setVisible(false);
			}
		}
	}

	public static boolean looksValid(String ref) {
		if (ref.startsWith("URL=") || ref.endsWith("End"))
			return true;

		return false;
	}

	public static void addPeer(FCPQueryManager queryManager, String ref) {
		FCPAddPeer addPeer = new FCPAddPeer(ref, queryManager);
		addPeer.start();

		/* see you later :) */
		/* (ie when the next ListPeers will be done) */
	}

	public static class PeerRemover implements PeerAction {

		private FCPQueryManager queryManager;

		private AbstractButton src;

		private Peer target;

		public PeerRemover(FCPQueryManager queryManager, AbstractButton actionSource) {
			this.queryManager = queryManager;
			this.src = actionSource;

			if (actionSource != null) {
				actionSource.addActionListener(this);
				actionSource.setEnabled(false);
			}
		}

		public void setTarget(Peer peer) {
			if (src != null) {
				src.setEnabled(peer != null);
			}

			target = peer;
		}

		public void actionPerformed(ActionEvent e) {
			if (target == null)
				return;

			int ret = JOptionPane.showConfirmDialog(null,
					I18n.getMessage("thaw.plugin.peerMonitor.confirmationForRemove"),
					I18n.getMessage("thaw.warning.title"),
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE);

			if (ret != JOptionPane.YES_OPTION) {
				return;
			}

			removePeer(queryManager, target.getIdentity());
		}
	}

	public static void removePeer(FCPQueryManager queryManager, String peer) {
		FCPRemovePeer addPeer = new FCPRemovePeer(peer, queryManager);
		addPeer.start();
	}

}

