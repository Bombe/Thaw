package thaw.plugins;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.WindowConstants;

import thaw.core.Core;
import thaw.core.I18n;
import thaw.core.Logger;
import thaw.fcp.FCPClientGet;
import thaw.fcp.FreenetURIHelper;
import thaw.gui.IconBox;
import thaw.plugins.fetchPlugin.FetchPanel;

public class FetchPlugin implements thaw.core.Plugin, ActionListener {

	public final static int MIN_SLASH_POSITION = 80;

	private Core core;

	private FetchPanel fetchPanel = null;

	private JFrame fetchFrame = null;

	private JButton buttonInToolBar = null;

	private JMenuItem menuItem = null;

	private QueueWatcher queueWatcher;

	public FetchPlugin() {

	}

	public boolean run(final Core core) {
		this.core = core;

		Logger.info(this, "Starting plugin \"FetchPlugin\" ...");

		core.getConfig().addListener("advancedMode", this);

		fetchPanel = new FetchPanel(core, this);

		// Prepare the frame

		fetchFrame = new JFrame(I18n.getMessage("thaw.common.download"));
		fetchFrame.setVisible(false);
		fetchFrame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		fetchFrame.setContentPane(fetchPanel.getPanel());
		fetchFrame.setSize(650, 500);

		// Add the button to the toolbar when the QueueWatch tab is selected
		buttonInToolBar = new JButton(IconBox.downloads);
		buttonInToolBar.setToolTipText(I18n.getMessage("thaw.common.download"));
		buttonInToolBar.addActionListener(this);

		menuItem = new JMenuItem(I18n.getMessage("thaw.common.addDownloads"), IconBox.minDownloads);
		menuItem.addActionListener(this);

		if (core.getPluginManager().getPlugin("thaw.plugins.QueueWatcher") == null) {
			Logger.notice(this, "Loading QueueWatcher plugin");

			if (core.getPluginManager().loadPlugin("thaw.plugins.QueueWatcher") == null
					|| !core.getPluginManager().runPlugin("thaw.plugins.QueueWatcher")) {
				Logger.error(this, "Unable to load thaw.plugins.QueueWatcher !");
				return false;
			}
		}

		queueWatcher = (QueueWatcher) core.getPluginManager().getPlugin("thaw.plugins.QueueWatcher");

		queueWatcher.addButtonToTheToolbar(buttonInToolBar, 0);
		queueWatcher.addMenuItemToTheDownloadTable(menuItem);
		queueWatcher.addButtonListener(QueueWatcher.DOWNLOAD_PANEL, this);

		return true;
	}

	public void stop() {
		queueWatcher.removeButtonListener(QueueWatcher.DOWNLOAD_PANEL, this);

		Logger.info(this, "Stopping plugin \"FetchPlugin\" ...");

		if (queueWatcher != null)
			queueWatcher.removeButtonFromTheToolbar(buttonInToolBar);

		fetchFrame.setVisible(false);
		fetchFrame.dispose();
		fetchFrame = null;
	}

	public String getNameForUser() {
		return I18n.getMessage("thaw.common.download");
	}

	public void actionPerformed(final ActionEvent e) {
		fetchFrame.setVisible(true);
	}

	public void fetchFiles(final String[] keys, final int priority,
						   final int persistence, final boolean globalQueue,
						   final String destination) {

		boolean somethingStarted = false;

		for (int i = 0; i < keys.length; i++) {
			if (keys[i].length() < 10 && !keys[i].startsWith("KSK@"))
				continue;

			final String[] subKey = keys[i].split("\\?"); /* Because of VolodyA :p */

			final String key = FreenetURIHelper.cleanURI(subKey[0]);

			if (key == null || !FreenetURIHelper.isAKey(key))
				continue;

			if (!key.startsWith("KSK@")) {
				int slash_pos = key.indexOf("/");

				if (slash_pos < 0)
					continue;
			}

			core.getQueueManager().addQueryToThePendingQueue(new FCPClientGet.Builder(core.getQueueManager())
					.setKey(key)
					.setPriority(priority)
					.setPersistence(persistence)
					.setGlobalQueue(globalQueue)
					.setMaxRetries(-1)
					.setDestinationDir(destination)
					.build());
			somethingStarted = true;
		}

		fetchFrame.setVisible(false);

		if (!somethingStarted) {
			new thaw.gui.WarningWindow(core,
					I18n.getMessage("thaw.plugin.fetch.noValidURI"));
			return;
		}

	}

	public javax.swing.ImageIcon getIcon() {
		return thaw.gui.IconBox.downloads;
	}
}
