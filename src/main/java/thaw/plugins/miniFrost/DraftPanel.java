package thaw.plugins.miniFrost;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;
import java.util.Vector;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import thaw.core.I18n;
import thaw.core.Logger;
import thaw.core.ThawRunnable;
import thaw.core.ThawThread;
import thaw.gui.FileChooser;
import thaw.gui.IconBox;
import thaw.plugins.miniFrost.interfaces.Attachment;
import thaw.plugins.miniFrost.interfaces.Board;
import thaw.plugins.miniFrost.interfaces.Draft;
import thaw.plugins.signatures.Identity;

public class DraftPanel implements ActionListener, MouseListener {

	public final static int ATTACHMENT_LIST_HEIGHT = 50;

	private Draft draft;

	private JPanel panel;

	private MiniFrostPanel mainPanel;

	private JLabel boardLabel;

	private JComboBox authorBox;

	private JTextField subjectField;

	private JTextArea textArea;

	private JComboBox recipientBox;

	private JButton cancelButton;

	private JButton sendButton;

	private JButton extractButton;

	private JButton addAttachment;

	private JList attachmentList;

	private JPopupMenu attachmentRightClickMenu;

	private JMenuItem attachmentRemove;

	private JDialog dialog;

	private final SimpleDateFormat gmtConverter;

	private final SimpleDateFormat dateParser;

	private final SimpleDateFormat messageDateFormat;

	public DraftPanel(MiniFrostPanel mainPanel) {
		this.mainPanel = mainPanel;

		gmtConverter = new SimpleDateFormat("yyyy.M.d HH:mm:ss");
		gmtConverter.setTimeZone(TimeZone.getTimeZone("GMT"));
		dateParser = new SimpleDateFormat("yyyy.M.d HH:mm:ss");
		messageDateFormat = new SimpleDateFormat("yyyy.MM.dd - HH:mm:ss");

		panel = new JPanel(new BorderLayout(5, 5));

		/* author box */

		authorBox = new JComboBox();
		authorBox.setEditable(true);
		authorBox.setBackground(Color.WHITE);

		subjectField = new JTextField("");
		subjectField.setEditable(true);

		/* recipient box */

		recipientBox = new JComboBox();

		/* content will be updated when setDraft() will be called
		 * to take into consideration people marked as GOOD recently
		 */

		textArea = new JTextArea("");
		textArea.setEditable(true);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		textArea.setFont(textArea.getFont().deriveFont((float) 13.5));

		boardLabel = new JLabel("");
		extractButton = new JButton(IconBox.minWindowNew);
		extractButton.setToolTipText(I18n.getMessage("thaw.plugin.miniFrost.newWindow"));
		extractButton.addActionListener(this);

		JPanel northPanel = new JPanel(new BorderLayout(5, 5));

		JPanel headersPanel = new JPanel(new GridLayout(4, 1));
		headersPanel.add(new JLabel(I18n.getMessage("thaw.plugin.miniFrost.board") + ": "));
		headersPanel.add(new JLabel(I18n.getMessage("thaw.plugin.miniFrost.author") + ": "));
		headersPanel.add(new JLabel(I18n.getMessage("thaw.plugin.miniFrost.recipient") + ": "));
		headersPanel.add(new JLabel(I18n.getMessage("thaw.plugin.miniFrost.subject") + ": "));

		JPanel valuesPanel = new JPanel(new GridLayout(4, 1));

		JPanel topPanel = new JPanel(new BorderLayout(5, 5));
		topPanel.add(boardLabel, BorderLayout.CENTER);
		topPanel.add(extractButton, BorderLayout.EAST);

		valuesPanel.add(topPanel);
		valuesPanel.add(authorBox);
		valuesPanel.add(recipientBox);
		valuesPanel.add(subjectField);

		northPanel.add(headersPanel, BorderLayout.WEST);
		northPanel.add(valuesPanel, BorderLayout.CENTER);

		JPanel southPanel = new JPanel(new GridLayout(1, 2));

		cancelButton = new JButton(I18n.getMessage("thaw.common.cancel"));
		sendButton = new JButton(I18n.getMessage("thaw.common.ok"));
		cancelButton.addActionListener(this);
		sendButton.addActionListener(this);

		southPanel.add(sendButton);
		southPanel.add(cancelButton);

		JPanel centerPanel = new JPanel(new BorderLayout(3, 3));

		JPanel southCenterPanel = new JPanel(new BorderLayout(3, 3));
		addAttachment = new JButton(IconBox.attachment);
		addAttachment.addActionListener(this);
		addAttachment.setPreferredSize(new Dimension(ATTACHMENT_LIST_HEIGHT,
				ATTACHMENT_LIST_HEIGHT));
		attachmentList = new JList();
		attachmentList.setCellRenderer(new AttachmentRenderer());
		attachmentList.addMouseListener(this);
		attachmentList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		attachmentList.setPreferredSize(new Dimension(ATTACHMENT_LIST_HEIGHT,
				ATTACHMENT_LIST_HEIGHT));

		JScrollPane attListScrollPanel = new JScrollPane(attachmentList);
		attListScrollPanel.setPreferredSize(new Dimension(ATTACHMENT_LIST_HEIGHT,
				ATTACHMENT_LIST_HEIGHT));

		southCenterPanel.add(addAttachment, BorderLayout.WEST);
		southCenterPanel.add(attListScrollPanel, BorderLayout.CENTER);

		centerPanel.add(new JScrollPane(textArea), BorderLayout.CENTER);
		centerPanel.add(southCenterPanel, BorderLayout.SOUTH);

		panel.add(northPanel, BorderLayout.NORTH);
		panel.add(centerPanel, BorderLayout.CENTER);
		panel.add(southPanel, BorderLayout.SOUTH);

		attachmentRightClickMenu = new JPopupMenu();
		attachmentRemove = new JMenuItem(I18n.getMessage("thaw.common.remove"));
		attachmentRemove.addActionListener(this);
		attachmentRightClickMenu.add(attachmentRemove);
	}

	public DraftPanel(MiniFrostPanel mainPanel, JDialog dialog) {
		this(mainPanel);
		this.dialog = dialog;
		extractButton.setEnabled(false);
	}

	protected class AttachmentRenderer extends DefaultListCellRenderer {

		/**
		 *
		 */
		private static final long serialVersionUID = -3102106806638714133L;

		public AttachmentRenderer() {

		}

		public Component getListCellRendererComponent(final JList list, Object value,
															   final int index, final boolean isSelected,
															   final boolean cellHasFocus) {
			Attachment att = (Attachment) value;

			value = att.getPrintableType() + " : " + att.toString();

			return super.getListCellRendererComponent(list, value,
					index, isSelected,
					cellHasFocus);
		}
	}

	public void setDraft(Draft draft) {
		this.draft = draft;

		/* board */
		boardLabel.setText(draft.getBoard().toString());

		/* identity */
		Vector ids = new Vector();
		ids.add(I18n.getMessage("thaw.plugin.miniFrost.anonymous"));
		ids.addAll(Identity.getYourIdentities(mainPanel.getDb()));

		authorBox.removeAllItems();

		for (Iterator it = ids.iterator(); it.hasNext(); )
			authorBox.addItem(it.next());

		if (draft.getAuthorIdentity() != null)
			authorBox.setSelectedItem(draft.getAuthorIdentity());
		else if (draft.getAuthorNick() != null)
			authorBox.setSelectedItem(draft.getAuthorNick());
		else
			authorBox.setSelectedIndex(0);

		/* recipient */
		Vector nicePeople = new Vector();
		nicePeople.add(I18n.getMessage("thaw.plugin.miniFrost.recipient.all"));
		nicePeople.addAll(Identity.getIdentities(mainPanel.getDb(),
				"trustLevel >= " + Integer.toString(Identity.trustLevelInt[1])));

		recipientBox.removeAllItems();

		for (Iterator it = nicePeople.iterator(); it.hasNext(); ) {
			recipientBox.addItem(it.next());
		}

		recipientBox.setSelectedIndex(0);

		if (draft.getRecipient() != null) {
			recipientBox.setSelectedItem(draft.getRecipient());

			if (!recipientBox.getSelectedItem().equals(draft.getRecipient())) {
				/* then it means that the recipient wasn't in the list */
				recipientBox.addItem(draft.getRecipient());
				recipientBox.setSelectedItem(draft.getRecipient());
			}
		}


		/* subject */
		subjectField.setText(draft.getSubject());

		/* text */
		String txt = draft.getText();

		textArea.setText(txt);

		/* attachments */
		refreshAttachmentList();

		refresh();
	}

	private void refreshAttachmentList() {
		Vector v = null;

		if (draft != null)
			v = draft.getAttachments();

		if (v == null)
			v = new Vector();

		attachmentList.setListData(v);
	}

	public void refresh() {
		/* we don't want to erase by accident the current draft
		 * => we do nothing
		 */
	}

	public void hided() {

	}

	public void redisplayed() {
		textArea.requestFocus();
	}

	public JPanel getPanel() {
		return panel;
	}

	public Date getGMTDate() {
		/* dirty way to obtain the GMT date */
		String dateStr = gmtConverter.format(new Date());

		try {
			return dateParser.parse(dateStr);
		} catch (ParseException e) {
			Logger.warning(null, "DraftPanel : Can't get the GMT date => will use the local time");
			return new Date();
		}
	}

	/** Don't do the replacements in the text. Don't call Draft.setDate() */
	public void fillInDraft() {
		/* author */

		if (authorBox.getSelectedItem() instanceof Identity) {
			draft.setAuthor(authorBox.getSelectedItem().toString(),
					(Identity) authorBox.getSelectedItem());
		} else {
			String nick = authorBox.getSelectedItem().toString();
			nick = nick.replaceAll("@", "_");

			draft.setAuthor(nick, null);
		}

		/* recipient */

		if (recipientBox.getSelectedItem() instanceof Identity)
			draft.setRecipient((Identity) recipientBox.getSelectedItem());
		else
			draft.setRecipient(null);

		/* subject */

		draft.setSubject(subjectField.getText());

		/* text */

		String txt = textArea.getText();
		draft.setText(txt);
	}

	private JMenuItem addBoard = null;

	private JMenuItem addFile = null;

	private class BoardAdder implements ThawRunnable {

		public BoardAdder() {

		}

		public void run() {
			Vector boards = BoardSelecter.askBoardList(mainPanel,
					((dialog != null) ?
							(Object) dialog :
							(Object) mainPanel.getPluginCore().getCore().getMainWindow().getMainFrame()));

			if (boards == null) {
				Logger.info(this, "Cancelled");
				return;
			}

			for (Iterator it = boards.iterator();
				 it.hasNext(); ) {
				draft.addAttachment((Board) it.next());
			}

			refreshAttachmentList();
		}

		public void stop() { /* \_o< */ }
	}

	private class FileAdder implements ThawRunnable {

		public FileAdder() {

		}

		public void run() {
			String initialPath = mainPanel.getConfig().getValue("lastSourceDirectory");

			FileChooser chooser;

			chooser = ((initialPath != null) ? new FileChooser(initialPath) : new FileChooser());

			chooser.setTitle(I18n.getMessage("thaw.plugin.transferLogs.chooseFile"));
			chooser.setDirectoryOnly(false);
			chooser.setDialogType(JFileChooser.OPEN_DIALOG);

			Vector files = chooser.askManyFiles();

			if (files == null) {
				Logger.info(this, "Cancelled");
				return;
			}

			if (files.size() > 0) {
				mainPanel.getConfig().setValue("lastSourceDirectory", chooser.getFinalDirectory());
			}

			for (Iterator it = files.iterator();
				 it.hasNext(); ) {
				draft.addAttachment((File) it.next());
			}

			refreshAttachmentList();
		}

		public void stop() { /* \_o< */ }
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == addAttachment) {

			JPopupMenu menu = new JPopupMenu();

			menu.add((addBoard = new JMenuItem(I18n.getMessage("thaw.plugin.miniFrost.attachBoards"))));
			menu.add((addFile = new JMenuItem(I18n.getMessage("thaw.plugin.miniFrost.attachFiles"))));

			addBoard.addActionListener(this);
			addFile.addActionListener(this);

			menu.show(addAttachment,
					addAttachment.getWidth() / 2,
					addAttachment.getHeight() / 2);
			return;

		} else if (e.getSource() == addBoard) {

			Logger.info(this, "BoardAdder");

			new Thread((new ThawThread(new BoardAdder(), "Board attachment adder", this))).start();

			return;

		} else if (e.getSource() == addFile) {

			Logger.info(this, "FileAdder");

			new Thread((new ThawThread(new FileAdder(), "File attachment adder", this))).start();

			return;

		} else if (e.getSource() == attachmentRemove) {

			Object[] selection = attachmentList.getSelectedValues();

			for (Object attachment : selection) {
				draft.removeAttachment((Attachment) attachment);
			}

			refreshAttachmentList();

			return;

		} else if (e.getSource() == extractButton) {
			fillInDraft();

			JDialog newDialog = new JDialog(mainPanel.getPluginCore().getCore().getMainWindow().getMainFrame(),
					I18n.getMessage("thaw.plugin.miniFrost.draft"));
			newDialog.getContentPane().setLayout(new GridLayout(1, 1));

			DraftPanel panel = new DraftPanel(mainPanel, newDialog);

			panel.setDraft(draft);

			newDialog.getContentPane().add(panel.getPanel());

			newDialog.setSize(500, 500);

			newDialog.setVisible(true);

		} else if (e.getSource() == sendButton) {
			fillInDraft();

			Date date = getGMTDate();

			/* text */

			String txt = textArea.getText();

			int idLineLen = authorBox.getSelectedItem().toString().length();
			int idLinePos = txt.indexOf("$sender$");

			if (idLinePos <= -1) {
				idLinePos = 0;
				idLineLen = 0;
			}

			draft.setIdLinePos(idLinePos);
			draft.setIdLineLen(idLineLen);

			txt = txt.replaceAll("\\$sender\\$", authorBox.getSelectedItem().toString());

			String dateStr = messageDateFormat.format(date).toString();
			txt = txt.replaceAll("\\$dateAndTime\\$", dateStr);

			draft.setText(txt);


			/* date */
			draft.setDate(date);


			/* POST */
			draft.post(mainPanel.getPluginCore().getCore().getQueueManager());

		}
		if (e.getSource() == cancelButton) {

		}

		if (dialog == null)
			mainPanel.displayMessageTable();
		else {
			dialog.setVisible(false);
			dialog.dispose();
			dialog = null;
		}
	}

	public void mouseClicked(final MouseEvent e) {

	}

	public void mouseEntered(final MouseEvent e) {
	}

	public void mouseExited(final MouseEvent e) {
	}

	public void mousePressed(final MouseEvent e) {
		showPopupMenu(e);
	}

	public void mouseReleased(final MouseEvent e) {
		showPopupMenu(e);
	}

	protected void showPopupMenu(final MouseEvent e) {
		if (e.isPopupTrigger()) {
			attachmentRightClickMenu.show(e.getComponent(), e.getX(), e.getY());
		}
	}
}
