package thaw.plugins;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import thaw.core.Config;
import thaw.core.Core;
import thaw.core.I18n;
import thaw.core.LogListener;
import thaw.core.Logger;
import thaw.core.Plugin;
import thaw.core.ThawRunnable;
import thaw.core.ThawThread;
import thaw.gui.FileChooser;
import thaw.gui.IconBox;

/** Quick and dirty console showing Thaw logs, and allowing to save them. */
public class LogConsole implements Plugin, LogListener, ActionListener, ThawRunnable {

	public final static int MAX_LINE = 512;

	private Core core;

	private Config config;

	private String[] buffer;

	private int readOffset;

	private int writeOffset;

	private JPanel consolePanel;

	private JTextArea logArea;

	private JScrollPane logAreaScrollPane;

	private JButton saveToFile;

	private JComboBox logLevel;

	private boolean threadRunning;

	private boolean hasChanged;

	public final static String[] LOG_LEVEL_NAMES = new String[] {
			"Errors only",
			"Errors and warnings",
			"Errors to notices",
			"Errors to infos",
			"Errors to debugs",
			"Errors to verboses"
	};

	public LogConsole() {

	}

	public boolean run(final Core core) {
		this.core = core;
		this.config = core.getConfig();

		threadRunning = true;
		hasChanged = false;

		buffer = new String[MAX_LINE + 1];
		readOffset = 0;
		writeOffset = 0;

		consolePanel = new JPanel();
		consolePanel.setLayout(new BorderLayout());

		logArea = new JTextArea();
		logArea.setEditable(false);

		JPanel southPanel = new JPanel(new BorderLayout(5, 5));

		logLevel = new JComboBox(LOG_LEVEL_NAMES);
		updateLogLevel();
		logLevel.addActionListener(this);
		southPanel.add(logLevel, BorderLayout.WEST);

		saveToFile = new JButton(I18n.getMessage("thaw.plugin.console.saveToFile"));
		saveToFile.addActionListener(this);
		southPanel.add(saveToFile, BorderLayout.CENTER);

		logAreaScrollPane = new JScrollPane(logArea);

		consolePanel.add(logAreaScrollPane, BorderLayout.CENTER);
		consolePanel.add(southPanel, BorderLayout.SOUTH);

		core.getMainWindow().addTab(I18n.getMessage("thaw.plugin.console.console"),
				IconBox.terminal, consolePanel);

		Logger.addLogListener(this);

		Thread dispThread = new Thread(new ThawThread(this, "Log console refresh", this));
		dispThread.start();

		return true;

	}

	public void stop() {
		threadRunning = false;

		Logger.removeLogListener(this);

		core.getMainWindow().removeTab(consolePanel);
	}

	public void updateLogLevel() {
		logLevel.setSelectedIndex(Logger.getLogLevel());
	}

	public void actionPerformed(final ActionEvent e) {
		if (e.getSource() == logLevel) {

			if (Logger.getLogLevel() != logLevel.getSelectedIndex()) {/* to avoid loops */
				Logger.setLogLevel(logLevel.getSelectedIndex());
				config.setValue("logLevel", Integer.toString(logLevel.getSelectedIndex()));
			}

		} else if (e.getSource() == saveToFile) {
			final FileChooser fileChooser = new FileChooser();

			fileChooser.setTitle(I18n.getMessage("thaw.plugin.console.console"));
			fileChooser.setDirectoryOnly(false);
			fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);

			final File file = fileChooser.askOneFile();

			if (file != null) {
				Logger.info(this, "Saving logs ...");
				writeLogsToFile(file);
				Logger.info(this, "Saving done.");
			}

		}

	}

	public void writeLogsToFile(final File file) {
		/* A la bourrin */

		FileOutputStream output;

		try {
			output = new FileOutputStream(file);
		} catch (final FileNotFoundException e) {
			Logger.error(this, "FileNotFoundException ? wtf ?");
			return;
		}

		try {
			output.write(logArea.getText().getBytes("UTF-8"));
		} catch (final IOException e) {
			Logger.error(this, "IOException while writing logs ... out of space ?");
			return;
		}

		try {
			output.close();
		} catch (final IOException e) {
			Logger.error(this, "IOException while closing log file ?!");
			return;
		}
	}

	public void newLogLine(int level, Object src, String line) {
		if (src != null)
			addLine(Logger.PREFIXES[level] + " " + src.getClass().getName() + ": " + line + "\n");
		else
			addLine(Logger.PREFIXES[level] + " " + line + "\n");
	}

	public void logLevelChanged(int oldLevel, int newLevel) {
		updateLogLevel();
	}

	public void addLine(String line) {
		buffer[writeOffset] = line;

		writeOffset++;

		if (writeOffset == MAX_LINE)
			writeOffset = 0;

		if (writeOffset == readOffset) {
			readOffset++;

			if (readOffset == MAX_LINE)
				readOffset = 0;
		}

		hasChanged = true;
	}

	public void refreshDisplay() {
		String res = "";
		int i;

		for (i = readOffset; ; i++) {
			if (i == MAX_LINE + 1)
				i = 0;

			if (buffer[i] != null)
				res += buffer[i];

			if ((readOffset > 0 && i == readOffset - 1)
					|| (readOffset <= 0 && i == MAX_LINE))
				break;
		}

		logArea.setText(res);

		SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                logAreaScrollPane.getVerticalScrollBar().setValue(logAreaScrollPane.getVerticalScrollBar().getMaximum());
            }

        });
	}

	public void run() {
		while (threadRunning) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				/* \_o< */
			}

			if (threadRunning && hasChanged) {
				hasChanged = false;
				refreshDisplay();
			}
		}
	}

	public String getNameForUser() {
		return I18n.getMessage("thaw.plugin.console.console");
	}

	public ImageIcon getIcon() {
		return IconBox.terminal;
	}
}
