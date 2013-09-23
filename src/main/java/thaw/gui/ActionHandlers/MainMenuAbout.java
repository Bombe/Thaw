package thaw.gui.ActionHandlers;

import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import thaw.core.I18n;
import thaw.core.Main;

public class MainMenuAbout implements ActionListener {

	public void actionPerformed(final ActionEvent e) {
		showDialogAbout();
	}

	public void showDialogAbout() {
		final JComponent[] labels = new JComponent[] {
				new JTextField("Thaw " + Main.getVersion()),
				new JLabel(I18n.getMessage("thaw.about.l02")),
				new JLabel(I18n.getMessage("thaw.about.l03")),
				new JLabel(I18n.getMessage("thaw.about.l04")),
				new JLabel(""),
				new JLabel(I18n.getMessage("thaw.about.l06")),
				new JLabel(""),
				new JLabel(I18n.getMessage("thaw.about.l07")),
				new JLabel(I18n.getMessage("thaw.about.l08")),
				new JLabel(I18n.getMessage("thaw.about.l09")),
				new JLabel(I18n.getMessage("thaw.about.l10")),
				new JLabel(I18n.getMessage("thaw.about.l11")),
				new JLabel(I18n.getMessage("thaw.about.l12")),
				new JLabel(I18n.getMessage("thaw.about.l13")),
				new JLabel(I18n.getMessage("thaw.about.l14")),
				new JLabel(I18n.getMessage("thaw.about.l15"))
		};

		for (JComponent label : labels) {
			if (label instanceof JTextField) {
				((JTextField) label).setEditable(false);
			}
		}

		((JTextField) labels[0]).setFont(new Font("Dialog", Font.BOLD, 30));

		JOptionPane.showMessageDialog(null, labels, I18n.getMessage("thaw.about.title"),
				JOptionPane.INFORMATION_MESSAGE);
	}
}
