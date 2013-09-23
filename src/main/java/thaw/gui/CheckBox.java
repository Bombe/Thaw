package thaw.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JCheckBox;

import thaw.core.Config;

/**
 * Memorize the checkbox status (selected or not) in the configuration
 *
 * @author jflesch
 */
public class CheckBox extends JCheckBox implements ActionListener {

	private static final long serialVersionUID = -7815009734483702831L;

	public final static String PREFIX = "checkbox_";

	private Config config;

	private String name;

	public CheckBox(Config config, String name,
					String txt) {
		super(txt);

		this.config = config;
		this.name = name;

		loadState();

		super.addActionListener(this);
	}

	public CheckBox(Config config, String name,
					String txt, boolean selected) {
		super(txt, selected);

		this.config = config;
		this.name = name;

		loadState();

		super.addActionListener(this);
	}

	public void loadState() {
		if (config.getValue(PREFIX + name) != null)
			super.setSelected((new Boolean(config.getValue(PREFIX + name))).booleanValue());
	}

	public void saveState() {
		config.setValue(PREFIX + name,
				Boolean.toString(super.isSelected()));
	}

	public void actionPerformed(ActionEvent e) {
		saveState();
	}
}
