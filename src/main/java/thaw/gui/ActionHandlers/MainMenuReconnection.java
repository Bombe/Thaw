package thaw.gui.ActionHandlers;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import thaw.core.Core;

public class MainMenuReconnection implements ActionListener {

	final protected Core core;

	public MainMenuReconnection(Core core) {
		this.core = core;
	}

	public void actionPerformed(final ActionEvent e) {
		if (!core.canDisconnect()) {
			if (!core.askDeconnectionConfirmation())
				return;
		}

		core.reconnect(false);
	}
}
