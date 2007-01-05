package thaw.plugins.index;

import thaw.core.Logger;
import thaw.core.I18n;
import thaw.fcp.FCPQueueManager;
import thaw.plugins.Hsqldb;


public class IndexRoot extends IndexCategory {

	private FCPQueueManager queueManager;
	private IndexBrowserPanel indexBrowser;

	public IndexRoot(final FCPQueueManager queueManager, final IndexBrowserPanel indexBrowser, final String name) {
		super(queueManager, indexBrowser, -1, null, name);
		this.queueManager = queueManager;
		this.indexBrowser = indexBrowser;
	}


	public IndexCategory getNewImportFolder() {
		int idx;
		String name;
		IndexTreeNode node;
		IndexCategory importFolder;

		if (!areChildrenLoaded())
			loadChildren();

		idx = 0;
		name = I18n.getMessage("thaw.plugin.index.importedFolderName");
		node = getDirectChild(name);

		while (node != null) {
			idx++;
			name = I18n.getMessage("thaw.plugin.index.importedFolderName") + " - "+Integer.toString(idx);
			node = getDirectChild(name);
		}

		importFolder = IndexManagementHelper.addIndexCategory(queueManager, indexBrowser, this, name);

		return importFolder;
	}

}
