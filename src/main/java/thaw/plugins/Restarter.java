package thaw.plugins;

import static thaw.fcp.FCPQuery.Type.DOWNLOAD;

import java.awt.GridLayout;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.Vector;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import thaw.core.Core;
import thaw.core.I18n;
import thaw.core.Logger;
import thaw.core.Plugin;
import thaw.core.ThawRunnable;
import thaw.core.ThawThread;
import thaw.fcp.FCPTransferQuery;
import thaw.gui.IconBox;

/**
 * This plugin, after a given time, restart all/some the failed downloads (if
 * maxDownloads >= 0, downloads to restart are choosen randomly). A not too bad
 * example to show how to make plugins.
 *
 * @deprecated When this plugin was created, MaxRetries was stupidly set to 0
 *             instead of -1 => now this plugin is useless.
 */
public class Restarter implements Observer, ThawRunnable, Plugin {

	private int interval = 180; /* in s */

	private boolean restartFatals = false;

	private int timeElapsed = 0;

	private Core core;

	private boolean running = true;

	private Thread restarter = null;

	private JPanel configPanel;

	private JLabel restartIntervalLabel;

	private JTextField restartIntervalField;

	private JCheckBox restartFatalsBox;

	public boolean run(final Core core) {
		this.core = core;

		core.getConfig().addListener("restartInterval", this);
		core.getConfig().addListener("restartFatals", this);

		/* Reloading value from the configuration */
		try {
			if ((core.getConfig().getValue("restartInterval") != null)
					&& (core.getConfig().getValue("restartFatals") != null)) {
				interval = Integer.parseInt(core.getConfig().getValue("restartInterval"));
				restartFatals = Boolean.valueOf(core.getConfig().getValue("restartFatals")).booleanValue();
			}
		} catch (final Exception e) { /* probably conversion errors */ /* Yes I know, it's dirty */
			Logger.notice(this, "Unable to read / understand value from the config. Using default values");
		}


		/* Adding restart config tab to the config window */
		configPanel = new JPanel();
		configPanel.setLayout(new GridLayout(15, 1));

		restartIntervalLabel = new JLabel(I18n.getMessage("thaw.plugin.restarter.interval"));
		restartIntervalField = new JTextField(Integer.toString(interval));

		restartFatalsBox = new JCheckBox(I18n.getMessage("thaw.plugin.restarter.restartFatals"), restartFatals);

		configPanel.add(restartIntervalLabel);
		configPanel.add(restartIntervalField);
		configPanel.add(restartFatalsBox);

		core.getConfigWindow().addTab(I18n.getMessage("thaw.plugin.restarter.configTabName"),
				IconBox.minRefreshAction,
				configPanel);
		core.getConfigWindow().addObserver(this);

		running = true;
		restarter = new Thread(new ThawThread(this, "Restarter", this));
		restarter.start();

		return true;
	}

	public void stop() {
		core.getConfigWindow().removeTab(configPanel);
		core.getConfigWindow().deleteObserver(this);
		running = false;
	}

	public void run() {
		while (running) {
			try {

				for (timeElapsed = 0; (timeElapsed < interval) && running; timeElapsed++) {
					Thread.sleep(1000);
				}

			} catch (final InterruptedException e) {
				// We really really really don't care.
			}

			Logger.info(this, "Restarting some failed downloads (if there are some)");

			if (!running)
				break;

			final int maxDownloads = core.getQueueManager().getMaxDownloads();
			int alreadyRunning = 0;
			int failed = 0;
			final Vector<FCPTransferQuery> runningQueue = core.getQueueManager().getRunningQueue();

			try {
				if (maxDownloads >= 0) {
					/* We count how many are really running
					   and we write down those which are failed */
					for (final FCPTransferQuery query : runningQueue) {
						if (query.getQueryType() != DOWNLOAD)
							continue;

						if (query.isRunning() && !query.isFinished()) {
							alreadyRunning++;
						}

						if (query.isFinished() && !query.isSuccessful()
								&& (restartFatals || !query.isFatallyFailed())) {
							failed++;
						}
					}

					/* We choose randomly the ones to restart */
					while ((alreadyRunning < maxDownloads) && (failed > 0)) {
						final int toRestart = (new Random()).nextInt(failed);

						final Iterator it = runningQueue.iterator();
						int i = 0;

						while (it.hasNext()) {
							final FCPTransferQuery query = (FCPTransferQuery) it.next();

							if (query.getQueryType() != DOWNLOAD)
								continue;

							if (query.isFinished() && !query.isSuccessful()
									&& (restartFatals || !query.isFatallyFailed())) {
								if (i == toRestart) {
									restartQuery(query);
									break;
								}

								i++;
							}
						}

						alreadyRunning++;
						failed--;
					}

				} else { /* => if maxDownloads < 0 */

					/* We restart them all */
					for (final FCPTransferQuery query : runningQueue) {
						if ((query.getQueryType() == DOWNLOAD) && query.isFinished()
								&& !query.isSuccessful()
								&& (restartFatals || !query.isFatallyFailed()))
							restartQuery(query);
					}
				}
			} catch (final Exception e) {
				Logger.error(this, "Exception : " + e);
			}

		}
	}

	public void restartQuery(final FCPTransferQuery query) {
		query.stop();

		if (query.getMaxAttempt() >= 0)
			query.setAttempt(0);

		query.start();
	}

	public void update(final Observable o, final Object arg) {
		if (o == core.getConfigWindow()) {

			if (arg == core.getConfigWindow().getOkButton()) {
				core.getConfig().setValue("restartInterval", restartIntervalField.getText());
				core.getConfig().setValue("restartFatals",
						Boolean.toString(restartFatalsBox.isSelected()));

				/* Plugin will be stop() and start(), so no need to reload config */

				return;
			}

			if (arg == core.getConfigWindow().getCancelButton()) {
				restartIntervalField.setText(Integer.toString(interval));
				restartFatalsBox.setSelected(restartFatals);

				return;
			}
		}
	}

	public String getNameForUser() {
		return I18n.getMessage("thaw.plugin.restarter.restarter");
	}

	public ImageIcon getIcon() {
		return IconBox.refreshAction;
	}
}
