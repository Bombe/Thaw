package thaw.plugins.transferLogs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.util.ArrayList;
import java.util.List;

import thaw.core.Config;
import thaw.core.I18n;
import thaw.core.Logger;
import thaw.gui.GUIHelper;
import thaw.gui.IconBox;
import thaw.gui.Table;
import thaw.plugins.Hsqldb;
import thaw.plugins.TransferLogs;
import thaw.plugins.transferLogs.TransferManagementHelper.TransferAction;

/**
 * Allow to see entry page per page. columns : "dates" (start,end), transfer
 * "type", "filename","key already seen?" . A button "details" is added in each
 * row of the column "filename". Button "details" add in the column "filename"
 * the following informations: <ul> <li>Key</li> <li>Was finished ?</li> <li>If
 * finished : Average speed</li> </ul> <br/> right click menu : copy key(s) to
 * clipboard
 */
public class TransferTable implements MouseListener {

	public final static int NMB_ELEMENTS_PER_PAGE = 100;

	public final static int DEFAULT_LINE_HEIGHT = 18;

	public final static int MAX_LINE_HEIGHT = 100;

	public final static String[] COLUMN_NAMES = {
			I18n.getMessage("thaw.plugin.transferLogs.dates"),
			I18n.getMessage("thaw.plugin.transferLogs.type"),
			I18n.getMessage("thaw.plugin.transferLogs.file"),
			I18n.getMessage("thaw.plugin.transferLogs.isDup")
	};

	private JPanel panel;

	private TransferTableModel model;

	private PageSelecter pageSelecter;

	private Table table;

	private JPopupMenu rightClickMenu;

	private final List<TransferAction> rightClickActions = new ArrayList<TransferAction>();

	private TransferManagementHelper.TransferRemover remover;

	private DateFormat dateFormat;

	public TransferTable(Hsqldb db, Config config) {
		this.dateFormat = DateFormat.getDateTimeInstance();

		rightClickMenu = new JPopupMenu();

		JMenuItem item;

		item = new JMenuItem(I18n.getMessage("thaw.common.remove"), IconBox.minDelete);
		rightClickMenu.add(item);
		rightClickActions.add(new TransferManagementHelper.TransferRemover(item, this));

		item = new JMenuItem(I18n.getMessage("thaw.common.copyKeysToClipboard"), IconBox.minCopy);
		rightClickMenu.add(item);
		rightClickActions.add(new TransferManagementHelper.TransferKeyCopier(item));

		panel = new JPanel(new BorderLayout(5, 5));

		model = new TransferTableModel(db);
		pageSelecter = new PageSelecter(db, model);
		table = new Table(config, "table_transfer_logs", model);
		table.setDefaultRenderer(table.getColumnClass(0), new TransferTableRenderer());
		table.addMouseListener(this);

		table.setShowGrid(true);
		table.setIntercellSpacing(new Dimension(1, 1));

		panel.add(new JScrollPane(table), BorderLayout.CENTER);
		panel.add(pageSelecter.getPanel(), BorderLayout.SOUTH);

		remover = new TransferManagementHelper.TransferRemover(null, this);
		table.addKeyListener(remover);

		refresh();
	}

	public JPanel getPanel() {
		return panel;
	}

	protected class TransferTableRenderer extends DefaultTableCellRenderer {

		/**
		 *
		 */
		private static final long serialVersionUID = 4227879912938245537L;

		private Color softGray;

		private Color lightBlue;

		private JTextArea textAreaRenderer;

		private JLabel labelRenderer;

		public TransferTableRenderer() {
			softGray = new Color(240, 240, 240);
			lightBlue = new Color(220, 220, 255);

			labelRenderer = new JLabel("", JLabel.CENTER);

			textAreaRenderer = new JTextArea();
			textAreaRenderer.setEditable(false);
			textAreaRenderer.setLineWrap(false);
		}

		public Component getTableCellRendererComponent(final JTable table, Object value,
													   final boolean isSelected, final boolean hasFocus,
													   final int row, final int column) {
			Component cell;

			if (value instanceof String && ((String) value).indexOf("\n") >= 0) {
				textAreaRenderer.setText((String) value);
				cell = textAreaRenderer;

			} else if ((value instanceof String) && "X".equals(value)) {
				labelRenderer.setIcon(IconBox.minClose);
				return labelRenderer;
			} else if (value instanceof Integer) {
				int val = ((Integer) value).intValue();

				if (val == 0) {
					value = TransferLogs.TRANSFER_TYPE_NAMES[val];
					cell = super.getTableCellRendererComponent(table, value, isSelected,
							hasFocus, row, column);
				} else {
					ImageIcon icon;

					if (isSelected)
						icon = (val == TransferLogs.TRANSFER_TYPE_DOWNLOAD) ?
								IconBox.downloads : IconBox.insertions;
					else
						icon = (val == TransferLogs.TRANSFER_TYPE_DOWNLOAD) ?
								IconBox.minDownloads : IconBox.minInsertions;

					labelRenderer.setIcon(icon);

					return labelRenderer;
				}

			} else {
				cell = super.getTableCellRendererComponent(table, value, isSelected,
						hasFocus, row, column);
			}

			if (!isSelected) {
				if (row % 2 == 0)
					cell.setBackground(Color.WHITE);
				else
					cell.setBackground(softGray);
			} else {
				cell.setBackground(lightBlue);
			}

			/*
			if (isSelected) {
				if (table.getRowHeight(row) < (cell.getPreferredSize().getHeight()+5))
					table.setRowHeight((int)cell.getPreferredSize().getHeight()+5);
			} else {
				if (table.getRowHeight(row) > DEFAULT_LINE_HEIGHT)
					table.setRowHeight(row, DEFAULT_LINE_HEIGHT);
			}
			*/

			return cell;
		}

	}

	protected class TransferTableModel
			extends AbstractTableModel {

		/**
		 *
		 */
		private static final long serialVersionUID = -4078889047070552383L;

		private int page;

		private List<Transfer> transfers = null;

		private final Hsqldb db;

		public TransferTableModel(Hsqldb db) {
			super();

			this.db = db;
			this.page = 0;
		}

		public int getRowCount() {
			if (transfers != null)
				return transfers.size();
			else
				return 0;
		}

		public int getColumnCount() {
			return COLUMN_NAMES.length;
		}

		public String getColumnName(int col) {
			return COLUMN_NAMES[col];
		}

		public String getAverageSpeed(Transfer t) {
			long v = t.getAverageSpeed();

			if (v < 0)
				return I18n.getMessage("thaw.common.unknown");

			return GUIHelper.getPrintableSize(v) + "/s";
		}

		public Object getValueAt(final int row, int column) {
			if (transfers == null)
				return null;

			if (row > transfers.size())
				return null;

			Transfer t = transfers.get(row);

			if (column == 0) { /* dates */
				String dates = I18n.getMessage("thaw.plugin.transferLogs.dateStart") + " ";
				dates += dateFormat.format(t.getDateStart()) + "\n";
				dates += I18n.getMessage("thaw.plugin.transferLogs.dateEnd") + " ";
				if (t.getDateEnd() != null)
					dates += dateFormat.format(t.getDateEnd());
				else
					dates += I18n.getMessage("thaw.common.unknown");
				return dates;
			}

			if (column == 1) { /* type */
				return new Integer((int) t.getTransferTypeByte());
			}

			if (column == 2) { /* file */
				String str = " " + t.getFilename() + "\n";
				str += I18n.getMessage("thaw.plugin.transferLogs.key") + ": ";
				str += (t.getKey() != null ? t.getKey() : I18n.getMessage("thaw.common.unknown")) + "\n";
				str += I18n.getMessage("thaw.plugin.transferLogs.fileSize") + ": ";
				str += GUIHelper.getPrintableSize(t.getSize()) + "\n";
				str += I18n.getMessage("thaw.plugin.transferLogs.isSuccess") + ": ";
				if (t.getDateEnd() != null)
					str += (t.isSuccess() ? I18n.getMessage("thaw.common.yes") : I18n.getMessage("thaw.common.no")) + "\n";
				else
					str += I18n.getMessage("thaw.common.unknown") + "\n";
				str += I18n.getMessage("thaw.plugin.transferLogs.averageSpeed") + ": ";
				str += getAverageSpeed(t);
				return str;
			}

			if (column == 3) { /* isDup ? */
				return t.isDup() ? "X" : "";
			}

			return null;
		}

		public boolean isCellEditable(final int row, final int column) {
			return false;
		}

		public void setPage(int page) {
			this.page = page;
		}

		public int getPage() {
			return page;
		}

		public void refresh(int row) {
			fireTableChanged(new TableModelEvent(this, row));
		}

		public List<Transfer> getRows(int[] rows) {
			if (transfers == null)
				return null;

			List<Transfer> v = new ArrayList<Transfer>();
			for (int rowIndex : rows) {
				v.add(transfers.get(rowIndex));
			}

			return v;
		}

		public void refresh() {

			try {
				synchronized (db.dbLock) {
					PreparedStatement st;

					int offset = NMB_ELEMENTS_PER_PAGE * page;

					st = db.getConnection().prepareStatement("SELECT id, dateStart, " +
							"dateEnd, transferType, " +
							"key, filename, size, " +
							"isDup, isSuccess " +
							"FROM transferLogs " +
							"ORDER BY dateStart DESC " +
							"LIMIT " + Integer.toString(NMB_ELEMENTS_PER_PAGE) +
							" OFFSET " + Integer.toString(offset));
					ResultSet res = st.executeQuery();

					transfers = new ArrayList<Transfer>();

					while (res.next()) {
						transfers.add(new Transfer(db, res.getInt("id"),
								res.getTimestamp("dateStart"),
								res.getTimestamp("dateEnd"),
								res.getByte("transferType"),
								res.getString("key"),
								res.getString("filename"),
								res.getLong("size"),
								res.getBoolean("isDup"),
								res.getBoolean("isSuccess")));
					}

					st.close();
				}
			} catch (SQLException e) {
				Logger.error(this, "Error while reading transfer logs: " + e.toString());
				return;
			}

			fireTableChanged(new TableModelEvent(this));
		}

	}

	protected class PageSelecter implements ActionListener {

		private JPanel panel;

		private JButton leftButton;

		private JComboBox pageSelecter;

		private JButton rightButton;

		private final Hsqldb db;

		private TransferTableModel model;

		private int pageMax;

		public PageSelecter(Hsqldb db, TransferTableModel m) {
			this.db = db;
			this.model = m;

			panel = new JPanel(new BorderLayout(5, 5));

			leftButton = new JButton("<");
			pageSelecter = new JComboBox(new String[] { "0" });
			rightButton = new JButton(">");

			leftButton.addActionListener(this);
			pageSelecter.addActionListener(this);
			rightButton.addActionListener(this);

			JPanel centerPanel = new JPanel();
			centerPanel.add(new JLabel(I18n.getMessage("thaw.common.page")));
			centerPanel.add(pageSelecter);

			panel.add(leftButton, BorderLayout.WEST);
			panel.add(centerPanel, BorderLayout.CENTER);
			panel.add(rightButton, BorderLayout.EAST);
		}

		public JPanel getPanel() {
			return panel;
		}

		public void refresh() {
			int nmb_elements = -1;

			try {
				synchronized (db.dbLock) {
					PreparedStatement st;

					st = db.getConnection().prepareStatement("SELECT count(id) FROM transferLogs");
					ResultSet res = st.executeQuery();
					res.next();

					nmb_elements = res.getInt(1);

					st.close();
				}
			} catch (SQLException e) {
				Logger.error(this, "Unable to compute the number of pages in the logs because : " + e.toString());
				return;
			}

			pageMax = nmb_elements / NMB_ELEMENTS_PER_PAGE;

			if (nmb_elements % NMB_ELEMENTS_PER_PAGE == 0)
				pageMax--;

			if (model.getPage() > pageMax)
				model.setPage(0);

			pageSelecter.setSelectedItem("0");

			pageSelecter.removeAllItems();

			for (int i = 0; i <= pageMax; i++)
				pageSelecter.addItem(Integer.toString(i));

			/* should call actionPerformed() */
			pageSelecter.setSelectedItem(Integer.toString(model.getPage()));

			refreshButtonState();
		}

		private void refreshButtonState() {
			leftButton.setEnabled(model.getPage() > 0);
			rightButton.setEnabled(model.getPage() < pageMax);
		}

		public void actionPerformed(ActionEvent e) {
			int targetPage = -1;

			if (e.getSource() == leftButton) {
				targetPage = model.getPage() - 1;
			} else if (e.getSource() == rightButton) {
				targetPage = model.getPage() + 1;
			} else if (e.getSource() == pageSelecter) {
				if (pageSelecter.getSelectedItem() == null
						|| !(pageSelecter.getSelectedItem() instanceof String))
					return;

				targetPage = Integer.parseInt((String) pageSelecter.getSelectedItem());
			}

			if (targetPage < 0 || targetPage > pageMax)
				return;

			model.setPage(targetPage);
			model.refresh();

			pageSelecter.removeActionListener(this);
			pageSelecter.setSelectedItem(Integer.toString(targetPage));
			pageSelecter.addActionListener(this);

			refreshButtonState();
		}
	}

	public void refresh() {
		model.refresh();
		pageSelecter.refresh();
	}

	private void updateToolbar(List<Transfer> selection) {

	}

	private void updateRightClickMenu(List<Transfer> selection) {
		for (TransferAction action : rightClickActions) {
			action.setTarget(selection);
		}
	}

	public void adjustRowHeights() {
		for (int i = table.getRowCount() - 1; i >= 0; i--) {
			if (table.isRowSelected(i))
				table.setRowHeight(i, MAX_LINE_HEIGHT);
			else
				table.setRowHeight(i, DEFAULT_LINE_HEIGHT);
		}
	}

	public void mouseClicked(final MouseEvent e) {
		List<Transfer> selection;

		int[] selectedRows = table.getSelectedRows();

		if (selectedRows == null)
			return;

		selection = model.getRows(selectedRows);

		if (selection == null)
			return;

		if (e.getButton() == MouseEvent.BUTTON1) {
			adjustRowHeights();
			updateToolbar(selection);
			remover.setTarget(selection);
		}

		if (e.getButton() == MouseEvent.BUTTON3) {
			updateRightClickMenu(selection);
			rightClickMenu.show(e.getComponent(), e.getX(), e.getY());
		}
	}

	public void mouseEntered(final MouseEvent e) {
	}

	public void mouseExited(final MouseEvent e) {
	}

	public void mousePressed(final MouseEvent e) {
	}

	public void mouseReleased(final MouseEvent e) {
		adjustRowHeights();
	}

}
