/*
  XMLTools.java / Frost
  Copyright (C) 2003  Frost Project <jtcfrost.sourceforge.net>

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU General Public License as
  published by the Free Software Foundation; either version 2 of
  the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
  General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/
package frost.util;

import java.io.*;
import java.util.*;
import thaw.core.Logger;
import javax.xml.parsers.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.OutputKeys;

import org.w3c.dom.*;
import org.xml.sax.*;

/**
 * A place to hold utility methods for XML processing.
 */
public class XMLTools {

    private static DocumentBuilderFactory validatingFactory = DocumentBuilderFactory.newInstance();
    private static DocumentBuilderFactory nonValidatingFactory = DocumentBuilderFactory.newInstance();

    {
        validatingFactory.setAttribute("http://apache.org/xml/features/disallow-doctype-decl", Boolean.TRUE);
        validatingFactory.setAttribute("http://xml.org/sax/features/external-general-entities",Boolean.FALSE);
        validatingFactory.setAttribute("http://xml.org/sax/features/external-parameter-entities",Boolean.FALSE);
        validatingFactory.setAttribute("http://apache.org/xml/features/nonvalidating/load-dtd-grammar",Boolean.FALSE);
        validatingFactory.setAttribute("http://apache.org/xml/features/nonvalidating/load-external-dtd",Boolean.FALSE);
        validatingFactory.setValidating(true);

        nonValidatingFactory.setAttribute("http://apache.org/xml/features/disallow-doctype-decl", Boolean.TRUE);
        nonValidatingFactory.setAttribute("http://xml.org/sax/features/external-general-entities",Boolean.FALSE);
        nonValidatingFactory.setAttribute("http://xml.org/sax/features/external-parameter-entities",Boolean.FALSE);
        nonValidatingFactory.setAttribute("http://apache.org/xml/features/nonvalidating/load-dtd-grammar",Boolean.FALSE);
        nonValidatingFactory.setAttribute("http://apache.org/xml/features/nonvalidating/load-external-dtd",Boolean.FALSE);
        nonValidatingFactory.setValidating(false);
    }

    /**
     * creates a document containing a single element - the one
     * returned by getXMLElement of the argument
     * @param element the object that will be contained by the document
     * @return the document
     */
    public static Document getXMLDocument(XMLizable element) {
        Document doc = createDomDocument();
        doc.appendChild(element.getXMLElement(doc));
        return doc;
    }

    /**
     * Serializes the XML into a byte array.
     */
    public static byte [] getRawXMLDocument (XMLizable element) {
        Document doc = getXMLDocument(element);
        File tmp = getXmlTempFile();
        byte [] result=null;
        try {
            writeXmlFile(doc, tmp.getPath());
            result = FileAccess.readByteArray(tmp);
        } catch (Throwable t) {
            Logger.notice(t, "Exception thrown in getRawXMLDocument(XMLizable element): "+ t.toString());
        }
        tmp.delete();
        return result;
    }

    /**
     * Returns the parsed Document for the given xml content.
     * @param content  xml data
     * @return  xml document
     */
    public static Document parseXmlContent(byte[] content, boolean validating) {
        Document result = null;
        File tmp = getXmlTempFile();
        try {
            FileAccess.writeFile(content, tmp);
            result = XMLTools.parseXmlFile(tmp, validating);
        } catch(Throwable t) {
            Logger.notice(t, "Exception thrown in parseXmlContent: "+ t.toString());
        }
        tmp.delete();
        return result;
    }

    /**
     * Parses an XML file and returns a DOM document.
     * If validating is true, the contents is validated against the DTD
     * specified in the file.
     */
    public static Document parseXmlFile(String filename, boolean validating)
    throws IllegalArgumentException
    {
        return parseXmlFile(new File(filename), validating);
    }

    /**
     * Parses an XML file and returns a DOM document.
     * If validating is true, the contents is validated against the DTD
     * specified in the file.
     */
    public static Document parseXmlFile(File file, boolean validating)
        throws IllegalArgumentException {
        try {
            DocumentBuilder builder;
            if (validating) {
                synchronized (validatingFactory) {
                    builder = validatingFactory.newDocumentBuilder();
                }
            } else {
                synchronized (nonValidatingFactory) {
                    builder = nonValidatingFactory.newDocumentBuilder();
                }
            }
            return builder.parse(file);
        } catch (SAXException e) {
            // A parsing error occurred; the xml input is not valid
		Logger.notice(e,
			     "Parsing of xml file failed (send badfile.xml to a dev for analysis) - " +
			     "File name: '" + file.getName() + "': "+e.toString());
            file.renameTo(new File("badfile.xml"));
            throw new IllegalArgumentException();
        } catch (ParserConfigurationException e) {
            Logger.notice(e, "Exception thrown in parseXmlFile(File file, boolean validating) - " +
			 "File name: '" + file.getName() + "': "+
			 e.toString());
        } catch (IOException e) {
            Logger.notice(e,
			 "Exception thrown in parseXmlFile(File file, boolean validating) - " +
			 "File name: '" + file.getName() + "': "+e);
        }
        return null;
    }

    /**
     * This method writes a DOM document to a file.
     */
    public static boolean writeXmlFile(Document doc, String filename) {
        return writeXmlFile(doc, new File(filename));
    }

    /**
     * This method writes a DOM document to a file.
     */
    public static boolean writeXmlFile(Document doc, File file) {
	    /* This method didn't work for me, so I replaced it by mine */
	    try {
		    FileOutputStream out = new FileOutputStream(file);
		    StreamResult streamResult;

		    streamResult = new StreamResult(out);


		    /* Serialization */
		    final DOMSource domSource = new DOMSource(doc);
		    final TransformerFactory transformFactory = TransformerFactory.newInstance();

		    Transformer serializer;

		    serializer = transformFactory.newTransformer();

		    serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		    serializer.setOutputProperty(OutputKeys.INDENT, "yes");


		    serializer.transform(domSource, streamResult);

		    return true;
	    } catch(final javax.xml.transform.TransformerException e) {
		    Logger.notice(e, "Unable to generate XML because: "+e.toString());
		    e.printStackTrace();
	    } catch(java.io.FileNotFoundException e) {
		    Logger.notice(e, "File not found exception ?!");
	    }
    
	    return false;
    }

    /**
     * This method creates a new DOM document.
     */
    public static Document createDomDocument() {

        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.newDocument();
            return doc;
        } catch (ParserConfigurationException e) {
            Logger.notice(e, "Exception thrown in createDomDocument(): "+e.toString());
        }
        return null;
    }

    /**
     * gets a true or false attribute from an element
     */
    public static boolean getBoolValueFromAttribute(Element el, String attr, boolean defaultVal) {

        String res = el.getAttribute(attr);

        if( res == null ) {
            return defaultVal;
        }

        if( res.toLowerCase().equals("true") == true ) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns a list containing all Elements of this parent with given tag name.
     */
    public static List getChildElementsByTagName(Element parent, String name) {

        LinkedList newList = new LinkedList();

        NodeList childs = parent.getChildNodes();
        for( int x=0; x<childs.getLength(); x++ ) {
            Node child = childs.item(x);
            if( child.getNodeType() == Node.ELEMENT_NODE ) {
                Element ele = (Element)child;
                if( ele.getTagName().equals( name ) == true ) {
                    newList.add( ele );
                }
            }
        }
        return newList;
    }

    /**
     * Gets the Element by name from parent and extracts the Text child node.
     * E.g.:
     * <parent>
     *   <child>
     *     text
     */
    public static String getChildElementsTextValue( Element parent, String childname ) {

        List nodes = getChildElementsByTagName( parent, childname );
        if( nodes.size() == 0 ) {
            return null;
        }
        Text txtname = (Text) (((Node)nodes.get(0)).getFirstChild());
        if( txtname == null ) {
            return null;
        }
        return txtname.getData();
    }

    /**
     * Gets the Element by name from parent and extracts the CDATASection child node.
     */
    public static String getChildElementsCDATAValue( Element parent, String childname ) {

        List nodes = getChildElementsByTagName( parent, childname );
        if( nodes.size() == 0 ) {
            return null;
        }
        CDATASection txtname = (CDATASection) ((Node)nodes.get(0)).getFirstChild();
        if( txtname == null ) {
            return null;
        }
        // if the text contained control characters then it was maybe splitted into multiple CDATA sections.
        if( txtname.getNextSibling() == null ) {
            return txtname.getData();
        }
        
        StringBuffer sb = new StringBuffer(txtname.getData());
        while( txtname.getNextSibling() != null ) {
            txtname = (CDATASection)txtname.getNextSibling();
            sb.append(txtname.getData());
        }
        return sb.toString();
    }

    /**
     * create a proper temp file (deleted on VM emergency exit).
     */
    private static File getXmlTempFile() {
        File tmp = FileAccess.createTempFile("xmltools_", ".tmp");
        tmp.deleteOnExit();
        return tmp;
    }
    
//    public static void main(String[] args) {
//
//        Document d = createDomDocument();
//        Element el = d.createElement("FrostMessage");
//
//        CDATASection cdata;
//        Element current;
//
//        current = d.createElement("MessageId");
//        cdata = d.createCDATASection("<![CDATA[\\</MessageId>]]> <helpme />");
//        current.appendChild(cdata);
//        
//        el.appendChild(current);
//        
//        d.appendChild(el);
//
//        boolean ok = writeXmlFile(d, "d:\\AAAAA.xml");
//        System.out.println("ok="+ok);
//        
//        Document dd = parseXmlFile("d:\\AAAAA.xml", false);
//        Element root = dd.getDocumentElement();
//        String s = XMLTools.getChildElementsCDATAValue(root, "MessageId");
//        System.out.println("s="+s);
//    }
}
