package thaw.plugins.index;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.Vector;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import thaw.core.Config;
import thaw.core.I18n;
import thaw.core.Logger;
import thaw.fcp.FCPQueueManager;
import thaw.gui.IconBox;

public class CommentTab implements ActionListener {

	private boolean visible;

	private Index index;

	private JPanel tabPanel;

	private JPanel centerPanel;

	private JButton closeTabButton;

	private Vector buttonActions;

	private Config config;

	private IndexBrowserPanel indexBrowser;

	private JLabel titleLabel = null;

	private JScrollPane scrollPane;

	public CommentTab(Config config,
					  FCPQueueManager queueManager,
					  IndexBrowserPanel indexBrowser) {
		this.indexBrowser = indexBrowser;
		this.config = config;

		visible = false;

		tabPanel = new JPanel(new BorderLayout(10, 10));

		JPanel northPanel = new JPanel(new BorderLayout(10, 10));

		closeTabButton = new JButton(IconBox.minClose);
		closeTabButton.setToolTipText(I18n.getMessage("thaw.common.closeTab"));
		closeTabButton.addActionListener(this);

		JPanel southPanel = new JPanel(new BorderLayout());

		JButton button;

		buttonActions = new Vector();

		/*
		button = new JButton(I18n.getMessage("thaw.plugin.index.comment.add"), IconBox.minAddComment);
		buttonActions.add(new IndexManagementHelper.IndexCommentAdder(queueManager, indexBrowser, button));
		northPanel.add(button, BorderLayout.WEST);
		*/

		titleLabel = new JLabel(I18n.getMessage("thaw.plugin.index.comment.commentList") + " :");

		northPanel.add(titleLabel, BorderLayout.CENTER);
		northPanel.add(closeTabButton, BorderLayout.EAST);

		button = new JButton(I18n.getMessage("thaw.plugin.index.comment.add"), IconBox.minAddComment);
		buttonActions.add(new IndexManagementHelper.IndexCommentAdder(queueManager, indexBrowser, button));

		southPanel.add(button, BorderLayout.CENTER);

		centerPanel = new JPanel();

		scrollPane = new JScrollPane(centerPanel);

		tabPanel.add(northPanel, BorderLayout.NORTH);
		tabPanel.add(scrollPane, BorderLayout.CENTER);
		tabPanel.add(southPanel, BorderLayout.SOUTH);
	}

	public void updateCommentList() {
		centerPanel.removeAll();
		centerPanel.setLayout(new BorderLayout());

		JPanel insidePanel = new JPanel();

		Vector comments = null;

		if (index != null)
			comments = index.getComments();

		if (comments != null) {
			int nmbCommentToDisplay = 0;

			for (Iterator it = comments.iterator();
				 it.hasNext(); ) {
				if (!(((Comment) it.next()).mustBeIgnored(config)))
					nmbCommentToDisplay++;
			}

			insidePanel.setLayout(new GridLayout(nmbCommentToDisplay, 1, 20, 20));

			for (Iterator it = comments.iterator();
				 it.hasNext(); ) {
				Comment c = ((Comment) it.next());

				if (!c.mustBeIgnored(config)) {
					JPanel panel = c.getPanel(this);

					if (panel != null)
						insidePanel.add(panel);
				}
			}
		}

		centerPanel.add(insidePanel, BorderLayout.NORTH);
		centerPanel.add(new JLabel(""), BorderLayout.CENTER);

		centerPanel.revalidate();

		Runnable doScroll = new Runnable() {
			public void run() {
				int max = scrollPane.getVerticalScrollBar().getMaximum();
				scrollPane.getVerticalScrollBar().setValue(max);
			}
		};

		javax.swing.SwingUtilities.invokeLater(doScroll);
	}

	/** will reset the page to 0 */
	public void setIndex(Index index) {

		if (titleLabel != null) {
			if (index != null)
				titleLabel.setText(I18n.getMessage("thaw.plugin.index.comment.commentListTitle")
						+ " '" + index.toString() + "'"
						+ " :");
			else
				hideTab();
		}

		Vector v = new Vector();
		v.add(index);

		for (Iterator it = buttonActions.iterator();
			 it.hasNext(); ) {
			IndexManagementHelper.IndexAction action = (IndexManagementHelper.IndexAction) it.next();
			action.setTargets(v);
		}

		this.index = index;

		if (visible)
			updateCommentList();
	}

	public void showTab() {
		if (index == null)
			return;

		if (visible) {
			updateCommentList();
			indexBrowser.getMainWindow().setSelectedTab(tabPanel);
			return;
		}

		Logger.info(this, "Showing comment tab");

		updateCommentList();

		indexBrowser.getMainWindow().addTab(I18n.getMessage("thaw.plugin.index.comment.commentList"),
				thaw.gui.IconBox.readComments,
				tabPanel);
		indexBrowser.getMainWindow().setSelectedTab(tabPanel);

		visible = true;

	}

	public void hideTab() {
		if (!visible)
			return;

		Logger.info(this, "Hiding comment tab");

		indexBrowser.getMainWindow().removeTab(tabPanel);
		indexBrowser.getMainWindow().setSelectedTab(indexBrowser.getPanel());

		visible = false;
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == closeTabButton)
			hideTab();
	}

}
