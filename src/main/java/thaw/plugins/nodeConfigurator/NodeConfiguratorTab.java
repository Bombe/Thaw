package thaw.plugins.nodeConfigurator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import thaw.core.I18n;
import thaw.fcp.FCPGetConfig;
import thaw.fcp.FCPModifyConfig;
import thaw.fcp.FCPQueryManager;

public class NodeConfiguratorTab implements Observer, ActionListener, ListSelectionListener {

	private FCPQueryManager queryManager;

	private boolean advanced;

	private JPanel panel;

	private JButton reload;

	private JList categoryChoice;

	private JList settingChoice;

	private JTextArea descriptionArea;

	private JTextField valueField;

	private JComboBox valueSelecter;

	private boolean selecter = false;

	private JTextField defaultField;

	private JPanel valuePanel;

	private JButton applyButton;

	public final static int COLUMNS_WIDTH = 125;

	public final static int GRAY = 240;

	private Hashtable categories = null;

	private Vector categoryNames = null;

	private final static String trueStr = I18n.getMessage("thaw.common.true");

	private final static String falseStr = I18n.getMessage("thaw.common.false");

	public NodeConfiguratorTab(boolean advanced, FCPQueryManager queryManager) {
		this.advanced = advanced;
		this.queryManager = queryManager;

		JScrollPane sc;

		panel = new JPanel(new BorderLayout(5, 5));

		/* reload */
		JPanel reloadPanel = new JPanel(new BorderLayout());
		reload = new JButton(I18n.getMessage("thaw.plugin.nodeConfig.reload"));
		reload.addActionListener(this);
		reloadPanel.add(reload, BorderLayout.WEST);
		reloadPanel.add(new JLabel(""), BorderLayout.EAST);

		panel.add(reloadPanel, BorderLayout.NORTH);

		/* categories */
		JPanel categoryPanel = new JPanel(new BorderLayout(0, 0));
		categoryPanel.add(new JLabel(I18n.getMessage("thaw.plugin.nodeConfig.categories")), BorderLayout.NORTH);
		categoryChoice = new JList();
		categoryChoice.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		categoryChoice.addListSelectionListener(this);
		categoryPanel.add(sc = new JScrollPane(categoryChoice), BorderLayout.CENTER);
		sc.setPreferredSize(new Dimension(COLUMNS_WIDTH, COLUMNS_WIDTH));

		panel.add(categoryPanel, BorderLayout.WEST);

		JPanel subPanel = new JPanel(new BorderLayout(5, 5));

		/* settings */
		JPanel settingsPanel = new JPanel(new BorderLayout(0, 0));
		settingsPanel.add(new JLabel(I18n.getMessage("thaw.plugin.nodeConfig.settings")), BorderLayout.NORTH);
		settingChoice = new JList();
		settingChoice.addListSelectionListener(this);
		settingsPanel.add(sc = new JScrollPane(settingChoice), BorderLayout.CENTER);
		sc.setPreferredSize(new Dimension((int) (COLUMNS_WIDTH * 1.5), (int) (COLUMNS_WIDTH * 1.5)));

		subPanel.add(settingsPanel, BorderLayout.WEST);

		JPanel descAndValuePanel = new JPanel(new GridLayout(2, 1));

		/* description */
		JPanel descPanel = new JPanel(new BorderLayout(0, 0));
		descPanel.add(new JLabel(I18n.getMessage("thaw.plugin.nodeConfig.description")), BorderLayout.NORTH);
		descriptionArea = new JTextArea();
		descriptionArea.setEditable(false);
		descriptionArea.setBackground(new Color(GRAY, GRAY, GRAY));
		descriptionArea.setLineWrap(true);
		descriptionArea.setWrapStyleWord(true);
		descPanel.add(new JScrollPane(descriptionArea), BorderLayout.CENTER);
		descAndValuePanel.add(descPanel);

		JPanel valueAndButtonsPanel = new JPanel(new BorderLayout(0, 0));

		/* value */
		valuePanel = new JPanel(new GridLayout(4, 0));
		valuePanel.add(new JLabel(I18n.getMessage("thaw.plugin.nodeConfig.default")));
		defaultField = new JTextField();
		defaultField.setEditable(false);
		defaultField.setBackground(new Color(GRAY, GRAY, GRAY));
		valuePanel.add(defaultField);
		valuePanel.add(new JLabel(I18n.getMessage("thaw.plugin.nodeConfig.value")));
		valueField = new JTextField();
		valuePanel.add(valueField);

		selecter = false;

		valueSelecter = new JComboBox(new String[] {
				trueStr,
				falseStr
		});

		valueAndButtonsPanel.add(valuePanel, BorderLayout.NORTH);

		/* button(s) */
		applyButton = new JButton(I18n.getMessage("thaw.common.apply"));
		applyButton.addActionListener(this);

		valueAndButtonsPanel.add(new JLabel(""), BorderLayout.CENTER);
		valueAndButtonsPanel.add(applyButton, BorderLayout.SOUTH);

		descAndValuePanel.add(valueAndButtonsPanel);

		subPanel.add(descAndValuePanel, BorderLayout.CENTER);

		panel.add(subPanel, BorderLayout.CENTER);

	}

	public JPanel getPanel() {
		return panel;
	}

	public void refresh() {
		FCPGetConfig getConfig = new FCPGetConfig(true /* current */, false /* with short desc */,
				true /* with long desc */, true /* with defaults */,
				true /* with sort order */, true /* with expert flag */,
				false /* with force write flag */, queryManager);
		getConfig.addObserver(this);
		getConfig.start();
	}

	private void refreshDisplay() {
		categoryChoice.removeListSelectionListener(this);
		categoryChoice.setListData(categoryNames);
		categoryChoice.addListSelectionListener(this);

		settingChoice.removeListSelectionListener(this);
		settingChoice.setListData(new Vector());
		settingChoice.addListSelectionListener(this);

		displayField();

		descriptionArea.setText("");
		valueField.setText("");
		defaultField.setText("");
	}

	private void displaySelecter() {
		if (!selecter) {
			valuePanel.remove(valueField);
			valuePanel.add(valueSelecter);
			selecter = true;
			panel.revalidate();
		}
	}

	private void displayField() {
		if (selecter) {
			valuePanel.remove(valueSelecter);
			valuePanel.add(valueField);
			selecter = false;
			panel.revalidate();
		}
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == applyButton) {

			FCPGetConfig.ConfigSetting setting = (FCPGetConfig.ConfigSetting) settingChoice.getSelectedValue();

			String name = setting.getName();

			String value;

			if (!selecter)
				value = valueField.getText();
			else {
				String selected = (String) valueSelecter.getSelectedItem();
				if (trueStr == selected)
					value = "true";
				else
					value = "false";
			}

			FCPModifyConfig modifConf = new FCPModifyConfig(name, value, queryManager);
			modifConf.start();

			refresh();

		} else if (e.getSource() == reload) {
			refresh();
		}
	}

	public void valueChanged(ListSelectionEvent e) {
		if (e.getSource() == categoryChoice) {

			synchronized (categories) {

				String catName = (String) categoryChoice.getSelectedValue();

				settingChoice.removeListSelectionListener(this);
				settingChoice.setListData((Vector) categories.get(catName));
				settingChoice.addListSelectionListener(this);

				displayField();

				descriptionArea.setText("");
				valueField.setText("");
				defaultField.setText("");

			}

		} else if (e.getSource() == settingChoice) {

			FCPGetConfig.ConfigSetting setting = (FCPGetConfig.ConfigSetting) settingChoice.getSelectedValue();
			descriptionArea.setText(setting.getLongDesc());

			if ("false".equals(setting.getDefault())
					|| "true".equals(setting.getDefault())) {

				displaySelecter();

				if ("true".equals(setting.getCurrent()))
					valueSelecter.setSelectedItem(trueStr);
				else
					valueSelecter.setSelectedItem(falseStr);

			} else {

				displayField();
				valueField.setText(setting.getCurrent());
			}

			defaultField.setText(setting.getDefault());
		}
	}

	public void update(Observable o, Object param) {
		if (param == null || !(param instanceof Hashtable))
			return;

		Hashtable configSettings = (Hashtable) param;

		/* will restructure the data */
		categories = new Hashtable();
		categoryNames = new Vector();

		synchronized (categories) {

			/* first : sort them by category */

			for (Enumeration keyEnum = configSettings.keys();
				 keyEnum.hasMoreElements(); ) {

				String name = (String) keyEnum.nextElement();
				FCPGetConfig.ConfigSetting setting = (FCPGetConfig.ConfigSetting) configSettings.get(name);

				if (setting.getExpertFlag() && !advanced)
					continue;

				int dotPos = name.indexOf('.');
				String categoryName = name.substring(0, dotPos);

				Vector categorySettings = (Vector) categories.get(categoryName);
				if (categorySettings == null) {
					categoryNames.add(categoryName);
					categorySettings = new Vector();
					categories.put(categoryName, categorySettings);
				}

				categorySettings.add(setting);
			}

			/* second : sort them by sortOrder */

			for (Enumeration keyEnum = categories.keys();
				 keyEnum.hasMoreElements(); ) {
				String catName = (String) keyEnum.nextElement();
				Vector categorySettings = (Vector) categories.get(catName);

				Collections.sort(categorySettings);
			}

			refreshDisplay();
		}
	}
}
