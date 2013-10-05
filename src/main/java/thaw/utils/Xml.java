/*
 * Thaw - Xml.java - Copyright © 2013 David Roden
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package thaw.utils;

import static com.google.common.base.Optional.absent;
import static com.google.common.base.Optional.of;

import java.io.File;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.google.common.base.Optional;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import thaw.core.Logger;

/**
 * Common helper methods to deal with XML parsing and creation.
 *
 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
 */
public class Xml {

	/**
	 * Parses the given file into an XML {@link Document}.
	 *
	 * @param file
	 * 		The file to parse
	 * @return The parsed document, or {@link Optional#absent()} if the file could
	 *         not be parsed
	 */
	public static Optional<Document> parseFile(File file) {
		try {
			DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder xmlBuilder = xmlFactory.newDocumentBuilder();
			return of(xmlBuilder.parse(file));
		} catch (final ParserConfigurationException pce1) {
			Logger.warning(Xml.class, "Unable to parse file because: " + pce1);
			return absent();
		} catch (final SAXException saxe1) {
			Logger.warning(Xml.class, "Unable to parse file because: " + saxe1);
			return absent();
		} catch (final IOException ioe1) {
			Logger.warning(Xml.class, "Unable to parse file because: " + ioe1);
			return absent();
		}
	}

	/**
	 * Creates an XML file.
	 *
	 * @param file
	 * 		The file to create
	 * @param rootElementName
	 * 		The name of the XML’s root element
	 * @param creator
	 * 		The XML creator used to fill the document
	 * @throws XmlError
	 * 		if the XML document can not be created
	 */
	public static void createXmlFile(File file, String rootElementName, Creator creator) throws XmlError {
		try {
			DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder xmlBuilder = xmlFactory.newDocumentBuilder();
			DOMImplementation impl = xmlBuilder.getDOMImplementation();
			Document document = impl.createDocument(null, rootElementName, null);
			creator.create(document);

			/* Serialization */
			final DOMSource domSource = new DOMSource(document);
			final TransformerFactory transformFactory = TransformerFactory.newInstance();
			Transformer serializer = transformFactory.newTransformer();

			serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			serializer.setOutputProperty(OutputKeys.INDENT, "yes");

			StreamResult configOut = new StreamResult(file);
			serializer.transform(domSource, configOut);
		} catch (final ParserConfigurationException e) {
			Logger.error(Xml.class, "Unable to save configuration because: " + e.toString());
			throw new XmlError();
		} catch (final TransformerConfigurationException e) {
			Logger.error(Xml.class, "Unable to save configuration because: " + e.toString());
			throw new XmlError();
		} catch (final TransformerException e) {
			Logger.error(Xml.class, "Unable to save configuration because: " + e.toString());
			throw new XmlError();
		}
	}

	/**
	 * A creator is responsible for filling a {@link Document} supplied by {@link
	 * #createXmlFile(File, String, Creator)} with nodes.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	public interface Creator {

		/**
		 * Creates the content of the given document.
		 *
		 * @param document
		 * 		The document to fill with content
		 */
		public void create(Document document);

	}

	/**
	 * Exception that signals an error while creating the XML file.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	public static class XmlError extends RuntimeException {

	}

}
