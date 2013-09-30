package thaw.plugins;

import static thaw.fcp.FCPQuery.Type.UPLOAD;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import thaw.core.Config;
import thaw.core.Core;
import thaw.core.I18n;
import thaw.core.LogListener;
import thaw.core.Logger;
import thaw.core.Main;
import thaw.core.Plugin;
import thaw.core.PluginManager;
import thaw.core.ThawRunnable;
import thaw.core.ThawThread;
import thaw.fcp.FCPTransferQuery;
import thaw.gui.IconBox;
import thaw.gui.SysTrayIcon;
import thaw.gui.TransferProgressBar;
import thaw.gui.WarningWindow;

public class TrayIcon implements Plugin,
		MouseListener,
		WindowListener,
		ActionListener,
        LogListener {

	private Core core;

	private Config config;

	private SysTrayIcon icon;

	private JDialog dialog;

	private JButton closeDialog;

	public final static int DIALOG_X = 500;

	public final static int DIALOG_Y = 800;

	public TrayIcon() {

	}

	public boolean run(Core core) {
		this.core = core;
		this.config = core.getConfig();

		if (!Core.checkJavaVersion(1, 6)) {
			new WarningWindow(core,
					I18n.getMessage("thaw.plugin.trayIcon.java1.6"));
			return false;
		}

		if (config.getValue("disableTrayIconPopups") == null)
			config.setValue("disableTrayIconPopups", "false");

		icon = new SysTrayIcon(IconBox.blueBunny);
		icon.setToolTip("Thaw " + Main.getVersion());
		icon.addMouseListener(this);

		core.getMainWindow().addWindowListener(this);

		icon.setVisible(true);

		Logger.addLogListener(this);

		return true;
	}

	public void stop() {
		if (icon == null)
			return;

		Logger.removeLogListener(this);

		core.getMainWindow().removeWindowListener(this);
		icon.removeMouseListener(this);

		icon.setVisible(false);

		if (!core.getMainWindow().isVisible()) {
			Logger.info(this, "Making main window visible again");
			core.getMainWindow().setVisible(true);
		}

		Logger.addLogListener(this);
	}

	public static boolean popMessage(PluginManager pluginManager,
									 String title,
									 String message) {
		return popMessage(pluginManager, title, message, SysTrayIcon.MSG_NONE);
	}

	/**
	 * that's an helper to make my life easier : it tries to find a loaded instance
	 * of this plugin, it succeed, it tries to pop the message with it. Else if
	 * returns false.
	 *
	 * @param msgType
	 * 		see SysTrayIcon
	 */
	public static boolean popMessage(PluginManager pluginManager,
									 String title,
									 String message,
									 int msgType) {
		TrayIcon plugin = (TrayIcon) pluginManager.getPlugin("thaw.plugins.TrayIcon");

		if (plugin == null)
			return false;

		return plugin.popMessage(title, message, msgType);
	}

	/** Made to be also used by other plugins */
	public boolean popMessage(String title, String message, int msgType) {
		if (icon == null || !icon.canWork())
			return false;

		String cfg;

		if ((cfg = config.getValue("disableTrayIconPopups")) != null)
			if (Boolean.TRUE.equals(Boolean.valueOf(cfg)))
				return false;

		icon.popMessage(title, message, msgType);

		return true;
	}

	public void newLogLine(int level, Object src, String line) {
		if (level > Logger.LOG_LEVEL_ERROR || src == this || src == icon)
			return;

		int msgType = ((level == 0) ? SysTrayIcon.MSG_ERROR : SysTrayIcon.MSG_WARNING);

		popMessage("Thaw : " + Logger.PREFIXES[level], line, msgType);
	}

	public void logLevelChanged(int oldLevel, int newLevel) {
	}

	public String getNameForUser() {
		return I18n.getMessage("thaw.plugin.trayIcon.pluginName");
	}

	public ImageIcon getIcon() {
		return IconBox.blueBunny;
	}

	public void switchMainWindowVisibility() {
		Logger.info(this, "Changing main window visibility");

		boolean v = !core.getMainWindow().isVisible();

		core.getMainWindow().setVisible(v);

		if (v)
			core.getMainWindow().getMainFrame().toFront();
	}

	private class QueryComparator implements Comparator {

		public QueryComparator() {

		}

		public int compare(final Object o1, final Object o2) {
			int result = 0;

			if (!(o1 instanceof FCPTransferQuery)
					|| !(o2 instanceof FCPTransferQuery))
				return 0;

			final FCPTransferQuery q1 = (FCPTransferQuery) o1;
			final FCPTransferQuery q2 = (FCPTransferQuery) o2;

			if ((q1.getProgression() <= 0)
					&& (q2.getProgression() <= 0)) {
				if (q1.isRunning() && !q2.isRunning())
					return 1;

				if (q2.isRunning() && !q1.isRunning())
					return -1;
			}

			result = -1 * (new Integer(q1.getProgression())).compareTo(new Integer(q2.getProgression()));

			return result;
		}

		public boolean equals(final Object obj) {
			return true;
		}

		public int hashCode() {
			return super.hashCode();
		}
	}

	private Vector<TransferProgressBar> progressBars = null;

	private JPanel getTransferPanel(FCPTransferQuery q) {
		JPanel p = new JPanel(new GridLayout(2, 1, 5, 5));

		String txt = q.getFilename();

		if (txt == null)
			txt = q.getFileKey();

		if (txt == null)
			txt = "?";

		ImageIcon icon;

		if (q.getQueryType() == UPLOAD)
			icon = IconBox.minInsertions;
		else
			icon = IconBox.minDownloads;

		JLabel l = new JLabel(txt);
		l.setIcon(icon);

		p.add(l);

		TransferProgressBar bar = new TransferProgressBar(q, true, true);
		bar.refresh();
		progressBars.add(bar);
		p.add(bar);

		return p;
	}

	private boolean realDisplayFrame(int x, int y) {
		dialog = new JDialog((Frame) null,
				I18n.getMessage("thaw.plugin.trayIcon.dialogTitle"));
		dialog.getContentPane().setLayout(new BorderLayout(5, 5));
		dialog.setUndecorated(true);
		dialog.setResizable(true);

		JPanel panel = new JPanel(new BorderLayout(10, 10));
		panel.add(new JLabel(" "), BorderLayout.CENTER);

		List<FCPTransferQuery> queries = core.getQueueManager().getRunningQueue();

		JPanel north;

		List<FCPTransferQuery> newQueries = new Vector<FCPTransferQuery>();

		for (FCPTransferQuery query : queries) {
			newQueries.add(query);
		}

		if (newQueries.size() == 0) {
			popMessage("Thaw",
					I18n.getMessage("thaw.plugin.trayIcon.emptyQueue"),
					SysTrayIcon.MSG_WARNING);
			dialog = null;
			return false;
		}

		Collections.sort(newQueries, new QueryComparator());

		north = new JPanel(new GridLayout(queries.size(), 1, 10, 10));

		progressBars = new Vector();

		for (FCPTransferQuery query : newQueries) {
			north.add(getTransferPanel(query));
		}

		JPanel northNorth = new JPanel(new BorderLayout());
		northNorth.add(new JLabel(" "), BorderLayout.CENTER);

		closeDialog = new JButton(IconBox.minClose);
		closeDialog.addActionListener(this);
		northNorth.add(closeDialog, BorderLayout.EAST);

		dialog.getContentPane().add(northNorth, BorderLayout.NORTH);
		dialog.getContentPane().add(panel, BorderLayout.CENTER);

		panel.add(north,
				BorderLayout.NORTH);

		dialog.getContentPane().add(new JScrollPane(panel,
				JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED),
				BorderLayout.CENTER);

		//dialog.setSize(DIALOG_X, DIALOG_Y);
		dialog.validate();
		dialog.pack();

		Dimension size = dialog.getSize();

		double height = size.getHeight();
		double width = size.getWidth();

		if (width > DIALOG_X)
			width = DIALOG_X;
		if (height > DIALOG_Y)
			height = DIALOG_Y;

		dialog.setSize((int) width, (int) height);

		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		int screen_x = (int) d.getWidth();
		int screen_y = (int) d.getHeight();

		if (x + width >= screen_x)
			x -= width;
		if (y + height >= screen_y)
			y -= height;

		dialog.setLocation(x, y);

		dialog.setVisible(true);
		dialog.toFront();

		return true;
	}

	private class ProgressBarRefresher implements ThawRunnable {

		private Vector<TransferProgressBar> bars;

		private boolean stop;

		public ProgressBarRefresher(Vector<TransferProgressBar> bars) {
			this.bars = bars;
			stop = false;
		}

		public void run() {
			while (!stop) {

				for (TransferProgressBar bar : bars) {
					try {
						Thread.sleep(200);
					} catch (InterruptedException e) {
						/* \_o< */
					}

					if (stop)
						break;

					bar.refresh();
				}
			}
		}

		public void stop() {
			stop = true;
		}
	}

	private ProgressBarRefresher refresher = null;

	public void displayFrame(int x, int y) {
		if (realDisplayFrame(x, y) && dialog != null) {

			/* progressBars vector is generated at the same time than the panels */
			refresher = new ProgressBarRefresher(progressBars);
			Thread th = new Thread(new ThawThread(refresher, "Trayicon transfer list refresher", this));
			th.start();
		}
	}

	public void hideFrame() {
		if (dialog != null) {
			dialog.setVisible(false);
			dialog = null;
			progressBars = null;
			refresher.stop();
			refresher = null;
		}
	}

	public void windowActivated(WindowEvent e) {
	}

	public void windowClosed(WindowEvent e) {
	}

	public void windowClosing(WindowEvent e) {
	}

	public void windowDeactivated(WindowEvent e) {
	}

	public void windowDeiconified(WindowEvent e) {
	}

	public void windowIconified(WindowEvent e) {
	}

	public void windowOpened(WindowEvent e) {
	}

	public void mouseClicked(MouseEvent e) {
		if (dialog != null) {
			hideFrame();
			return;
		}

		if (e.getButton() == MouseEvent.BUTTON1)
			switchMainWindowVisibility();
		else if (e.getButton() == MouseEvent.BUTTON3) {
			if (dialog == null) {
				Point p = icon.getMousePosition();
				displayFrame(((int) p.getX()), ((int) p.getY()));
			}
		}
	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {
	}

	public void mouseReleased(MouseEvent e) {
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == closeDialog) {
			if (dialog != null) {
				hideFrame();
			}
		}
	}
}
