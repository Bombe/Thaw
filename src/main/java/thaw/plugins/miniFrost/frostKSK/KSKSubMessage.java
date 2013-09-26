package thaw.plugins.miniFrost.frostKSK;

import java.util.Date;

import thaw.plugins.miniFrost.interfaces.Author;
import thaw.plugins.miniFrost.interfaces.SubMessage;

public class KSKSubMessage
		implements SubMessage {

	private KSKAuthor author;

	private Date date;

	private String msg;

	public KSKSubMessage(KSKAuthor author,
						 Date date,
						 String msg) {
		this.author = author;
		this.date = date;
		this.msg = msg;
	}

	public Author getAuthor() {
		return author;
	}

	protected void setAuthor(KSKAuthor author) {
		this.author = author;
	}

	protected void setDate(Date date) {
		this.date = date;
	}

	public Date getDate() {
		return date;
	}

	public String getMessage() {
		return msg;
	}
}
