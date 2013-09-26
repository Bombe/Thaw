package thaw.fcp;

import java.io.UnsupportedEncodingException;

import thaw.core.Logger;
import thaw.core.ThawRunnable;
import thaw.core.ThawThread;

/**
 * Only used by FCPConnection. Except special situation, you shouldn't have to
 * use it directly. Currently only used for output. (shouldn't be really usefull
 * for input). Some data are sent each 'INTERVAL' (in ms).
 */
public class FCPBufferedStream implements ThawRunnable {

	private final FCPConnection connection;

	private int maxUploadSpeed;

	private byte outputBuffer[];

	public final static int OUTPUT_BUFFER_SIZE = 102400;

	public final static int INTERVAL = 200;

	private int waiting = 0; /* amount of data stored in the buffer */

	private int readCursor = 0; /* indicates where the nex read will be */

	private int writeCursor = 0; /* indicates where the next write will be */

	private Thread tractopelle = null;

	private boolean running = true;

	private int packetSize = 0;

	public FCPBufferedStream(final FCPConnection connection,
							 final int maxUploadSpeed) {
		this.connection = connection;
		this.maxUploadSpeed = maxUploadSpeed;

		if (maxUploadSpeed >= 0) {
			outputBuffer = new byte[FCPBufferedStream.OUTPUT_BUFFER_SIZE];
			packetSize = (maxUploadSpeed * 1024) / (1000 / FCPBufferedStream.INTERVAL);
		}
	}

	/**
	 * Add to the buffer. Can block if buffer is full ! Never send more than
	 * OUTPUT_BUFFER_SIZE.
	 */
	public synchronized boolean write(final byte[] data) {
		if (maxUploadSpeed == -1)
			return connection.realRawWrite(data);

		while (waiting + data.length > FCPBufferedStream.OUTPUT_BUFFER_SIZE) {
			sleep(FCPBufferedStream.INTERVAL);
		}

		waiting += data.length;

		for (int i = 0; i < data.length; i++) {
			outputBuffer[writeCursor] = data[i];

			writeCursor++;

			if (writeCursor >= FCPBufferedStream.OUTPUT_BUFFER_SIZE)
				writeCursor = 0;
		}

		return true;
	}

	/** @see #write(byte[]) */
	public boolean write(final String data) {
		try {
			return this.write(data.getBytes("UTF-8"));
		} catch (final UnsupportedEncodingException e) {
			Logger.error(this, "UNSUPPORTED ENCODING EXCEPTION : UTF-8");
			return this.write(data.getBytes());
		}
	}

	/** extract from the buffer */
	private boolean readOutputBuffer(final byte[] data) {
		for (int i = 0; i < data.length; i++) {
			data[i] = outputBuffer[readCursor];

			readCursor++;

			if (readCursor >= FCPBufferedStream.OUTPUT_BUFFER_SIZE)
				readCursor = 0;
		}

		waiting -= data.length;

		return true;
	}

	/** wait for the buffer being empty. */
	public void flush() {
		while (waiting > 0) {
			sleep(FCPBufferedStream.INTERVAL);
		}
	}

	public void run() {
		byte[] data;

		while (running) { /* Wild and freeeeeee */
			if (waiting > 0) {
				int to_read = packetSize;

				if (waiting < to_read)
					to_read = waiting;

				data = new byte[to_read];

				readOutputBuffer(data);

				connection.realRawWrite(data);
			}

			sleep(FCPBufferedStream.INTERVAL);
		}
	}

	public void stop() {
		running = false;
	}

	/** Start the thread sending data from the buffer to the OutputStream (socket). */
	public boolean startSender() {
		running = true;

		if (maxUploadSpeed < 0) {
			Logger.notice(this, "startSender(): No upload limit. Not needed");
			return false;
		}

		if (tractopelle == null) {
			tractopelle = new Thread(new ThawThread(this, "Upload limiter", this));
			tractopelle.start();
			return true;
		} else {
			Logger.notice(this, "startSender(): Already started");
			return false;
		}
	}

	public boolean stopSender() {
		running = false;
		tractopelle = null;
		return true;
	}

	public boolean isOutputBufferEmpty() {
		return (waiting == 0);
	}

	public boolean isOutputBufferFull() {
		return ((maxUploadSpeed < 0) || (waiting >= (FCPBufferedStream.OUTPUT_BUFFER_SIZE - 1)));
	}

	/** Just ignore the InterruptedException. */
	private void sleep(final int ms) {
		try {
			Thread.sleep(ms);
		} catch (final InterruptedException e) {
			/* just iggnnnnnooored */
		}
	}
}
