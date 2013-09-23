package thaw.gui;

import java.awt.Component;
import java.util.Vector;
import javax.swing.Icon;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import thaw.core.Logger;

public class TabbedPane extends JTabbedPane implements ChangeListener {

	/**
	 *
	 */
	private static final long serialVersionUID = 8453293567552928389L;

	private Vector<String> tabNames;

	public TabbedPane() {
		super();
		tabNames = new Vector<String>();
		super.addChangeListener(this);
	}

	public void addTab(final String tabName, final Icon icon,
					   final java.awt.Component panel) {
		tabNames.add(tabName);

		if (tabNames.size() > 1)
			super.addTab("", icon, panel);
		else
			super.addTab(tabName, icon, panel);

		int x = super.indexOfComponent(panel);

		super.setToolTipTextAt(x, tabName);
	}

	public void remove(Component panel) {
		int x = super.indexOfComponent(panel);

		if (x >= 0)
			tabNames.remove(x);
		else {
			/* ConfigWindow is a little bit lazy :
			 * when you call ConfigWindow.addTabs(),
			 * it will first try to remove the tabs before readding them.
			 * This way, it's sure that there is not twice the tabs
			 */
			/* so this situation can be perfectly normal */
			Logger.debug(this, "remove(): Component not found ?");
		}

		super.remove(panel);
	}

	public int indexOfTab(String tabName) {
		return tabNames.indexOf(tabName);
	}

	public void stateChanged(final ChangeEvent e) {
		int x = super.getSelectedIndex();
		int tabCount = super.getTabCount();

		for (int i = 0; i < tabCount; i++) {
			if (i == x)
				super.setTitleAt(i, tabNames.get(i));
			else
				super.setTitleAt(i, "");
		}
	}
}
