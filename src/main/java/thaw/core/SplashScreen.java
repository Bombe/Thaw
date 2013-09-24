package thaw.core;

import static javax.swing.SwingConstants.CENTER;
import static javax.swing.SwingConstants.WEST;
import static javax.swing.SwingUtilities.updateComponentTreeUI;
import static thaw.gui.GUIHelper.center;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

public class SplashScreen {

	private static final int SIZE_X = 500;

	private static final int SIZE_Y = 150;

	private final JDialog splashScreen = new JDialog();

	private final JProgressBar progressBar = new JProgressBar(0, 100);

	private final JPanel iconPanel = new JPanel();

	public void display() {
		final JPanel panel = new JPanel();
		JPanel subPanel = new JPanel();

		splashScreen.setUndecorated(true);
		splashScreen.setResizable(false);

		panel.setLayout(new BorderLayout(10, 10));
		subPanel.setLayout(new GridLayout(2, 1));
		iconPanel.setLayout(new FlowLayout(WEST));

		final JLabel thawLabel = new JLabel("Thaw");

		thawLabel.setFont(new Font("Dialog", Font.BOLD, 42));
		thawLabel.setHorizontalAlignment(CENTER);

		subPanel.add(thawLabel);
		subPanel.add(iconPanel);

		panel.add(subPanel, BorderLayout.CENTER);

		progressBar.setStringPainted(true);
		progressBar.setString("Wake up Neo ...");

		panel.add(progressBar, BorderLayout.SOUTH);

		splashScreen.getContentPane().add(panel);

		splashScreen.setSize(SIZE_X, SIZE_Y);
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

		lb.setHorizontalAlignment(CENTER);
		lb.setVerticalAlignment(CENTER);

		iconPanel.add(lb);

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
