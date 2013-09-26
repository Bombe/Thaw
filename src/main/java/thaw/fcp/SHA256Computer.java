package thaw.fcp;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Observable;

import freenet.crypt.SHA256;
import freenet.support.Base64;

import thaw.core.Logger;
import thaw.core.ThawRunnable;

/** Automatically used by FCPClientPut. You shouldn't have to bother about it */
public class SHA256Computer extends Observable implements ThawRunnable {

	private MessageDigest md;

	private String hash;

	private final Object hashLock = new Object();

	private final String file;

	private final String headers;

	private short progress = 0;

	private boolean isFinished = false;

	public final static int BLOCK_SIZE = 32768; /* 32 Ko */

	public boolean running = true;

	public SHA256Computer(String header, String fileToHash) {
		this.file = fileToHash;
		this.headers = header;
		this.running = true;
	}

	public void run() {
		File realFile = new File(file);
		long realFileSize = realFile.length();

		try {
			FileInputStream in = new FileInputStream(realFile);
			BufferedInputStream bis = new BufferedInputStream(in);
			md = SHA256.getMessageDigest();
			md.reset();
			md.update(headers.getBytes("UTF-8"));

			byte[] buf = new byte[4096];
			int readBytes = bis.read(buf);
			while (readBytes > -1 && running) {
				md.update(buf, 0, readBytes);
				readBytes = bis.read(buf);
				progress = (short) Math.round(readBytes * 100 / realFileSize);
				setChanged();
				notifyObservers();
			}

			bis.close();
			in.close();

			if (!running) {
				setChanged();
				notifyObservers();
				return;
			}

			synchronized (hashLock) {
				hash = Base64.encode(md.digest());
			}
			isFinished = true;
			SHA256.returnMessageDigest(md);

		} catch (FileNotFoundException e) {
			Logger.error(this, "Can't hash file because: " + e.toString());
		} catch (IOException e) {
			Logger.error(this, "Can't hash file because: " + e.toString());
		}

		setChanged();
		notifyObservers();
	}

	public void stop() {
		running = false;
	}

	/** In % */
	public int getProgression() {
		if (isFinished)
			return 100;
		else if (progress > 99)
			return 99;
		else
			return progress;
	}

	/** Returns the Base64Encode of the hash */
	public String getHash() {
		synchronized (hashLock) {
			return hash;
		}
	}

	public boolean isFinished() {
		return isFinished;
	}
}
