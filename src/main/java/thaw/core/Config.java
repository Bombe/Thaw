package thaw.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import thaw.utils.Xml;
import thaw.utils.Xml.Creator;

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

			checkForPluginsToReload(key, value);

			/* and to finish, we set the value */
			if (value != null)
				parameters.put(key, value);
			else
				parameters.remove(key);
		}
	}

	private void checkForPluginsToReload(String key, String value) {
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

		listenChanges = false;
		pluginsToReload.clear();
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
	public void loadConfig() {
		if (!configFile.exists() || !configFile.canRead()) {
			Logger.notice(this, "Unable to read config file '" + configFile.getPath() + "'");
			return;
		}

		loadXmlConfig();
	}

	private void loadXmlConfig() {
		Optional<Document> xmlDoc = Xml.parseFile(configFile);
		if (!xmlDoc.isPresent()) {
			return;
		}

		Element rootEl = xmlDoc.get().getDocumentElement();
		loadParameters(rootEl);
		loadPlugins(rootEl);
	}

	private void loadParameters(Element rootEl) {
		final NodeList params = rootEl.getElementsByTagName("param");

		for (int i = 0; i < params.getLength(); i++) {
			final Node paramNode = params.item(i);

			if (paramNode.getNodeType() == Node.ELEMENT_NODE) {
				Element paramEl = (Element) paramNode;
				parameters.put(paramEl.getAttribute("name"), paramEl.getAttribute("value"));
			}
		}
	}

	private void loadPlugins(Element rootEl) {
		final NodeList plugins = rootEl.getElementsByTagName("plugin");

		for (int i = 0; i < plugins.getLength(); i++) {
			final Node pluginNode = plugins.item(i);

			if (pluginNode.getNodeType() == Node.ELEMENT_NODE) {
				Element pluginEl = (Element) pluginNode;
				pluginNames.add(pluginEl.getAttribute("name"));
			}
		}
	}

	/**
	 * Save the configuration.
	 *
	 * @return true if success, else false.
	 */
	public void saveConfig() {
		if (configFileIsNotWritable()) {
			Logger.warning(this, "Unable to write config file '" + configFile.getPath() + "' (can't write)");
			throw new ConfigFileNotWritable();
		}

		Xml.createXmlFile(configFile, "config", new ConfigFileCreator());
	}

	private boolean configFileIsNotWritable() {
		try {
			return (!configFile.exists() && !configFile.createNewFile()) || !configFile.canWrite();
		} catch (IOException e) {
			Logger.warning(this, "Error while checking perms to save config: " + e);
			return false;
		}
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

	public static class ConfigFileNotWritable extends RuntimeException {

	}

	/**
	 * {@link Creator} implementation that stores the configuration into a {@link
	 * Document}.
	 *
	 * @author <a href="mailto:bombe@pterodactylus.net">David ‘Bombe’ Roden</a>
	 */
	private class ConfigFileCreator implements Creator {

		@Override
		public void create(Document document) {
			Element rootElement = document.getDocumentElement();
			createParameterElements(document, rootElement);
			createPluginElements(document, rootElement);
		}

		private void createPluginElements(Document document, Element rootElement) {
			for (String pluginName : pluginNames) {
				final Element pluginEl = document.createElement("plugin");
				pluginEl.setAttribute("name", pluginName);
				rootElement.appendChild(pluginEl);
			}
		}

		private void createParameterElements(Document document, Element rootElement) {
			for (Entry<String, String> parameter : parameters.entrySet()) {
				final Element paramEl = document.createElement("param");
				paramEl.setAttribute("name", parameter.getKey());
				paramEl.setAttribute("value", parameter.getValue());
				rootElement.appendChild(paramEl);
			}
		}

	}

}
