package thaw.core;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;
import java.util.Observable;
import java.util.Observer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import thaw.gui.ConfigWindow;
import thaw.gui.FileChooser;

/**
 * Creates and manages the panel containing all the things to configure related
 * to Thaw and only Thaw.
 */
public class ThawConfigPanel implements Observer, ActionListener {

	private Core core;

	private JPanel thawConfigPanel = null;

	private JCheckBox advancedModeBox = null;

	private boolean advancedMode;

	private JLabel tmpDirLabel;

	private JTextField tmpDirField;

	private JButton tmpDirButton;

	private JLabel langLabel;

	private JComboBox langBox;

	public ThawConfigPanel(final ConfigWindow configWindow, final Core core) {
		this.core = core;

		if (core.getConfig().getValue("advancedMode") == null)
			core.getConfig().setValue("advancedMode", "false");

		advancedMode = Boolean.valueOf(core.getConfig().getValue("advancedMode")).booleanValue();


		/* advanced mode */

		thawConfigPanel = new JPanel();
		thawConfigPanel.setLayout(new GridLayout(15, 1));

		advancedModeBox = new JCheckBox(I18n.getMessage("thaw.config.advancedMode"), advancedMode);

		thawConfigPanel.add(advancedModeBox);
		thawConfigPanel.add(new JLabel(" "));


		/* tmpdir */

		tmpDirField = new JTextField(System.getProperty("java.io.tmpdir"));
		tmpDirButton = new JButton(I18n.getMessage("thaw.common.browse"));
		tmpDirButton.addActionListener(this);

		tmpDirLabel = new JLabel(I18n.getMessage("thaw.common.tempDir"));
		thawConfigPanel.add(tmpDirLabel);

		JPanel tempDirPanel = new JPanel(new BorderLayout());

		tempDirPanel.add(tmpDirField,
				BorderLayout.CENTER);
		tempDirPanel.add(tmpDirButton,
				BorderLayout.EAST);
		thawConfigPanel.add(tempDirPanel);

		/* lang */
		langLabel = new JLabel(I18n.getMessage("thaw.common.language"));
		langBox = new JComboBox(I18n.supportedLocales.toArray());

		setLang();

		thawConfigPanel.add(new JLabel(""));
		thawConfigPanel.add(langLabel);
		thawConfigPanel.add(langBox);


		/* misc */

		setAdvancedOptionsVisibility(advancedMode);

		configWindow.addObserver(this);
	}

	public JPanel getPanel() {
		return thawConfigPanel;
	}

	private void setLang() {
		for (Locale supportedLocale : I18n.supportedLocales) {
			if (supportedLocale.getLanguage().equals(I18n.getLocale().getLanguage())) {
				langBox.setSelectedItem(supportedLocale);
			}
		}
	}

	private void setAdvancedOptionsVisibility(boolean v) {
		tmpDirField.setVisible(v);
		tmpDirButton.setVisible(v);
		tmpDirLabel.setVisible(v);
		langLabel.setVisible(v);
		langBox.setVisible(v);
	}

	public void actionPerformed(ActionEvent e) {
		FileChooser chooser = new FileChooser(System.getProperty("java.io.tmpdir"));
		chooser.setTitle(I18n.getMessage("thaw.common.tempDir"));
		chooser.setDirectoryOnly(true);
		chooser.setDialogType(javax.swing.JFileChooser.OPEN_DIALOG);

		java.io.File file = chooser.askOneFile();
		tmpDirField.setText(file.getPath());
	}

	public void update(final Observable o, final Object arg) {
		if (arg == core.getConfigWindow().getOkButton()) {
			advancedMode = advancedModeBox.isSelected();
			core.getConfig().setValue("advancedMode", Boolean.toString(advancedMode));

			core.getConfig().setValue("tmpDir", tmpDirField.getText());
			System.setProperty("java.io.tmpdir", tmpDirField.getText());
			tmpDirField.setText(System.getProperty("java.io.tmpdir"));

			core.getConfig().setValue("lang",
					((Locale) langBox.getSelectedItem()).getLanguage());

			setAdvancedOptionsVisibility(advancedMode);
		}

		if (arg == core.getConfigWindow().getCancelButton()) {
			advancedModeBox.setSelected(advancedMode);

			tmpDirField.setText(System.getProperty("java.io.tmpdir"));
			setLang();
		}
	}

}

