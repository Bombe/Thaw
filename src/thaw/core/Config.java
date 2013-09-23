package thaw.core;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class manages the thaw config.
 *
 * @author <a href="mailto:jflesch@gmail.com">Jerome Flesch</a>
 */
public class Config {

	public static String CONFIG_FILE_NAME = "thaw.conf.xml";

	private final Core core;

	private final File configFile;

	private final Map<String, String> parameters = new HashMap<String, String>();

	private final Map<String, List<Plugin>> listeners = new HashMap<String, List<Plugin>>();

	private final List<String> pluginNames = new ArrayList<String>();

	private boolean listenChanges = false;

	private final List<Plugin> pluginsToReload = null;

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

		String currentValue = getValue(key);

		if ((currentValue != null && !currentValue.equals(value))
				|| (currentValue == null && value != null)
				|| (currentValue != null && value == null)) {

			/* we get the plugin list to reload */
			List<Plugin> pluginList = listeners.get(key);

			if (listenChanges && pluginList != null) {
				for (Plugin plugin : pluginList) {
					/* if the plugin is not already in the plugin list to
					 * reload, we add it */
					if (!pluginsToReload.contains(plugin)) {
						Logger.notice(this, "Will have to reload '" + plugin.getClass().getName() + "' " +
								"because '" + key + "' was changed from '" + currentValue + "' to '" + value + "'");
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

	/**
	 * Add the plugin at the end of the given position (shifting already
	 * existing).
	 */
	public void addPlugin(final String name, final int position) {
		pluginNames.add(position, name);
	}

	/** Give a vector containing the whole list of plugins. */
	public List<String> getPluginNames() {
		return new ArrayList<String>(pluginNames);
	}

	/** Remove the given plugin. */
	public void removePlugin(final String name) {
		for (int i = 0; i < pluginNames.size(); i++) {
			final String currentPlugin = pluginNames.get(i);

			if (currentPlugin.equals(name))
				pluginNames.remove(i);
		}
	}

	/**
	 * Load the configuration.
	 *
	 * @return true if success, else false.
	 */
	public boolean loadConfig() {
		if (configFile == null) {
			Logger.error(this, "loadConfig(): No file specified !");
			return false;
		}

		if (!configFile.exists() || !configFile.canRead()) {
			Logger.notice(this, "Unable to read config file '" + configFile.getPath() + "'");
			return false;
		}

		Document xmlDoc = null;
		DocumentBuilderFactory xmlFactory = null;
		DocumentBuilder xmlBuilder = null;

		Element rootEl = null;

		xmlFactory = DocumentBuilderFactory.newInstance();

		try {
			xmlBuilder = xmlFactory.newDocumentBuilder();
		} catch (final javax.xml.parsers.ParserConfigurationException e) {
			Logger.warning(this, "Unable to load config because: " + e);
			return false;
		}

		try {
			xmlDoc = xmlBuilder.parse(configFile);
		} catch (final org.xml.sax.SAXException e) {
			Logger.warning(this, "Unable to load config because: " + e);
			return false;
		} catch (final java.io.IOException e) {
			Logger.warning(this, "Unable to load config because: " + e);
			return false;
		}

		rootEl = xmlDoc.getDocumentElement();

		final NodeList params = rootEl.getElementsByTagName("param");

		for (int i = 0; i < params.getLength(); i++) {
			Element paramEl;
			final Node paramNode = params.item(i);

			if ((paramNode != null) && (paramNode.getNodeType() == Node.ELEMENT_NODE)) {
				paramEl = (Element) paramNode;
				parameters.put(paramEl.getAttribute("name"), paramEl.getAttribute("value"));
			}
		}

		final NodeList plugins = rootEl.getElementsByTagName("plugin");

		for (int i = 0; i < plugins.getLength(); i++) {

			Element pluginEl;
			final Node pluginNode = plugins.item(i);

			if ((pluginNode != null) && (pluginNode.getNodeType() == Node.ELEMENT_NODE)) {
				pluginEl = (Element) pluginNode;
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
		StreamResult configOut;

		if (configFile == null) {
			Logger.error(this, "saveConfig(): No file specified !");
			return false;
		}

		try {
			if ((!configFile.exists() && !configFile.createNewFile())
					|| !configFile.canWrite()) {
				Logger.warning(this, "Unable to write config file '" + configFile.getPath() + "' (can't write)");
				return false;
			}
		} catch (final java.io.IOException e) {
			Logger.warning(this, "Error while checking perms to save config: " + e);
		}

		configOut = new StreamResult(configFile);

		Document xmlDoc = null;
		DocumentBuilderFactory xmlFactory = null;
		DocumentBuilder xmlBuilder = null;
		DOMImplementation impl = null;

		Element rootEl = null;

		xmlFactory = DocumentBuilderFactory.newInstance();

		try {
			xmlBuilder = xmlFactory.newDocumentBuilder();
		} catch (final javax.xml.parsers.ParserConfigurationException e) {
			Logger.error(this, "Unable to save configuration because: " + e.toString());
			return false;
		}

		impl = xmlBuilder.getDOMImplementation();

		xmlDoc = impl.createDocument(null, "config", null);

		rootEl = xmlDoc.getDocumentElement();

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
		} catch (final javax.xml.transform.TransformerConfigurationException e) {
			Logger.error(this, "Unable to save configuration because: " + e.toString());
			return false;
		}

		serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		serializer.setOutputProperty(OutputKeys.INDENT, "yes");

		/* final step */
		try {
			serializer.transform(domSource, configOut);
		} catch (final javax.xml.transform.TransformerException e) {
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

		List<Plugin> pluginList = listeners.get(name);

		if (pluginList == null) {
			pluginList = new ArrayList<Plugin>();
			listeners.put(name, pluginList);
		}

		if (pluginList.indexOf(plugin) < 0)
			pluginList.add(plugin);
	}

}
