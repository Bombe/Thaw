package thaw.gui;

import java.net.URL;
import javax.swing.ImageIcon;

import thaw.core.Logger;

/** This class is simply an helper to find and load quickly some common icons. */
public class IconBox {

	/** Freenet logo :) */
	public static ImageIcon blueBunny;

	public static ImageIcon connectAction;

	public static ImageIcon minConnectAction;

	public static ImageIcon disconnectAction;

	public static ImageIcon minDisconnectAction;

	public static ImageIcon stop;

	public static ImageIcon minStop;

	public static ImageIcon copy;

	public static ImageIcon minCopy;

	public static ImageIcon queue;

	public static ImageIcon minQueue;

	public static ImageIcon insertions;

	public static ImageIcon minInsertions;

	public static ImageIcon downloads;

	public static ImageIcon minDownloads;

	public static ImageIcon indexBrowser;

	public static ImageIcon minIndexBrowser;

	public static ImageIcon addToIndexAction;

	public static ImageIcon insertAndAddToIndexAction;

	public static ImageIcon makeALinkAction;

	public static ImageIcon minMakeALinkAction;

	public static ImageIcon minIndex;

	public static ImageIcon minIndexReadOnly;

	public static ImageIcon indexNew;

	public static ImageIcon minIndexNew;

	public static ImageIcon indexReuse;

	public static ImageIcon delete;

	public static ImageIcon minDelete;

	public static ImageIcon refreshAction;

	public static ImageIcon minRefreshAction;

	public static ImageIcon settings;

	public static ImageIcon minSettings;

	public static ImageIcon reconnectAction;

	public static ImageIcon minReconnectAction;

	public static ImageIcon quitAction;

	public static ImageIcon minQuitAction;

	public static ImageIcon key;

	public static ImageIcon minKey;

	public static ImageIcon help;

	public static ImageIcon minHelp;

	public static ImageIcon folderNew;

	public static ImageIcon minFolderNew;

	public static ImageIcon mainWindow;

	public static ImageIcon add;

	public static ImageIcon minAdd;

	public static ImageIcon remove;

	public static ImageIcon minRemove;

	public static ImageIcon terminal;

	public static ImageIcon minTerminal;

	public static ImageIcon queueWatcher;

	public static ImageIcon importExport;

	public static ImageIcon minPeerMonitor;

	public static ImageIcon minImportAction;

	public static ImageIcon minExportAction;

	public static ImageIcon database;

	public static ImageIcon computer;

	public static ImageIcon identity;

	public static ImageIcon peers;

	public static ImageIcon minPeers;

	public static ImageIcon lookAndFeel;

	public static ImageIcon minLookAndFeel;

	public static ImageIcon close;

	public static ImageIcon minClose;

	public static ImageIcon link;

	public static ImageIcon minLink;

	public static ImageIcon file;

	public static ImageIcon minFile;

	public static ImageIcon indexSettings;

	public static ImageIcon minIndexSettings;

	public static ImageIcon addComment;

	public static ImageIcon minAddComment;

	public static ImageIcon readComments;

	public static ImageIcon minReadComments;

	public static ImageIcon minRed;

	public static ImageIcon minOrange;

	public static ImageIcon minGreen;

	public static ImageIcon minDetails;

	public static ImageIcon mDns;

	public static ImageIcon minMDns;

	public static ImageIcon msgReply;

	public static ImageIcon msgNew;

	public static ImageIcon minMsgReply;

	public static ImageIcon minMsgNew;

	public static ImageIcon search;

	public static ImageIcon minSearch;

	public static ImageIcon nextUnread;

	public static ImageIcon minNextUnread;

	public static ImageIcon up;

	public static ImageIcon down;

	public static ImageIcon left;

	public static ImageIcon right;

	public static ImageIcon minUp;

	public static ImageIcon minDown;

	public static ImageIcon minLeft;

	public static ImageIcon minRight;

	public static ImageIcon attachment;

	public static ImageIcon minAttachment;

	public static ImageIcon windowNew;

	public static ImageIcon minWindowNew;

	public static ImageIcon markAsRead;

	public static ImageIcon minMarkAsRead;

	public static ImageIcon mail;

	public static ImageIcon minMail;

	public static ImageIcon web;

	public static ImageIcon miniFrostGmailView;

	public static ImageIcon miniFrostOutlookView;

	public static ImageIcon trust;

	public static ImageIcon minTrust;

	public static ImageIcon minPlugins;

	public static void loadIcons() {
		blueBunny = loadIcon("images/blueBunny.png");
		connectAction = loadIcon("images/connect.png");
		minConnectAction = loadIcon("images/min-connect.png");
		disconnectAction = loadIcon("images/disconnect.png");
		minStop = loadIcon("images/min-stop.png");
		stop = loadIcon("images/stop.png");
		minDisconnectAction = loadIcon("images/min-disconnect.png");
		queue = loadIcon("images/connect.png");
		minQueue = loadIcon("images/min-connect.png");
		insertions = loadIcon("images/insertion.png");
		minInsertions = loadIcon("images/min-insertion.png");
		minIndex = loadIcon("images/min-index.png");
		minIndexReadOnly = loadIcon("images/min-indexReadOnly.png");
		indexNew = loadIcon("images/index-new.png");
		minIndexNew = loadIcon("images/min-index-new.png");
		indexReuse = loadIcon("images/indexReadOnly.png");
		downloads = loadIcon("images/download.png");
		minDownloads = loadIcon("images/min-download.png");
		settings = loadIcon("images/settings.png");
		minSettings = loadIcon("images/min-settings.png");
		indexBrowser = loadIcon("images/index.png");
		minIndexBrowser = loadIcon("images/min-index.png");
		addToIndexAction = loadIcon("images/add.png");
		add = loadIcon("images/add.png");
		minAdd = loadIcon("images/min-add.png");
		insertAndAddToIndexAction = loadIcon("images/index.png");
		makeALinkAction = loadIcon("images/makeLink.png");
		minMakeALinkAction = loadIcon("images/min-makeLink.png");
		reconnectAction = loadIcon("images/refresh.png");
		minReconnectAction = loadIcon("images/min-refresh.png");
		refreshAction = loadIcon("images/refresh.png");
		minRefreshAction = loadIcon("images/min-refresh.png");
		quitAction = loadIcon("images/quit.png");
		minQuitAction = loadIcon("images/min-quit.png");
		key = loadIcon("images/key.png");
		minKey = loadIcon("images/min-key.png");
		delete = loadIcon("images/delete.png");
		minDelete = loadIcon("images/min-delete.png");
		folderNew = loadIcon("images/folder-new.png");
		minFolderNew = loadIcon("images/min-folder-new.png");
		help = loadIcon("images/help.png");
		minHelp = loadIcon("images/min-help.png");
		mainWindow = loadIcon("images/mainWindow.png");
		terminal = loadIcon("images/terminal.png");
		minTerminal = loadIcon("images/min-terminal.png");
		remove = loadIcon("images/remove.png");
		minRemove = loadIcon("images/min-remove.png");
		queueWatcher = loadIcon("images/queueWatcher.png");
		importExport = loadIcon("images/refresh.png");
		minImportAction = loadIcon("images/min-import.png");
		minExportAction = loadIcon("images/min-export.png");
		database = loadIcon("images/database.png");
		minPeerMonitor = loadIcon("images/min-peerMonitor.png");
		computer = loadIcon("images/computer.png");
		identity = loadIcon("images/identity.png");
		peers = loadIcon("images/peers.png");
		minPeers = loadIcon("images/min-peers.png");
		lookAndFeel = loadIcon("images/lookAndFeel.png");
		minLookAndFeel = loadIcon("images/min-lookAndFeel.png");
		close = loadIcon("images/emblem-unreadable.png");
		minClose = loadIcon("images/min-emblem-unreadable.png");
		copy = loadIcon("images/copy.png");
		minCopy = loadIcon("images/min-copy.png");
		file = loadIcon("images/file.png");
		minFile = loadIcon("images/min-file.png");
		link = loadIcon("images/indexBrowser.png");
		minLink = loadIcon("images/min-indexBrowser.png");
		minIndexSettings = loadIcon("images/min-indexSettings.png");
		indexSettings = loadIcon("images/indexSettings.png");
		addComment = loadIcon("images/mail-message-new.png");
		minAddComment = loadIcon("images/min-mail-message-new.png");
		markAsRead = loadIcon("images/mail-message-new.png");
		minMarkAsRead = loadIcon("images/min-mail-message-new.png");
		readComments = loadIcon("images/readComments.png");
		minReadComments = loadIcon("images/min-readComments.png");
		minRed = loadIcon("images/min-red.png");
		minOrange = loadIcon("images/min-orange.png");
		minGreen = loadIcon("images/min-green.png");
		minDetails = loadIcon("images/min-details.png");
		mDns = loadIcon("images/mDns.png");
		minMDns = loadIcon("images/min-mDns.png");
		msgReply = loadIcon("images/mail-reply-sender.png");
		msgNew = loadIcon("images/new-message.png");
		minMsgReply = loadIcon("images/min-mail-reply-sender.png");
		minMsgNew = loadIcon("images/min-new-message.png");
		search = loadIcon("images/mDns.png");
		minSearch = loadIcon("images/min-mDns.png");
		nextUnread = loadIcon("images/mail-forward.png");
		minNextUnread = loadIcon("images/min-mail-forward.png");
		up = loadIcon("images/go-up.png");
		down = loadIcon("images/go-down.png");
		left = loadIcon("images/go-previous.png");
		right = loadIcon("images/go-next.png");
		minUp = loadIcon("images/min-go-up.png");
		minDown = loadIcon("images/min-go-down.png");
		minLeft = loadIcon("images/min-go-previous.png");
		minRight = loadIcon("images/min-go-next.png");
		attachment = loadIcon("images/mail-attachment.png");
		minAttachment = loadIcon("images/min-mail-attachment.png");
		windowNew = loadIcon("images/window-new.png");
		minWindowNew = loadIcon("images/min-window-new.png");
		mail = loadIcon("images/mail.png");
		minMail = loadIcon("images/min-mail.png");
		web = loadIcon("images/web.png");
		miniFrostGmailView = loadIcon("images/miniFrost-view-gmail.png");
		miniFrostOutlookView = loadIcon("images/miniFrost-view-outlook.png");
		trust = loadIcon("images/trust.png");
		minTrust = loadIcon("images/min-trust.png");
		minPlugins = loadIcon("images/min-plugins.png");
	}

	//
	// PRIVATE METHODS
	//

	private static ImageIcon loadIcon(final String fileName) {
		URL url = IconBox.class.getClassLoader().getResource(fileName);
		if (url == null) {
			Logger.error(IconBox.class, "Icon '" + fileName + "' not found ! (Resource)");
			return null;
		}
		return new ImageIcon(url);
	}

}
