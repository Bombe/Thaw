package thaw.fcp;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Observable;

import thaw.core.Logger;

/**
 * This object manages directly the socket attached to the node. After being
 * instanciated, you should commit it to the FCPQueryManager, and then commit
 * the FCPQueryManager to the FCPQueueManager. Call observer when connected /
 * disconnected.<br/> WARNING: This FCP implementation doesn't guarantee that
 * messages are sent in the same order than initally put if the lock on writting
 * is not set !<br/>
 */
public class FCPConnection extends Observable {

	/** If == true, then will print on stdout all fcp input / output. */
	private final static boolean DEBUG_MODE = true;

	private final static int MAX_RECV = 1024;

	/* global to avoid each time free() / malloc() */
	private final byte[] recvBytes = new byte[FCPConnection.MAX_RECV];

	private FCPBufferedStream bufferedOut = null;

	private int maxUploadSpeed = 0;

	private String nodeAddress = null;

	private int port = 0;

	private Socket socket = null;

	private InputStream in = null;

	private OutputStream out = null;

	private BufferedInputStream reader = null;

	private long rawBytesWaiting = 0;

	private int writersWaiting;

	private final Object monitor;

	private boolean duplicationAllowed = true;

	private boolean localSocket = false;

	private boolean autoDownload = true;

	private FCPClientHello clientHello;

	/**
	 * Don't connect. Call connect() for that.
	 *
	 * @param maxUploadSpeed
	 * 		in KB: -1 means no limit
	 * @param duplicationAllowed
	 * 		FCPClientGet and FCPClientPut will be allowed to open a separate socket to
	 * 		transfer the files
	 * @param autoDownload
	 * 		If !localSocket and if autoDownload, then files are automatically
	 * 		downloaded when the transfer ends
	 */
	public FCPConnection(final String nodeAddress,
						 final int port,
						 int maxUploadSpeed,
						 boolean duplicationAllowed,
						 final boolean localSocket,
						 final boolean autoDownload) {
		if (localSocket)
			duplicationAllowed = false;

		if (FCPConnection.DEBUG_MODE) {
			Logger.notice(this, "DEBUG_MODE ACTIVATED");
		}

		monitor = new Object();

		maxUploadSpeed = -1;

		setNodeAddress(nodeAddress);
		setNodePort(port);
		setMaxUploadSpeed(maxUploadSpeed);
		setDuplicationAllowed(duplicationAllowed);
		setLocalSocket(localSocket);
		setAutoDownload(autoDownload);

		writersWaiting = 0;
	}

	public void setNodeAddress(final String nodeAddress) {
		this.nodeAddress = nodeAddress;
	}

	public void setNodePort(final int port) {
		this.port = port;
	}

	public void setMaxUploadSpeed(final int max) {
		maxUploadSpeed = max;
	}

	public void setDuplicationAllowed(final boolean allowed) {
		duplicationAllowed = allowed;
	}

	public void setLocalSocket(final boolean local) {
		localSocket = local;
	}

	public void setAutoDownload(final boolean autoDownload) {
		this.autoDownload = autoDownload;
	}

	public boolean getAutoDownload() {
		return autoDownload;
	}

	public boolean isLocalSocket() {
		return localSocket;
	}

	public void disconnect() {
		try {
			if (isConnected())
				socket.close();
			else {
				Logger.info(this, "Disconnect(): Already disconnected.");
			}
			synchronized (monitor) {
				monitor.notifyAll();
			}
		} catch (final IOException e) {
			Logger.warning(this, "Unable to close cleanly the connection : "
					+ e.toString() + " ; " + e.getMessage());
		}

		socket = null;
		in = null;
		out = null;
		if (bufferedOut != null) {
			bufferedOut.stopSender();
			bufferedOut = null;
		}

		setChanged();
		this.notifyObservers();
	}

	/**
	 * If already connected, disconnect before connecting.
	 *
	 * @return true if successful
	 */
	public boolean connect() {
		if ((nodeAddress == null) || (port == 0)) {
			Logger.warning(this, "Address or port not defined ! Unable to connect");
			return false;
		}

		Logger.info(this, "Connection to " + nodeAddress + ":" + Integer.toString(port) + "...");

		if ((socket != null) && !socket.isClosed())
			disconnect();

		socket = openSocket(nodeAddress, port);
		if (socket == null) {
			return false;
		}

		if (!socket.isConnected()) {
			Logger.warning(this, "Unable to connect, but no exception ?!");
			Logger.warning(this, "Will try to continue ...");
		}

		try {
			in = socket.getInputStream();
			out = socket.getOutputStream();
		} catch (final IOException e) {
			Logger.error(this, "Socket and connection established, but unable to get " +
					"in/output streams ?! : " + e.toString() + " ; " + e.getMessage());
			return false;
		}

		reader = new BufferedInputStream(in);
		bufferedOut = new FCPBufferedStream(this, maxUploadSpeed);
		bufferedOut.startSender();

		rawBytesWaiting = 0;
		writersWaiting = 0;

		Logger.info(this, "Connected");

		setChanged();
		this.notifyObservers();

		return true;
	}

	protected Socket openSocket(String nodeAddress, int port) {
		Socket socket;
		try {
			socket = new Socket(nodeAddress, port);

		} catch (final UnknownHostException e) {
			Logger.error(this, "Error while trying to connect to " + nodeAddress + ":" + port + " : " +
					e.toString());
			socket = null;
		} catch (final IOException e) {
			Logger.error(this, "Error while trying to connect to " + nodeAddress + ":" + port + " : " +
					e.toString() + " ; " + e.getMessage());
			socket = null;
		}

		return socket;
	}

	public boolean isOutputBufferEmpty() {
		return bufferedOut.isOutputBufferEmpty();
	}

	public boolean isConnected() {
		if (socket == null)
			return false;
		else
			return socket.isConnected();
	}

	/** Doesn't check the lock state ! You have to manage it yourself. */
	public synchronized boolean rawWrite(final byte[] data) {
		if (bufferedOut != null)
			return bufferedOut.write(data);
		else {
			Logger.notice(this, "rawWrite(), bufferedOut == null ? Socket closed ?");
			disconnect();
			return false;
		}
	}

	/** Should be call by FCPBufferedStream. */
	protected synchronized boolean realRawWrite(final byte[] data) {
		if ((out != null) && (socket != null) && socket.isConnected()) {
			try {
				out.write(data);
				out.flush();
			} catch (final IOException e) {
				Logger.warning(this, "Unable to write() on the socket ?! : "
						+ e.toString() + " ; " + e.getMessage());
				disconnect();
				return false;
			}
		} else {
			Logger.notice(this, "Cannot write if disconnected !");
			if (out == null)
				Logger.notice(this, "^ no output stream         ^");
			if (socket == null)
				Logger.notice(this, "^ no socket                ^");
			else if (!socket.isConnected())
				Logger.notice(this, "^ socket but not connected ^");
			return false;
		}

		return true;
	}

	public void addToWriterQueue() {
		synchronized (monitor) {
			writersWaiting++;

			if (writersWaiting > 1) {
				try {
					monitor.wait();
				} catch (final InterruptedException e) {
					Logger.warning(this, "Interrupted while waiting ?!");
				}
			}

			return;
		}
	}

	public void removeFromWriterQueue() {
		synchronized (monitor) {
			writersWaiting--;
			if (writersWaiting < 0) {
				Logger.warning(this, "Negative number of writers ?!");
				writersWaiting = 0;
			}
			monitor.notify();
		}
	}

	public boolean isWriting() {
		if (!isConnected()) {
			return false;
		}

		return (writersWaiting > 0);
	}

	public boolean write(final String toWrite) {
		return this.write(toWrite, true);
	}

	public boolean write(final String toWrite, final boolean checkLock) {

		if (checkLock) {
			addToWriterQueue();
		}

		if (DEBUG_MODE) {
			Logger.debug(this, "Thaw >>> Node :");
			Logger.debug(this, toWrite);
		}

		if ((out != null) && (socket != null) && socket.isConnected()) {
			try {
				bufferedOut.write(toWrite.getBytes("UTF-8"));
			} catch (final UnsupportedEncodingException e) {
				Logger.error(this, "UNSUPPORTED ENCODING EXCEPTION : UTF-8");
				bufferedOut.write(toWrite.getBytes());
			}
		} else {
			Logger.notice(this, "Cannot write if disconnected !");
			if (out == null)
				Logger.notice(this, "^ no output stream         ^");
			if (socket == null)
				Logger.notice(this, "^ no socket                ^");
			else if (!socket.isConnected())
				Logger.notice(this, "^ socket but not connected ^");
			if (checkLock)
				removeFromWriterQueue();
			return false;
		}

		if (checkLock)
			removeFromWriterQueue();
		return true;
	}

	/**
	 * For security : FCPQueryManager uses this function to tells FCPConnection how
	 * many raw bytes are waited (to avoid to *serious* problems).
	 */
	public void setRawDataWaiting(final long waiting) {
		rawBytesWaiting = waiting;
	}

	/** @return -1 Disconnection. */
	public int read(final byte[] buf) {
		int rdBytes = 0;

		try {
			rdBytes = reader.read(buf);

			if (rdBytes < 0) {
				Logger.error(this, "Error while reading on the socket => disconnection");
				disconnect();
				return rdBytes;
			}

			rawBytesWaiting = rawBytesWaiting - rdBytes;

			//Logger.verbose(this, "Remaining: "+rawBytesWaiting);

			return rdBytes;
		} catch (final IOException e) {
			Logger.error(this, "IOException while reading raw bytes on socket => disconnection:");
			Logger.error(this, "   =========");
			Logger.error(this, e.getMessage() + ":");
			if (e.getCause() != null)
				Logger.error(this, e.getCause().toString());
			Logger.error(this, e.getMessage());
			Logger.error(this, "   =========");

			disconnect();
			return -2; /* -1 can mean eof */
		}

	}

	/**
	 * Read a line.
	 *
	 * @return null if disconnected or error
	 */
	public String readLine() {

		/* SECURITY */
		if (rawBytesWaiting > 0) {
			Logger.warning(this, "RAW BYTES STILL WAITING ON SOCKET. THIS IS ABNORMAL. -> Will drop them.");

			while (rawBytesWaiting > 0) {
				int to_read = 1024;

				if (to_read > rawBytesWaiting)
					to_read = (int) rawBytesWaiting;

				final byte[] read = new byte[to_read];
				this.read(read); /* TODO(Jflesch): Use BufferInputStream.skip() */

				rawBytesWaiting = rawBytesWaiting - to_read;
			}
		}

		String result;

		if ((in != null) && (reader != null) && (socket != null) && socket.isConnected()) {
			try {
				for (int i = 0; i < recvBytes.length; i++)
					recvBytes[i] = 0;

				result = "";

				int c = 0;
				int i = 0; /* position in recvBytes */

				while ((c != '\n') && (i < recvBytes.length)) {
					c = reader.read();

					if (c == -1) {
						if (isConnected())
							Logger.warning(this, "Unable to read but still connected");
						else
							Logger.notice(this, "Disconnected");

						disconnect(); /* will warn everybody */

						return null;
					}

					if (c == '\n')
						break;

					//result = result + new String(new byte[] { (byte)c });

					recvBytes[i] = (byte) c;
					i++;
				}

				result = new String(recvBytes, 0, i, "UTF-8");

				if (FCPConnection.DEBUG_MODE) {
					if (result.matches("[\\-\\ \\?.a-zA-Z0-9\\,~%@/_=\\[\\]\\(\\)]*"))
						Logger.debug(this, "Thaw <<< Node : " + result);
					else
						Logger.debug(this, "Thaw <<< Node : Unknow chars in message. Not displayed");
				}

				return result;

			} catch (final IOException e) {
				if (isConnected())
					Logger.notice(this, "IOException while reading but still connected ?! : "
							+ e.toString() + " ; " + e.getMessage());
				else
					Logger.notice(this, "IOException. Disconnected. : " + e.toString() + " ; "
							+ e.getMessage());

				disconnect();

				return null;
			}
		} else {
			Logger.notice(this, "Cannot read if disconnected => null");
		}

		return null;
	}

	/**
	 * If duplicationAllowed, returns a copy of this object, using a different
	 * socket and differents lock / buffer. If !duplicationAllowed, returns this
	 * object. The duplicate socket is just connected but not initialized
	 * (ClientHello, etc).
	 */
	public FCPConnection duplicate() {
		if (!duplicationAllowed)
			return this;

		Logger.info(this, "Duplicating connection to the node ...");

		FCPConnection newConnection;

		/* upload limit is useless here, since we can't do a global limit
		 * on all the connections */
		newConnection = new FCPConnection(nodeAddress, port, -1,
				duplicationAllowed, localSocket,
				autoDownload);

		if (!newConnection.connect()) {
			Logger.warning(this, "Unable to duplicate socket !");
			return this;
		}

		return newConnection;
	}

	public void registerClientHello(FCPClientHello ch) {
		clientHello = ch;
	}

	public FCPClientHello getClientHello() {
		return clientHello;
	}
}
