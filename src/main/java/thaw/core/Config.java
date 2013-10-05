package thaw.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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

import com.google.common.base.Objects;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * This class manages the thaw config.
 *
 * @author <a href="mailto:jflesch@gmail.com">Jerome Flesch</a>
 */
public class Config {

	private final Core core;

	private final File configFile;

	private final Map<String, String> parameters = new HashMap<String, String>();

	private final Multimap<String, Plugin> listeners = LinkedHashMultimap.create();

	private final List<String> pluginNames = new ArrayList<String>();

	private boolean listenChanges = false;

	private final List<Plugin> pluginsToReload = new ArrayList<Plugin>();

	public Config(Core core, final String filename) {
		this.core = core;

		configFile = new File(filename);
	}

	/** @return null if the value doesn't exit in the config. */
	public String getValue(final String key) {
		return (parameters.get(key));
	}

	/**
	 * called when majors changed will be done to the config and will imply some
	 * plugin reloading
	 */
	public void startChanges() {
		listenChanges = true;
		pluginsToReload.clear();
	}

	/** Set the value in the config. */
	public void setValue(final String key, final String value) {
		Logger.debug(this, "Setting value '" + key + "' to '" + value + "'");

		if (!Objects.equal(getValue(key), value)) {

			if (listenChanges) {
				for (Plugin plugin : listeners.get(key)) {
					/* if the plugin is not already in the plugin list to
					 * reload, we add it */
					if (!pluginsToReload.contains(plugin)) {
						Logger.notice(this, "Will have to reload '" + plugin.getClass().getName() + "' " +
								"because '" + key + "' was changed from '" + getValue(key) + "' to '" + value + "'");
						pluginsToReload.add(plugin);
					}

				}
			}

			/* and to finish, we set the value */
			if (value != null)
				parameters.put(key, value);
			else
				parameters.remove(key);
		}
	}

	/**
	 * called after startChanges. Will reload the plugin listening for the changed
	 * values
	 */
	public void applyChanges() {
		for (Plugin plugin : pluginsToReload) {
			core.getPluginManager().stopPlugin(plugin.getClass().getName());
			core.getPluginManager().runPlugin(plugin.getClass().getName());
		}

		cancelChanges();
	}

	/**
	 * Will not undo the changes do to the values, but reset to 0 the plugin list
	 * to reload Use it only if you know what you're doing !
	 */
	public void cancelChanges() {
		listenChanges = false;
		pluginsToReload.clear();
	}

	/** Add the plugin at the end of the plugin list. */
	public void addPlugin(final String name) {
		pluginNames.add(name);
	}

	/** Give a vector containing the whole list of plugins. */
	public List<String> getPluginNames() {
		return new ArrayList<String>(pluginNames);
	}

	/** Remove the given plugin. */
	public void removePlugin(final String name) {
		pluginNames.remove(name);
	}

	/**
	 * Load the configuration.
	 *
	 * @return true if success, else false.
	 */
	public boolean loadConfig() {
		if (!configFile.exists() || !configFile.canRead()) {
			Logger.notice(this, "Unable to read config file '" + configFile.getPath() + "'");
			return false;
		}

		DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder xmlBuilder;
		try {
			xmlBuilder = xmlFactory.newDocumentBuilder();
		} catch (final ParserConfigurationException e) {
			Logger.warning(this, "Unable to load config because: " + e);
			return false;
		}

		Document xmlDoc;
		try {
			xmlDoc = xmlBuilder.parse(configFile);
		} catch (final SAXException e) {
			Logger.warning(this, "Unable to load config because: " + e);
			return false;
		} catch (final IOException e) {
			Logger.warning(this, "Unable to load config because: " + e);
			return false;
		}

		Element rootEl = xmlDoc.getDocumentElement();

		final NodeList params = rootEl.getElementsByTagName("param");

		for (int i = 0; i < params.getLength(); i++) {
			final Node paramNode = params.item(i);

			if (paramNode.getNodeType() == Node.ELEMENT_NODE) {
				Element paramEl = (Element) paramNode;
				parameters.put(paramEl.getAttribute("name"), paramEl.getAttribute("value"));
			}
		}

		final NodeList plugins = rootEl.getElementsByTagName("plugin");

		for (int i = 0; i < plugins.getLength(); i++) {
			final Node pluginNode = plugins.item(i);

			if (pluginNode.getNodeType() == Node.ELEMENT_NODE) {
				Element pluginEl = (Element) pluginNode;
				pluginNames.add(pluginEl.getAttribute("name"));
			}
		}

		return true;
	}

	/**
	 * Save the configuration.
	 *
	 * @return true if success, else false.
	 */
	public boolean saveConfig() {
		try {
			if ((!configFile.exists() && !configFile.createNewFile())
					|| !configFile.canWrite()) {
				Logger.warning(this, "Unable to write config file '" + configFile.getPath() + "' (can't write)");
				return false;
			}
		} catch (final IOException e) {
			Logger.warning(this, "Error while checking perms to save config: " + e);
		}

		DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder xmlBuilder;
		try {
			xmlBuilder = xmlFactory.newDocumentBuilder();
		} catch (final ParserConfigurationException e) {
			Logger.error(this, "Unable to save configuration because: " + e.toString());
			return false;
		}

		DOMImplementation impl = xmlBuilder.getDOMImplementation();
		Document xmlDoc = impl.createDocument(null, "config", null);
		Element rootEl = xmlDoc.getDocumentElement();

		final Set<String> parameterKeySet = parameters.keySet();
		for (String entry : parameterKeySet) {
			final String value = parameters.get(entry);

			final Element paramEl = xmlDoc.createElement("param");
			paramEl.setAttribute("name", entry);
			paramEl.setAttribute("value", value);

			rootEl.appendChild(paramEl);
		}

		for (String pluginName : pluginNames) {
			final Element pluginEl = xmlDoc.createElement("plugin");

			pluginEl.setAttribute("name", pluginName);

			rootEl.appendChild(pluginEl);
		}

		/* Serialization */
		final DOMSource domSource = new DOMSource(xmlDoc);
		final TransformerFactory transformFactory = TransformerFactory.newInstance();

		Transformer serializer;
		try {
			serializer = transformFactory.newTransformer();
		} catch (final TransformerConfigurationException e) {
			Logger.error(this, "Unable to save configuration because: " + e.toString());
			return false;
		}

		serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		serializer.setOutputProperty(OutputKeys.INDENT, "yes");

		/* final step */
		StreamResult configOut = new StreamResult(configFile);
		try {
			serializer.transform(domSource, configOut);
		} catch (final TransformerException e) {
			Logger.error(this, "Unable to save configuration because: " + e.toString());
			return false;
		}

		return true;
	}

	public boolean isEmpty() {
		return (parameters.keySet().size() == 0);
	}

	/** Set the value only if it doesn't exits. */
	public void setDefaultValue(final String name, final String val) {
		if (getValue(name) == null)
			setValue(name, val);
	}

	/** don't override the values if already existing */
	public void setDefaultValues() {
		setDefaultValue("nodeAddress", "127.0.0.1");
		setDefaultValue("nodePort", "9481");
		setDefaultValue("maxSimultaneousDownloads", "-1");
		setDefaultValue("maxSimultaneousInsertions", "-1");
		setDefaultValue("maxUploadSpeed", "-1");
		setDefaultValue("thawId", "thaw_" + Integer.toString((new Random()).nextInt(1000)));
		setDefaultValue("advancedMode", "false");
		setDefaultValue("userNickname", "Another anonymous");
		setDefaultValue("multipleSockets", "true");
		setDefaultValue("downloadLocally", "true");
		setDefaultValue("sameComputer", "true");
	}

	public void addListener(String name, Plugin plugin) {
		listeners.put(name, plugin);
	}

}
