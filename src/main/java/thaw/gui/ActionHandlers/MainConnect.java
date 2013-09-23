package thaw.gui.ActionHandlers;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import thaw.core.Core;

public class MainConnect implements ActionListener {

	final protected Core core;

	public MainConnect(Core core) {
		this.core = core;
	}

	public void actionPerformed(final ActionEvent e) {
		core.reconnect(false);
	}
}
