package thaw.core;

import javax.swing.ImageIcon;

/** Define what methods a plugin must implements. */
public interface Plugin {

	/**
	 * Called when the plugin is runned.
	 *
	 * @param core
	 * 		A ref to the core of the program.
	 * @return false means that plugin has troubles and should not be considered as
	 *         running.
	 */
	public boolean run(Core core);

	/** Called when the plugin is stopped (often at the end of the program). */
	public void stop();

	/**
	 * Gives plugin name. The same name as used for the tabs is recommanded. Result
	 * of this function is used only to inform user.
	 */
	public String getNameForUser();

	/** Can return null */
	public ImageIcon getIcon();
}
