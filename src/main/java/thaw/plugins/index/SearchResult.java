package thaw.plugins.index;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import thaw.core.Logger;
import thaw.plugins.Hsqldb;

public class SearchResult implements FileAndLinkList {

	private String[] search = null;

	private Hsqldb db;

	private IndexTreeNode node;

	public SearchResult(final Hsqldb hsqldb, final String search, final IndexTreeNode node) {
		this.search = search.split(" ");
		this.node = node;
		db = hsqldb;
	}

	public String getWhereClause(boolean hasFilename) {
		String where = null;
		String column = hasFilename ? "filename" : "publicKey";

		if (node instanceof IndexFolder) {
			if (node.getId() >= 0) {
				where = "indexParent IN " +
						"(SELECT indexParents.indexId FROM indexParents WHERE indexParents.folderId = ?)";
			}
		}

		if (node instanceof Index) {
			where = "indexParent = ? ";
		}

		for (int i = 0; i < search.length; i++) {
			if (where == null) {
				where = " LOWER(" + column + ") LIKE ?";
			} else {
				where += " AND LOWER(" + column + ") LIKE ?";
			}
		}

		return where;
	}

	public void fillInStatement(PreparedStatement st) throws SQLException {
		int i, j;

		i = 1;

		if ((node instanceof Index)
				|| node.getId() >= 0) {
			st.setInt(i, node.getId());
			i++;
		}

		for (j = 0; j < search.length; j++) {
			st.setString(i, "%" + search[j].toLowerCase() + "%");
			i++;
		}
	}

	public List<File> getFileList(String col, boolean asc) {

		if (col == null)
			col = "filename";

		List<File> v = new ArrayList<File>();

		synchronized (db.dbLock) {
			try {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT id, filename, publicKey, localPath, mime, size, indexParent " +
						"FROM files " +
						"WHERE " + getWhereClause(true) + " ORDER by " + col + (asc ? "" : " DESC"));

				fillInStatement(st);

				ResultSet set = st.executeQuery();

				while (set.next()) {
					v.add(new File(db,
							set.getInt("id"),
							set.getString("filename"),
							set.getString("publicKey"),
							(set.getString("localPath") != null ? new java.io.File(set.getString("localPath")) : null),
							set.getString("mime"),
							set.getLong("size"),
							set.getInt("indexParent")));
				}

				st.close();

			} catch (SQLException e) {
				Logger.error(this, "Error while searching: " + e.toString());
			}
		}

		return v;
	}

	public List<Link> getLinkList(String col, boolean asc) {
		List<Link> v = new ArrayList<Link>();

		synchronized (db.dbLock) {
			try {
				PreparedStatement st;

				st = db.getConnection().prepareStatement("SELECT links.id AS id, " +
						" links.publicKey AS publicKey, " +
						" links.blackListed AS blacklisted," +
						" links.indexParent AS indexParent, " +
						" categories.name AS categoryName " +
						" FROM links LEFT OUTER JOIN categories " +
						" ON links.category = categories.id " +
						"WHERE " + getWhereClause(false));
				fillInStatement(st);
				ResultSet set = st.executeQuery();

				while (set.next()) {
					if (!set.getBoolean("blackListed")) {
						v.add(new Link(db,
								set.getInt("id"),
								set.getString("publicKey"),
								set.getString("categoryName"),
								false,
								set.getInt("indexParent")));
					}
				}

				st.close();

			} catch (SQLException e) {
				Logger.error(this, "Error while searching: " + e.toString());
			}
		}

		return v;
	}

}
