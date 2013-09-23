package thaw.core;

import static javax.swing.SwingUtilities.updateComponentTreeUI;
import static thaw.gui.GUIHelper.center;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

public class SplashScreen {

	private static final int SIZE_X = 500;

	private static final int SIZE_Y = 150;

	private static final int NMB_ICONS = 13;

	private final JDialog splashScreen = new JDialog();

	private final JProgressBar progressBar = new JProgressBar(0, 100);

	private final JPanel iconPanel = new JPanel();

	private int nmbIcon = 0;

	private final List<JLabel> emptyLabels = new ArrayList<JLabel>();

	private final List<JLabel> iconLabels = new ArrayList<JLabel>();

	public void display() {
		final JPanel panel = new JPanel();
		JPanel subPanel = new JPanel();

		splashScreen.setUndecorated(true);
		splashScreen.setResizable(false);

		panel.setLayout(new BorderLayout(10, 10));
		subPanel.setLayout(new GridLayout(2, 1));
		iconPanel.setLayout(new GridLayout(1, NMB_ICONS));

		/* it's a dirty method to keep the NMB_ICONS parts of the panel at the same size */
		for (int i = 0; i < NMB_ICONS; i++) {
			JLabel lb = new JLabel();
			emptyLabels.add(lb);
			iconPanel.add(lb, i);
		}

		final JLabel thawLabel = new JLabel("Thaw");

		thawLabel.setFont(new Font("Dialog", Font.BOLD, 42));
		thawLabel.setHorizontalAlignment(JLabel.CENTER);

		subPanel.add(thawLabel);
		subPanel.add(iconPanel);

		panel.add(subPanel, BorderLayout.CENTER);

		progressBar.setStringPainted(true);
		progressBar.setString("Wake up Neo ...");

		panel.add(progressBar, BorderLayout.SOUTH);

		splashScreen.getContentPane().add(panel);

		splashScreen.setSize(SplashScreen.SIZE_X, SplashScreen.SIZE_Y);
		center(splashScreen);
		splashScreen.setVisible(true);
	}

	public JDialog getDialog() {
		return splashScreen;
	}

	/**
	 * @param progress
	 * 		In percent
	 */
	public void setProgression(final int progress) {
		progressBar.setValue(progress);
		splashScreen.getContentPane().validate();
	}

	public void addIcon(ImageIcon icon) {
		JLabel lb = new JLabel(icon);

		lb.setHorizontalAlignment(JLabel.CENTER);
		lb.setVerticalAlignment(JLabel.CENTER);

		if (emptyLabels.size() > 0)
			iconPanel.remove(emptyLabels.get(0));

		iconPanel.add(lb, nmbIcon);

		if (emptyLabels.size() > 0)
			emptyLabels.remove(0);

		nmbIcon++;

		splashScreen.getContentPane().validate();
		lb.repaint();
	}


	/* TODO : removeIcon() */

	public int getProgression() {
		return progressBar.getValue();
	}

	public void setStatus(final String status) {
		progressBar.setString(status);
		splashScreen.getContentPane().validate();
	}

	public void setProgressionAndStatus(final int progress, final String status) {
		setProgression(progress);
		setStatus(status);
	}

	public void hide() {
		splashScreen.setVisible(false);
		splashScreen.dispose();
	}

	public void rebuild() {
		updateComponentTreeUI(splashScreen);
	}
}
