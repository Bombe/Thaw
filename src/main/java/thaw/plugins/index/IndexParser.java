package thaw.plugins.index;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import thaw.core.Logger;
import thaw.fcp.FreenetURIHelper;
import thaw.plugins.insertPlugin.DefaultMIMETypes;

/* DOM */
/* SAX */

public class IndexParser {

	public final static String DATE_FORMAT = "yyyyMMdd";

	private IndexContainer index;

	public IndexParser(IndexContainer index) {
		this.index = index;
	}

	public boolean generateXML(String path) {
		try {
			FileOutputStream stream = new FileOutputStream(new File(path));
			generateXML(stream);
			stream.close();
			return true;
		} catch (FileNotFoundException e) {
			Logger.error(this, "File not found exception ?!");
		} catch (IOException e) {
			Logger.error(this, "IOException while generating the index: " + e.toString());
		}
		return false;
	}

	public void generateXML(final OutputStream out) {
		StreamResult streamResult;

		streamResult = new StreamResult(out);

		Document xmlDoc;

		final DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder xmlBuilder;

		try {
			xmlBuilder = xmlFactory.newDocumentBuilder();
		} catch (final ParserConfigurationException e) {
			Logger.error(this, "Unable to generate the index because : " + e.toString());
			return;
		}

		final DOMImplementation impl = xmlBuilder.getDOMImplementation();

		xmlDoc = impl.createDocument(null, "index", null);

		final Element rootEl = xmlDoc.getDocumentElement();

		/**** DOM Tree generation ****/
		fillInRootElement(rootEl, xmlDoc);


		/* Serialization */
		final DOMSource domSource = new DOMSource(xmlDoc);
		final TransformerFactory transformFactory = TransformerFactory.newInstance();

		Transformer serializer;

		try {
			serializer = transformFactory.newTransformer();
		} catch (final TransformerConfigurationException e) {
			Logger.error(this, "Unable to save index because: " + e.toString());
			return;
		}

		serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		serializer.setOutputProperty(OutputKeys.INDENT, "yes");

		/* final step */
		try {
			serializer.transform(domSource, streamResult);
		} catch (final TransformerException e) {
			Logger.error(this, "Unable to save index because: " + e.toString());
			return;
		}
	}

	public boolean fillInRootElement(Element rootEl, Document xmlDoc) {
		rootEl.appendChild(getXMLHeader(xmlDoc));
		rootEl.appendChild(getXMLLinks(xmlDoc));
		rootEl.appendChild(getXMLFileList(xmlDoc));

		if (index.canHaveComments())
			rootEl.appendChild(getXMLCommentInfos(xmlDoc));

		return true;
	}

	private Element getXMLHeader(final Document xmlDoc) {
		final Element header = xmlDoc.createElement("header");

		final Element title = xmlDoc.createElement("title");
		final Text titleText = xmlDoc.createTextNode(index.toString(false));
		title.appendChild(titleText);

		header.appendChild(title);

		final Element clientVersion = xmlDoc.createElement("client");
		final Text versionText = xmlDoc.createTextNode(index.getClientVersion());
		clientVersion.appendChild(versionText);

		header.appendChild(clientVersion);

		if (index.publishPrivateKey() && index.getPrivateKey() != null) {
			final Element privateKeyEl = xmlDoc.createElement("privateKey");
			final Text privateKeyText = xmlDoc.createTextNode(index.getPrivateKey());
			privateKeyEl.appendChild(privateKeyText);

			header.appendChild(privateKeyEl);
		}

		/* insertion date */
		String dateStr;

		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		dateStr = sdf.format(new Date());

		final Element date = xmlDoc.createElement("date");
		final Text dateText = xmlDoc.createTextNode(dateStr);
		date.appendChild(dateText);

		header.appendChild(date);

		/* category */

		String cat = index.getCategory();

		if (cat != null) {
			Element category = xmlDoc.createElement("category");
			Text categoryText = xmlDoc.createTextNode(cat);
			category.appendChild(categoryText);
			header.appendChild(category);
		}

		/* TODO : Author */

		return header;
	}

	private Element getXMLLinks(final Document xmlDoc) {
		final Element linksEl = xmlDoc.createElement("indexes");

		LinkContainer[] links = index.getLinkList();

		for (int i = 0; i < links.length; i++) {
			LinkContainer link = links[i];

			String key = index.findTheLatestKey(link.getPublicKey());
			String cat = link.getCategory();

			final Element xmlLink = xmlDoc.createElement("link");

			xmlLink.setAttribute("key", key);

			if (cat != null)
				xmlLink.setAttribute("category", cat);

			linksEl.appendChild(xmlLink);
		}

		return linksEl;
	}

	private Element getXMLFileList(final Document xmlDoc) {
		final Element filesEl = xmlDoc.createElement("files");

		List<thaw.plugins.index.File> files = index.getFileList();

		for (FileContainer file : files) {

			String pubKey = file.getPublicKey();

			if (pubKey == null)
				continue;

			pubKey = pubKey.trim();

			if (!FreenetURIHelper.isAKey(pubKey)) {
				Logger.notice(this, "One of the file key wasn't generated => not added");
				continue;
			}

			final Element xmlFile = xmlDoc.createElement("file");

			//xmlFile.setAttribute("id", set.getString("id"));
			xmlFile.setAttribute("key", pubKey);
			xmlFile.setAttribute("size", Long.toString(file.getSize()));
			if (file.getMime() == null)
				xmlFile.setAttribute("mime", DefaultMIMETypes.guessMIMEType(file.getFilename()));
			else
				xmlFile.setAttribute("mime", file.getMime());

			filesEl.appendChild(xmlFile);
		}

		return filesEl;
	}

	private Element getXMLCommentInfos(final Document xmlDoc) {
		final Element infos = xmlDoc.createElement("comments");

		infos.setAttribute("publicKey", index.getCommentPublicKey());

		if (index.getCommentPrivateKey() != null)
			infos.setAttribute("privateKey", index.getCommentPrivateKey());

		for (Iterator it = index.getCommentBlacklistedRev().iterator();
			 it.hasNext(); ) {

			Integer rev = (Integer) it.next();
			Element bl = xmlDoc.createElement("blackListed");
			bl.setAttribute("rev", rev.toString());
			infos.appendChild(bl);

		}

		return infos;
	}

	/** ************************ INDEX LOADING ****************************** */

	public void loadXML(final String filePath) {
		loadXML(filePath, true);
	}

	/**
	 * @param clean
	 * 		if set to false, will do a merge (won't call purgeIndex())
	 */
	public void loadXML(final String filePath, boolean clean) {
		try {
			FileInputStream stream = new FileInputStream(filePath);
			loadXML(stream, clean);
			stream.close();
			stream = null;
		} catch (final FileNotFoundException e) {
			Logger.error(this, "Unable to load XML: FileNotFoundException ('" + filePath + "') ! : " + e.toString());
		} catch (IOException e) {
			Logger.error(this, "IOException while parsing the index: " + e.toString());
		}
	}

	protected class IndexHandler extends DefaultHandler {

		private boolean clean = true;

		public IndexHandler() {
			this(true);
		}

		public IndexHandler(boolean clean) {
			this.clean = clean;
		}

		/**
		 * Called when parsing is started
		 *
		 * @see ContentHandler#startDocument()
		 */
		public void startDocument() throws SAXException {
			if (clean)
				index.purgeIndex();
		}

		private boolean ownerTag = false;

		private boolean privateKeyTag = false;

		private boolean dateTag = false;

		private boolean categoryTag = false;

		private boolean hasCommentTag = false;

		private boolean clientTag = false;

		private String dateStr = null;

		private String categoryStr = null;

		/**
		 * Called when the parsed find an opening tag
		 *
		 * @param localName
		 * 		local tag name
		 * @param rawName
		 * 		rawName (the one used here)
		 * @see ContentHandler#startElement(String, String, String, Attributes)
		 */
		public void startElement(String nameSpaceURI, String localName,
								 String rawName, Attributes attrs) throws SAXException {
			if (rawName == null) {
				rawName = localName;
			}

			if (rawName == null)
				return;

			ownerTag = false;
			privateKeyTag = false;

			/* TODO : <title></title> */

			if ("owner".equals(rawName)) {
				ownerTag = true;
				return;
			} else if ("privateKey".equals(rawName)) {
				privateKeyTag = true;
				return;
			} else if ("date".equals(rawName)) {
				dateTag = true;
				return;
			} else if ("category".equals(rawName)) {
				categoryTag = true;
				return;
			} else if ("link".equals(rawName)
					|| "index".equals(rawName)) { /* links */

				if (!index.addLink(attrs.getValue("key"), attrs.getValue("category"))) {
					throw new SAXException("Index parsing interrupted because of a backend error");
				}

				return;
			} else if ("file".equals(rawName)) {

				if (!index.addFile(attrs.getValue("key"),
						Long.parseLong(attrs.getValue("size")),
						attrs.getValue("mime"))) {
					throw new SAXException("Index parsing interrupted because of a backend error");
				}

			} else if ("comments".equals(rawName)) {
				String pub = attrs.getValue("publicKey");
				String priv = attrs.getValue("privateKey");

				if (pub != null) {
					hasCommentTag = true;
					Logger.debug(this, "Comment allowed in this index");
					index.setCommentKeys(pub, priv);
				}

			} else if ("client".equals(rawName)) {

				clientTag = true;
				return;

			} else if ("blackListed".equals(rawName)) {
				int blRev;

				blRev = Integer.parseInt(attrs.getValue("rev"));

				Logger.notice(this, "BlackListing rev '" + Integer.toString(blRev) + "'");

				index.addBlackListedRev(blRev);
			}



			/* ignore unknown tags */

			/* et paf ! Ca fait des Chocapics(tm)(r)(c)(m)(dtc) ! */
		}

		/**
		 * Called when a closing tag is met
		 *
		 * @see ContentHandler#endElement(String, String, String)
		 */
		public void endElement(String nameSpaceURI, String localName,
							   String rawName) throws SAXException {
			if (rawName == null) {
				rawName = localName;
			}

			if (rawName == null)
				return;

			if ("owner".equals(rawName)) {
				ownerTag = false;
				return;
			} else if ("privateKey".equals(rawName)) {
				privateKeyTag = false;
				return;
			} else if ("date".equals(rawName)) {
				dateTag = false;
				return;
			} else if ("category".equals(rawName)) {
				categoryTag = false;
				return;
			} else if ("header".equals(rawName)) {
				if (dateStr != null) {
					SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
					Date dateUtil = sdf.parse(dateStr, new ParsePosition(0));

					index.setInsertionDate(dateUtil);
				}

				if (categoryStr != null)
					index.setCategory(categoryStr);
			} else if ("comments".equals(rawName)) {
				return;
			} else if ("client".equals(rawName)) {
				clientTag = false;
				return;
			}
		}

		/**
		 * Called when a text between two tag is met
		 *
		 * @param ch
		 * 		text
		 * @param start
		 * 		position
		 * @param end
		 * 		position
		 * @see ContentHandler#characters(char[], int, int)
		 */
		public void characters(char[] ch, int start, int end) throws SAXException {
			String txt = new String(ch, start, end);

			if (ownerTag) {
				/* \_o< ==> TODO */

				return;
			}

			if (dateTag) {
				dateStr = txt;
			}

			if (categoryTag) {
				categoryStr = txt;
			}

			if (clientTag) {
				index.setClientVersion(txt);
			}

			if (privateKeyTag) {
				if (index.getPrivateKey() == null
						|| index.getPrivateKey().trim().equals(txt.trim())) {
					/**
					 * the private key was published, we will have to do the same later
					 */
					index.setPublishPrivateKey(true);
				} else {
					/**
					 * the provided key doesn't match with the one we have,
					 * we won't publish it anymore
					 */
					Logger.warning(this, "A private key was provided, but didn't match with the one we have ; ignored.");
				}

				if (index.getPrivateKey() == null) {
					String newPrivate = txt.trim();

					/* check that nobody is trying to inject some FCP commands
					 * through the private key
					 */
					if (!FreenetURIHelper.isAKey(newPrivate)
							|| newPrivate.indexOf('\n') >= 0) {
						Logger.warning(this, "Invalid private key");
						return;
					}

					index.setPrivateKey(newPrivate);
				}
				return;
			}




			/* ignore unkwown stuffs */

		}

		/**
		 * Called when parsing is finished
		 *
		 * @see ContentHandler#endDocument()
		 */
		public void endDocument() throws SAXException {
			if (!hasCommentTag) {
				Logger.debug(this, "No comment allowed in this index");
			}
		}
	}

	/** see import functionnality */
	public IndexHandler getIndexHandler() {
		return getIndexHandler(true);
	}

	public IndexHandler getIndexHandler(boolean clean) {
		return new IndexHandler(clean);
	}

	public synchronized void loadXML(final InputStream input, boolean clean) {
		IndexHandler handler = new IndexHandler(clean);

		try {
			// Use the default (non-validating) parser
			SAXParserFactory factory = SAXParserFactory.newInstance();

			// Parse the input
			SAXParser saxParser = factory.newSAXParser();

			Logger.info(this, "Parsing index ...");
			saxParser.parse(input, handler);
			Logger.info(this, "Parsing done");

		} catch (ParserConfigurationException e) {
			Logger.notice(this, "Error (1) while parsing index: " + e.toString());
		} catch (SAXException e) {
			Logger.notice(this, "Error (2) while parsing index: " + e.toString());
		} catch (IOException e) {
			Logger.notice(this, "Error (3) while parsing index: " + e.toString());
		} catch (Exception e) {
			Logger.notice(this, "Error (4) while parsing index: " + e.toString());
		}
	}

}

