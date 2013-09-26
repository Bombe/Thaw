package thaw.plugins.miniFrost.frostKSK;

import java.io.File;
import java.util.Date;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import thaw.core.I18n;
import thaw.core.Logger;
import thaw.core.ThawRunnable;
import thaw.core.ThawThread;
import thaw.fcp.FCPClientPut;
import thaw.fcp.FCPQueueManager;
import thaw.gui.SysTrayIcon;
import thaw.plugins.TrayIcon;
import thaw.plugins.index.Index;
import thaw.plugins.miniFrost.interfaces.Attachment;
import thaw.plugins.miniFrost.interfaces.Board;
import thaw.plugins.miniFrost.interfaces.Draft;
import thaw.plugins.signatures.Identity;

public class KSKDraft
		implements Draft, Observer {

	private KSKMessage inReplyTo = null;

	private KSKBoard board = null;

	private String subject = null;

	private String txt = null;

	private String nick = null;

	private Identity identity = null;

	private Identity recipient = null;

	private Date date = null;

	private int idLinePos = 0;

	private int idLineLen = 0;

	private Vector attachments;

	public KSKDraft(KSKBoard board, KSKMessage inReplyTo) {
		this.board = board;
		this.inReplyTo = inReplyTo;
		attachments = null;

		if (inReplyTo != null
				&& inReplyTo.encryptedFor() != null
				&& inReplyTo.getSender() != null
				&& inReplyTo.getSender().getIdentity() != null) {
			recipient = inReplyTo.getSender().getIdentity();
		}
	}

	public String getSubject() {
		if (subject != null)
			return subject;

		if (inReplyTo != null) {
			String subject = inReplyTo.getSubject();
			if (subject.indexOf("Re: ") == 0)
				return subject;
			return "Re: " + subject;
		}

		return "";
	}

	public String getText() {
		if (txt != null)
			return txt;

		String txt = "";

		if (inReplyTo != null) {
			txt = inReplyTo.getRawMessage();
			if (txt == null)
				txt = "";
			else
				txt = (txt.trim() + "\n\n");
		}

		txt += "----- $sender$ ----- $dateAndTime$GMT -----\n\n";

		return txt;
	}

	public boolean allowUnsignedPost() {
		return true;
	}

	public void setSubject(String txt) {
		subject = txt;
	}

	public void setText(String txt) {
		this.txt = txt;
	}

	/**
	 * @param identity
	 * 		if null, unsigned post
	 */
	public void setAuthor(String nick, Identity identity) {
		this.nick = nick;
		this.identity = identity;
	}

	public Identity getAuthorIdentity() {
		return identity;
	}

	public String getAuthorNick() {
		return nick;
	}

	public void setRecipient(Identity id) {
		this.recipient = id;
	}

	public Identity getRecipient() {
		return recipient;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public Date getDate() {
		return date;
	}

	private File fileToInsert;

	private FCPQueueManager queueManager;

	private int revUsed;

	private boolean waiting;

	private boolean posting;

	public void notifyPlugin() {
		board.getFactory().getPlugin().getPanel().update(this);
	}

	public boolean isWaiting() {
		return waiting;
	}

	public boolean isPosting() {
		return posting;
	}

	public void setIdLinePos(int i) {
		idLinePos = i;
	}

	public void setIdLineLen(int i) {
		idLineLen = i;
	}

	private boolean initialInsertion = false;

	public void post(FCPQueueManager queueManager) {
		this.queueManager = queueManager;

		initialInsertion = false;

		waiting = true;
		posting = false;
		notifyPlugin();

		/* we start immediatly a board refresh (we will need it) */
		synchronized (board) {
			board.addObserver(this);
			board.refresh(2 /* until yesterday ; just to be sure because of the GMT conversion etc */);
		}

		/* first check */
		update(board, null);
	}

	private class InsertionStarter implements ThawRunnable {

		private boolean forceStop;

		public InsertionStarter() {
			forceStop = false;
		}

		public void run() {
			boolean ready = false;

			while (!ready && attachments != null && !forceStop) {
				ready = true;

				for (Iterator it = attachments.iterator();
					 it.hasNext(); ) {
					KSKAttachment a = (KSKAttachment) it.next();
					if (!a.isReady())
						ready = false;
				}

				if (!ready) {
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						/* \_o< */
					}
				}
			}

			if (!forceStop)
				startInsertion();
		}

		public void stop() {
			forceStop = true;
		}
	}

	private void startInsertion() {

		/* we generate first the XML message */
		KSKMessageParser generator = new KSKMessageParser(board.getFactory().getDb(),
				((inReplyTo != null) ? inReplyTo.getMsgId() : null),
				nick,
				subject,
				date,
				recipient, /* recipient */
				board.getName(),
				txt,
				((identity != null) ? identity.getPublicKey() : null),
				attachments,
				identity,
				idLinePos,
				idLineLen,
				board.getFactory().getWoT().getTrustListPublicKeyFor(identity));

		fileToInsert = generator.generateXML(board.getFactory().getDb() /* gruick */);

		waiting = false;
		posting = true;
		notifyPlugin();

		String privateKey = board.getPrivateKey();
		String name = board.getNameForInsertion(date, revUsed);
		int keyType = board.getKeyType();

		if (keyType == FCPClientPut.KEY_TYPE_KSK)
			Logger.info(this, "Inserting : KSK@" + name);
		else
			Logger.info(this, "Insertion : SSK@" + privateKey + name);

		FCPClientPut clientPut = new FCPClientPut.Builder(queueManager)
				.setLocalFile(fileToInsert)
				.setKeyType(keyType)
				.setRev(-1) /* rev : we specify it ourselves in the key name */
				.setName(name)
				.setPrivateKey(privateKey)
				.setPriority(2)
				.setGlobal(false)
				.setPersistence(FCPClientPut.PERSISTENCE_FOREVER)
				.setCompress(true)
				.build();
		clientPut.addObserver(this);
		queueManager.addQueryToTheRunningQueue(clientPut);
	}

	private boolean isBoardUpToDateForToday() {
		if (!board.isRefreshing()
				|| (board.getCurrentlyRefreshedDate() != null
				&& (KSKBoard.getMidnight(board.getCurrentlyRefreshedDate()).getTime()
				< KSKBoard.getMidnight(date).getTime()))) {
			//Logger.info(this, "Board: "+Long.toString(KSKBoard.getMidnight(board.getCurrentlyRefreshedDate()).getTime()));
			Logger.info(this, "Draft: " + KSKBoard.getMidnight(date).getTime());
			return true;
		}
		return false;
	}

	public void update(Observable o, Object param) {
		if (o instanceof Board) {
			synchronized (board) {
				/* just to be sure we don't insert the message many times */
				if (initialInsertion)
					return;

				if (!isBoardUpToDateForToday())
					return;

				initialInsertion = true;

				board.deleteObserver(this);
				revUsed = board.getNextNonDownloadedAndValidRev(date, -1);

				Thread th = new Thread(new ThawThread(new InsertionStarter(), "Frost message insertion starter"));
				th.start();
			}
		}

		if (o instanceof FCPClientPut) {
			FCPClientPut put = (FCPClientPut) o;

			if (put.isFinished() && put.isSuccessful()) {
				posting = false;
				waiting = false;
				notifyPlugin();

				put.deleteObserver(this);
				put.stop();
				queueManager.remove(put);

				fileToInsert.delete();

				Logger.info(this, "Message sent.");

				String announce = I18n.getMessage("thaw.plugin.miniFrost.messageSent");
				announce = announce.replaceAll("X", board.toString());

				TrayIcon.popMessage(board.getFactory().getCore().getPluginManager(),
                    "MiniFrost",
                    announce);

			} else if (put.isFinished() && !put.isSuccessful()) {
				if (put.getPutFailedCode() != 9) { /* !Collision */
					put.deleteObserver(this);

					if (put.getPutFailedCode() < 0)
						Logger.warning(this, "message insertion on the board '" +
								board.toString() + "' cancelled");
					else
						Logger.error(this, "Can't insert the message on the board '" +
								board.toString() + "' ; Code: " + Integer.toString(put.getPutFailedCode()));
					waiting = false;
					posting = false;
					notifyPlugin();
					return;
				}

				String announce = I18n.getMessage("thaw.plugin.miniFrost.collision");
				announce = announce.replaceAll("X", board.toString());

				TrayIcon.popMessage(board.getFactory().getCore().getPluginManager(),
						"MiniFrost",
						announce,
						SysTrayIcon.MSG_WARNING);

				put.deleteObserver(this);
				put.stop();
				queueManager.remove(put);

				//revUsed = board.getNextNonDownloadedRev(date, revUsed);
				revUsed = board.getNextNonDownloadedAndValidRev(date, revUsed);
				startInsertion();
			}
		}

	}

	public Board getBoard() {
		return board;
	}

	public Vector getAttachments() {
		return attachments;
	}

	public boolean addAttachment(File file) {
		return addAttachment(new KSKFileAttachment(board.getFactory().getCore().getQueueManager(),
				file));
	}

	public boolean addAttachment(Board board) {
		return addAttachment(new KSKBoardAttachment(board));
	}

	public boolean addAttachment(Index index) {
		return false;
	}

	protected boolean addAttachment(KSKAttachment attachment) {
		if (attachments == null)
			attachments = new Vector();
		attachments.add(attachment);

		return true;
	}

	public boolean removeAttachment(Attachment attachment) {
		if (attachments == null)
			return false;

		attachments.remove(attachment);

		return false;
	}
}
