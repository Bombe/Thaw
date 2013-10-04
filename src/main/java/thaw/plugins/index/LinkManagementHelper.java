package thaw.plugins.index;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.List;
import javax.swing.AbstractButton;

import thaw.core.Logger;
import thaw.core.ThawRunnable;
import thaw.core.ThawThread;
import thaw.fcp.FCPQueueManager;

public class LinkManagementHelper {

	public interface LinkAction extends ActionListener {

		/**
		 * Can disable the abstract button if required
		 *
		 * @param links
		 * 		can be null
		 */
		public void setTarget(List<Link> links);
	}

	public static abstract class BasicLinkAction implements LinkAction, ThawRunnable {

		private AbstractButton src;

		public BasicLinkAction(AbstractButton src) {
			this.src = src;
		}

		public void actionPerformed(ActionEvent e) {
			if (e.getSource() == src) {
				Thread th = new Thread(new ThawThread(this, "Action replier", this));
				th.start();
			}
		}

		public void run() {
			apply();
		}

		public void stop() {
			/* \_o< */
		}

		public abstract void apply();
	}

	public static class LinkRemover implements LinkAction {

		private IndexBrowserPanel indexBrowser;

		private AbstractButton actionSource;

		private List<Link> target;

		public LinkRemover(IndexBrowserPanel indexBrowser, final AbstractButton actionSource) {
			this.actionSource = actionSource;
			this.indexBrowser = indexBrowser;
			if (actionSource != null)
				actionSource.addActionListener(this);
		}

		public void setTarget(final List<Link> target) {
			boolean isOk;

			isOk = true;

			this.target = target;

			if (target != null && target.size() > 0) {
				Link link = (Link) target.get(0);
				if (!link.isModifiable())
					isOk = false;
			}

			actionSource.setEnabled((target != null) && (target.size() > 0) && isOk);
		}

		public void actionPerformed(final ActionEvent e) {
			LinkManagementHelper.removeLinks(indexBrowser, target);
		}
	}

	public static void removeLinks(IndexBrowserPanel indexBrowser, final List<Link> links) {
		if (links == null)
			return;

		for (Link link : links) {
			link.delete();
		}

		indexBrowser.getTables().getLinkTable().refresh();
	}

	public static class IndexAdder extends BasicLinkAction {

		private FCPQueueManager queueManager;

		private IndexBrowserPanel indexBrowser;

		private AbstractButton src;

		private List<Link> t;

		private boolean addToParent; /* (== add to the same parent folder) */

		private boolean autoSorting = true;

		/**
		 * @param addToParent
		 * 		Try to add the new index to the same folder than the index from where
		 * 		comes the link
		 */
		public IndexAdder(final AbstractButton actionSource,
						  final FCPQueueManager queueManager,
						  final IndexBrowserPanel indexBrowser,
						  boolean addToParent) {
			super(actionSource);

			src = actionSource;
			if (actionSource != null)
				actionSource.addActionListener(this);
			this.queueManager = queueManager;
			this.indexBrowser = indexBrowser;
			this.addToParent = addToParent;
		}

		public void setTarget(final List<Link> targets) {
			t = targets;
			src.setEnabled((targets != null) && (targets.size() > 0));
		}

		public void setAutoSorting(boolean b) {
			autoSorting = b;
		}

		public void apply() {
			for (final Iterator it = t.iterator();
				 it.hasNext(); ) {
				final Link link = (Link) it.next();
				if (link != null) {
					if (addToParent && link.getTreeParent() != null)
						IndexManagementHelper.addIndex(queueManager, indexBrowser,
								((IndexFolder) link.getTreeParent().getParent()),
								link.getPublicKey(), autoSorting);
					else
						IndexManagementHelper.addIndex(queueManager, indexBrowser,
								null, link.getPublicKey(), autoSorting);
				}
			}
		}
	}

	public static class PublicKeyCopier implements LinkAction {

		private AbstractButton src;

		private List<Link> t;

		public PublicKeyCopier(final AbstractButton actionSource) {
			src = actionSource;
			if (actionSource != null)
				actionSource.addActionListener(this);
		}

		public void setTarget(final List<Link> targets) {
			t = targets;
			src.setEnabled((targets != null) && (targets.size() > 0));
		}

		public void actionPerformed(final ActionEvent e) {
			LinkManagementHelper.copyPublicKeyFrom(t);
		}
	}

	public static void copyPublicKeyFrom(final List<Link> targets) {
		String keys = "";

		if (targets == null)
			return;

		final Toolkit tk = Toolkit.getDefaultToolkit();

		for (Link link : targets) {
			keys = keys + link.getPublicKey() + "\n";
		}

		final StringSelection st = new StringSelection(keys);
		final Clipboard cp = tk.getSystemClipboard();
		cp.setContents(st, null);
	}

	public static class BlackListDisplayer implements LinkAction {

		private AbstractButton src;

		private BlackList blackList;

		public BlackListDisplayer(final AbstractButton actionSource, BlackList blackList) {
			src = actionSource;

			if (actionSource != null)
				actionSource.addActionListener(this);

			this.blackList = blackList;
		}

		public void setTarget(final List<Link> targets) {
			src.setEnabled(true);
		}

		public void actionPerformed(final ActionEvent e) {
			blackList.displayPanel();
		}
	}

	public static class ToBlackListAdder implements LinkAction {

		private AbstractButton src;

		private List<Link> t;

		private IndexBrowserPanel indexBrowser;

		public ToBlackListAdder(final AbstractButton actionSource, IndexBrowserPanel indexBrowser) {
			src = actionSource;

			this.indexBrowser = indexBrowser;

			if (actionSource != null)
				actionSource.addActionListener(this);
		}

		public void setTarget(final List<Link> targets) {
			t = targets;
			src.setEnabled((targets != null) && (targets.size() > 0));
		}

		public void actionPerformed(final ActionEvent e) {
			if (t == null) {
				Logger.error(this, "No target !?");
				return;
			}

			for (Iterator it = t.iterator();
				 it.hasNext(); ) {
				Link link = (Link) it.next();

				BlackList.addToBlackList(indexBrowser.getDb(), link.getPublicKey());
				indexBrowser.getUnknownIndexList().removeLink(link);
			}

			indexBrowser.getBlackList().updateList();
		}
	}
}
