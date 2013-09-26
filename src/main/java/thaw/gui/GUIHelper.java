package thaw.gui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.io.IOException;
import javax.swing.AbstractButton;
import javax.swing.text.JTextComponent;

import thaw.core.I18n;
import thaw.core.Logger;

public class GUIHelper {

	private final static String unknownStr = I18n.getMessage("thaw.common.unknown");

	private GUIHelper() {
		/* this class doesn’t need to be instantiated. */
	}

	/**
	 * when actionPerformed() is called, it will fill in the specified text
	 * component with what is in the clipboard
	 */
	public static class PasteHelper implements ActionListener {

		JTextComponent txtComp;

		public PasteHelper(final AbstractButton src, final JTextComponent txtComp) {
			if (src != null)
				src.addActionListener(this);
			this.txtComp = txtComp;
		}

		public void actionPerformed(final ActionEvent evt) {
			GUIHelper.pasteToComponent(txtComp);
		}
	}

	public static void pasteToComponent(final JTextComponent txtComp) {
		final Toolkit tk = Toolkit.getDefaultToolkit();
		final Clipboard cp = tk.getSystemClipboard();

		String result;
		final Transferable contents = cp.getContents(null);

		final boolean hasTransferableText = ((contents != null) &&
				contents.isDataFlavorSupported(DataFlavor.stringFlavor));

		try {
			if (hasTransferableText) {
				result = (String) contents.getTransferData(DataFlavor.stringFlavor);
				txtComp.setText(txtComp.getText() + result);
			} else {
				Logger.notice(new GUIHelper(), "Nothing to get from clipboard");
			}
		} catch (final UnsupportedFlavorException e) {
			Logger.error(new GUIHelper(), "Error while pasting: UnsupportedFlavorException: " + e.toString());
		} catch (final IOException e) {
			Logger.error(new GUIHelper(), "Error while pasting: IOException: " + e.toString());
		}
	}

	public static void copyToClipboard(final String str) {
		final Toolkit tk = Toolkit.getDefaultToolkit();
		final StringSelection st = new StringSelection(str);
		final Clipboard cp = tk.getSystemClipboard();
		cp.setContents(st, null);
	}

	public static String getPrintableTime(final long seconds) {
		if (seconds == 0)
			return unknownStr;

		if (seconds < 60)
			return (new Long(seconds)).toString() + " s";

		if (seconds < 3600) {
			final long min = seconds / 60;
			return ((new Long(min)).toString() + " m");
		}

		if (seconds < 86400) {
			final long hour = seconds / 3600;
			return ((new Long(hour)).toString() + " h");
		}

		final long day = seconds / 86400;
		return ((new Long(day)).toString()) + " day(s)";

	}

	public static String getPrintableSize(final long size) {
		if (size == 0)
			return unknownStr;

		if (size < 1024) /* < 1KB */
			return ((new Long(size)).toString() + " B");

		if (size < 1048576) { /* < 1MB */
			final long kb = size / 1024;
			return ((new Long(kb)).toString() + " KB");
		}

		if (size < 1073741824) { /* < 1GB */
			final long mb = size / 1048576;
			return ((new Long(mb)).toString() + " MB");
		}

		final long gb = size / 1073741824;

		return ((new Long(gb)).toString() + " GB");
	}

	public final static int ARROW_SIZE = 15;

	public final static double ARROW_ANGLE = Math.PI / 6; /* 30° */

	private static void paintLine(Graphics g, int headX, int headY, int lng, double angle) {
		int endX = (int) (headX + (Math.cos(angle) * lng));
		int endY = (int) (headY + (Math.sin(angle) * lng));

		g.drawLine(headX, headY, endX, endY);
	}

	/* paint an arrow */
	public static void paintArrow(Graphics g, int headX, int headY, int endX, int endY) {
		g.drawLine(headX, headY, endX, endY);

		double dist = new Point2D.Double(headX, headY).distance(endX, endY);

		double theta = Math.acos((endX - headX) / dist);

		if ((endY - headY) < 0)
			theta = -1 * theta;

		paintLine(g, headX, headY, ARROW_SIZE, theta + ARROW_ANGLE);
		paintLine(g, headX, headY, ARROW_SIZE, theta - ARROW_ANGLE);
	}

	/**
	 * Centers the given window on the primary screen.
	 *
	 * @param window
	 * 		The window to center
	 * @see Toolkit#getScreenSize()
	 */
	public static void center(Window window) {
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension windowSize = window.getSize();
		window.setLocation((screenSize.width - windowSize.width) / 2, (screenSize.height - windowSize.height) / 2);
	}

}
