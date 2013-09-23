package thaw.plugins;

import java.awt.Color;
import java.util.Vector;

import thaw.core.Core;
import thaw.core.I18n;
import thaw.core.LogListener;
import thaw.core.Logger;
import thaw.core.Main;
import thaw.core.Plugin;
import thaw.core.ThawRunnable;
import thaw.core.ThawThread;
import thaw.fcp.FCPTransferQuery;
import thaw.gui.IconBox;

public class StatusBar implements ThawRunnable, Plugin, LogListener {

	public final static int INTERVAL = 3000; /* in ms */

	public final static String SEPARATOR = "     ";

	private Core core;

	private boolean running = true;

	private Thread refresher;

	private boolean advancedMode = false;

	private boolean dropNextRefresh = false;

	public final static Color ORANGE = new Color(240, 160, 0);

	private final static String connectingStr = I18n.getMessage("thaw.statusBar.connecting");

	private final static String disconnectedStr = I18n.getMessage("thaw.statusBar.disconnected");

	private final static String globalProgressionStr = I18n.getMessage("thaw.plugin.statistics.globalProgression");

	private final static String finishedStr = I18n.getMessage("thaw.plugin.statistics.finished");

	private final static String failedStr = I18n.getMessage("thaw.plugin.statistics.failed");

	private final static String runningStr = I18n.getMessage("thaw.plugin.statistics.running");

	private final static String pendingStr = I18n.getMessage("thaw.plugin.statistics.pending");

	public boolean run(final Core core) {
		this.core = core;

		advancedMode = Boolean.valueOf(core.getConfig().getValue("advancedMode"));

		running = true;
		refresher = new Thread(new ThawThread(this, "Status bar refresh", this));

		refresher.start();

		Logger.addLogListener(this);

		return true;
	}

	public void run() {
		while (running) {

			try {
				Thread.sleep(StatusBar.INTERVAL);
			} catch (final java.lang.InterruptedException e) {
				// pfff :P
			}

			if (!dropNextRefresh)
				updateStatusBar();
			else
				dropNextRefresh = false;

		}

	}

	public void newLogLine(int level, Object src, String line) {
		if (level <= 1) { /* error / warnings */
			dropNextRefresh = true;

			String str = Logger.PREFIXES[level] + " "
					+ ((src != null) ? (src.getClass().getName() + ": ") : "")
					+ line;

			core.getMainWindow().setStatus(IconBox.minStop, str,
					((level == 0) ? Color.RED : ORANGE));
		}
	}

	public void logLevelChanged(int oldLevel, int newLevel) {
	}

	public void updateStatusBar() {

		if (core.isReconnecting()) {
			core.getMainWindow().setStatus(IconBox.blueBunny,
					connectingStr, java.awt.Color.RED);
			return;
		}

		if (!core.getConnectionManager().isConnected()) {
			core.getMainWindow().setStatus(IconBox.minDisconnectAction,
					disconnectedStr, java.awt.Color.RED);
			return;
		}

		int progressDone = 0;
		int progressTotal = 0;

		int finished = 0;
		int failed = 0;
		int running = 0;
		int pending = 0;
		int total = 0;

		final Vector<FCPTransferQuery> runningQueue = core.getQueueManager().getRunningQueue();

		for (FCPTransferQuery query : runningQueue) {
			if (query.isRunning() && !query.isFinished()) {
				running++;
				progressTotal += 100;
				progressDone += query.getProgression();
			}

			if (query.isFinished() && query.isSuccessful()) {
				finished++;
				progressTotal += 100;
				progressDone += 100;
			}

			if (query.isFinished() && !query.isSuccessful()) {
				failed++;
			}
		}

		final Vector<Vector<FCPTransferQuery>> pendingQueues = core.getQueueManager().getPendingQueues();
		for (Vector<FCPTransferQuery> pendingQueue : pendingQueues) {
			progressTotal += pendingQueue.size() * 100;
			pending += pendingQueue.size();
		}

		total = finished + failed + running + pending;

		String status = "Thaw " + Main.VERSION;

		if (advancedMode) {
			status = status
					+ StatusBar.SEPARATOR + globalProgressionStr + " "
					+ Integer.toString(progressDone) + "/" + Integer.toString(progressTotal);
		}

		status = status
				+ StatusBar.SEPARATOR + finishedStr + " "
				+ Integer.toString(finished) + "/" + Integer.toString(total)
				+ StatusBar.SEPARATOR + failedStr + " "
				+ Integer.toString(failed) + "/" + Integer.toString(total)
				+ StatusBar.SEPARATOR + runningStr + " "
				+ Integer.toString(running) + "/" + Integer.toString(total)
				+ StatusBar.SEPARATOR + pendingStr + " "
				+ Integer.toString(pending) + "/" + Integer.toString(total);

		core.getMainWindow().setStatus(IconBox.minConnectAction, status);
	}

	public void stop() {
		running = false;
		Logger.removeLogListener(this);
		core.getMainWindow().setStatus(IconBox.blueBunny, "Thaw " + Main.VERSION);
	}

	public String getNameForUser() {
		return I18n.getMessage("thaw.plugin.statistics.statistics");
	}

	public javax.swing.ImageIcon getIcon() {
		return IconBox.remove;
	}
}
