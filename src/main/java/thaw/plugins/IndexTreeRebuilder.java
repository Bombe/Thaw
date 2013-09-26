package thaw.plugins;

import javax.swing.ImageIcon;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Iterator;
import java.util.Vector;

import thaw.core.Core;
import thaw.core.I18n;
import thaw.core.Logger;
import thaw.core.Plugin;
import thaw.core.ThawRunnable;
import thaw.core.ThawThread;
import thaw.gui.WarningWindow;

public class IndexTreeRebuilder implements Plugin {

	private Core core;

	private Hsqldb db;

	public IndexTreeRebuilder() {
	}

	private class Rebuilder implements ThawRunnable {

		private boolean running;

		private Plugin parent;

		public Rebuilder(Plugin parent) {
			running = true;
			this.parent = parent;
		}

		private void rebuildIndex(Vector parents, int indexId) throws SQLException {
			synchronized (db.dbLock) {
				PreparedStatement st =
						db.getConnection().prepareStatement("INSERT INTO indexParents " +
								"(indexId, folderId) " +
								"VALUES (?, ?)");

				for (Iterator it = parents.iterator();
					 it.hasNext(); ) {
					st.setInt(1, indexId);

					int parent = ((Integer) it.next()).intValue();

					if (parent >= 0)
						st.setInt(2, parent);
					else
						st.setNull(2, Types.INTEGER);

					st.execute();
				}

				st.close();
			}
		}

		/**
		 * rebuild == rebuild the content of indexParents and folderParents
		 *
		 * @param parents
		 * 		Integer vector (id of the parent folders)
		 */
		private void rebuild(Vector parents, int folderId) throws SQLException {

			Vector newParentsVector = new Vector(parents);
			newParentsVector.add(new Integer(folderId));

			synchronized (db.dbLock) {
				PreparedStatement st;

				/* rebuild all the indexes in the subfolders */

				String where = ((folderId >= 0) ?
						"WHERE parent = ?" :
						"WHERE parent IS NULL");

				st = db.getConnection().prepareStatement("SELECT id FROM indexFolders " + where);

				if (folderId >= 0)
					st.setInt(1, folderId);

				ResultSet set = st.executeQuery();

				while (set.next()) {
					rebuild(newParentsVector, set.getInt("id"));
				}

				st.close();

				/* rebuild all the indexes in this folder */

				st = db.getConnection().prepareStatement("SELECT id FROM indexes " + where);

				if (folderId >= 0)
					st.setInt(1, folderId);

				set = st.executeQuery();

				while (set.next()) {
					rebuildIndex(newParentsVector, set.getInt("id"));
				}

				st.close();

				/* rebuild this folder */

				st = db.getConnection().prepareStatement("INSERT INTO folderParents " +
						"(folderId, parentId) " +
						"VALUES (?, ?)");

				for (Iterator it = parents.iterator();
					 it.hasNext(); ) {
					if (folderId >= 0)
						st.setInt(1, folderId);
					else
						st.setNull(1, Types.INTEGER);

					int parent = ((Integer) it.next()).intValue();

					if (parent >= 0)
						st.setInt(2, parent);
					else
						st.setNull(2, Types.INTEGER);

					st.execute();
				}

				st.close();
			}
		}

		public void rebuild() throws SQLException {
			synchronized (db.dbLock) {
				/* quick & dirty, as usual */
				PreparedStatement st =
						db.getConnection().prepareStatement("DELETE FROM indexParents");
				st.execute();
				st.close();

				st = db.getConnection().prepareStatement("DELETE FROM folderParents");
				st.execute();
				st.close();

				rebuild(new Vector(), -1);
			}
		}

		public void run() {

			if (core.getPluginManager().getPlugin("thaw.plugins.Hsqldb") == null) {
				Logger.info(this, "Loading Hsqldb plugin");

				if (core.getPluginManager().loadPlugin("thaw.plugins.Hsqldb") == null
						|| !core.getPluginManager().runPlugin("thaw.plugins.Hsqldb")) {
					Logger.error(this, "Unable to load thaw.plugins.Hsqldb !");
					return;
				}
			}

			db = (Hsqldb) core.getPluginManager().getPlugin("thaw.plugins.Hsqldb");

			if (db == null) {
				Logger.error(this, "Can't access the db !");
			} else {

				db.registerChild(parent);

				if (running)
					core.getPluginManager().stopPlugin("thaw.plugins.IndexBrowser");

				if (running) {
					try {
						rebuild();
					} catch (SQLException e) {
						/* wow, getting creepy */
						Logger.error(this, "Index tree rebuild failed : " + e.toString());
						new WarningWindow(core,
								I18n.getMessage("thaw.plugin.index.treeRebuilder.failed"));
					}
				}

				if (running)
					new WarningWindow(core,
							I18n.getMessage("thaw.plugin.index.treeRebuilder.finished"));

				if (running)
					core.getPluginManager().runPlugin("thaw.plugins.IndexBrowser");

				db.unregisterChild(parent);
			}

			core.getPluginManager().stopPlugin("thaw.plugins.IndexTreeRebuilder");
			core.getPluginManager().unloadPlugin("thaw.plugins.IndexTreeRebuilder");

			core.getConfigWindow().getPluginConfigPanel().refreshList();
		}

		public void stop() {
			running = false;
		}
	}

	public boolean run(Core core) {
		this.core = core;

		Thread th = new Thread(new ThawThread(new Rebuilder(this),
				"Index tree rebuilder",
				this));
		th.start();

		return true;
	}

	public void stop() {

	}

	public String getNameForUser() {
		return I18n.getMessage("thaw.plugin.index.treeRebuilder");
	}

	public ImageIcon getIcon() {
		return null;
	}
}

