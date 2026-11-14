/*
 * Copyright 2018 Eternita
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.frontcache.plugin.host;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import org.frontcache.FCConfig;
import org.frontcache.guard.GuardRule;
import org.frontcache.io.PluginStats;
import org.frontcache.license.BuildInfo;
import org.frontcache.module.FrontcacheModule;
import org.frontcache.plugin.FrontcachePlugin;
import org.frontcache.plugin.ManagementAction;
import org.frontcache.plugin.NodeMode;
import org.frontcache.plugin.PluginKind;
import org.frontcache.plugin.PluginManifest;
import org.frontcache.plugin.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The node's plugins - found once per process, started and stopped with the engine.
 *
 * <h2>Finding them</h2>
 *
 * Every {@value PluginManifest#RESOURCE} on the core's class path names a plugin's entry class; the class is
 * instantiated through its no-argument constructor. A pre-2.13 {@code FrontcacheModule} found through its
 * {@code META-INF/services} file and carrying no manifest is run through {@link LegacyModulePlugin} as a
 * {@code manage} plugin, with a deprecation WARN. A manifest that is refused, a class that cannot be loaded, a
 * duplicate id, an unsupported {@code plugin-api} major or a kind this node does not offer: logged, reported in
 * {@code get-node-stats}, and <b>the node starts without that plugin</b>.
 *
 * <h2>A plugin that fails is a plugin that is absent</h2>
 *
 * Every call into a plugin is wrapped. One that throws in {@code start} is dropped until the next start; one that
 * contributes something of a kind it did not declare (an event listener without {@code observe}, a management
 * action without {@code manage}) is refused and stopped. Nothing here may stop a node from serving.
 */
public final class FrontcachePlugins {

	private static final Logger logger = LoggerFactory.getLogger(FrontcachePlugins.class);

	/** The kinds this node offers today; a plugin declaring another is refused at load. */
	static final Set<PluginKind> OFFERED = Collections.unmodifiableSet(
			java.util.EnumSet.of(PluginKind.OBSERVE, PluginKind.MANAGE));

	/** {@code front-cache.plugins.<id>.queue}: a listener's queue capacity. */
	static final String QUEUE_PROPERTY = "front-cache.plugins.%s.queue";

	static final long GUARD_SUMMARY_INTERVAL_MILLIS = 10_000L;

	enum State {
		LOADED, STARTED, FAILED, REFUSED, STOPPED;

		String label() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** One plugin as found: its manifest (null when the manifest itself was refused) and instance (null likewise). */
	static final class Slot {
		final String id;
		final PluginManifest manifest;
		final FrontcachePlugin plugin;
		final boolean legacy;
		final PluginCounters counters = new PluginCounters();
		volatile State state;
		volatile String reason;

		Slot(String id, PluginManifest manifest, FrontcachePlugin plugin, boolean legacy, State state, String reason) {
			this.id = id;
			this.manifest = manifest;
			this.plugin = plugin;
			this.legacy = legacy;
			this.state = state;
			this.reason = reason;
		}

		/** Refused for good (manifest, class, kinds): never started, whatever the engine does. */
		boolean refused() {
			return null == plugin || State.REFUSED == state;
		}
	}

	private static volatile List<Slot> loaded = null;

	/** Plugins whose last start succeeded; replaced wholesale, never mutated. */
	private static volatile List<Slot> started = List.of();

	/** Action name -> action, from the started plugins; replaced wholesale, never mutated. */
	private static volatile Map<String, ManagementAction> actions = Map.of();

	private static volatile NodeMode mode = null;

	private FrontcachePlugins() {
	}

	/** Every plugin on the core's class path, found once. */
	static List<Slot> loaded() {

		List<Slot> local = loaded;
		if (null != local)
			return local;

		synchronized (FrontcachePlugins.class) {
			if (null == loaded)
				loaded = load(FrontcachePlugin.class.getClassLoader());
			return loaded;
		}
	}

	/** For tests: replaces what was found. */
	static synchronized void install(List<Slot> slots) {
		stop(true);
		loaded = Collections.unmodifiableList(new ArrayList<>(slots));
		mode = null;
	}

	/** Finds the plugins a class loader sees - their manifests, then the legacy modules. Never throws. */
	static List<Slot> load(ClassLoader loader) {

		List<Slot> slots = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		Set<String> classes = new HashSet<>();

		Enumeration<URL> manifests;
		try {
			manifests = loader.getResources(PluginManifest.RESOURCE);
		} catch (IOException e) {
			logger.error("Could not list " + PluginManifest.RESOURCE + " - the node starts without plugins", e);
			manifests = Collections.emptyEnumeration();
		}

		while (manifests.hasMoreElements())
		{
			URL url = manifests.nextElement();
			Slot slot = fromManifest(url, loader, ids);
			slots.add(slot);
			if (null != slot.manifest)
			{
				ids.add(slot.id);
				classes.add(slot.manifest.entryClass());
			}
		}

		slots.addAll(legacyModules(loader, ids, classes));

		for (Slot slot : slots)
			if (slot.refused())
				logger.error("Plugin '{}' is not loaded - the node starts without it: {}", slot.id, slot.reason);
			else if (slot.legacy)
				logger.warn("Plugin '{}' found: {} - a FrontcacheModule without a manifest, run through the deprecated"
						+ " adapter for one release; give it {}", slot.id, slot.plugin.getClass().getName(),
						PluginManifest.RESOURCE);
			else
				logger.info("Plugin '{}' found: {} ({})", slot.id, slot.manifest, slot.plugin.getClass().getName());
		return Collections.unmodifiableList(slots);
	}

	private static Slot fromManifest(URL url, ClassLoader loader, Set<String> ids) {

		Properties properties = new Properties();
		try (InputStream in = url.openStream(); Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (IOException | RuntimeException e) {
			return new Slot(String.valueOf(url), null, null, false, State.REFUSED, "manifest unreadable: " + e);
		}

		String declaredId = properties.getProperty("id", String.valueOf(url)).trim();
		PluginManifest manifest;
		try {
			manifest = PluginManifest.parse(properties, String.valueOf(url));
		} catch (IllegalArgumentException e) {
			return new Slot(declaredId, null, null, false, State.REFUSED, e.getMessage());
		}
		for (String warning : manifest.warnings())
			logger.warn("Plugin '{}' ({}): {}", manifest.id(), url, warning);

		String refusal = null;
		if (ids.contains(manifest.id()))
			refusal = "another plugin already has the id '" + manifest.id() + "'";
		else if (PluginManifest.PLUGIN_API != manifest.pluginApi())
			refusal = "built for plugin-api " + manifest.pluginApi() + "; this node offers plugin-api "
					+ PluginManifest.PLUGIN_API;
		else
			for (PluginKind kind : manifest.kinds())
				if (!OFFERED.contains(kind))
				{
					refusal = "declares kind '" + kind.manifestName() + "', which this node does not offer";
					break;
				}
		if (null != refusal)
			return new Slot(manifest.id(), manifest, null, false, State.REFUSED, refusal);

		try {
			Class<?> type = Class.forName(manifest.entryClass(), true, loader);
			if (!FrontcachePlugin.class.isAssignableFrom(type))
				return new Slot(manifest.id(), manifest, null, false, State.REFUSED,
						manifest.entryClass() + " does not implement " + FrontcachePlugin.class.getName());
			FrontcachePlugin plugin = (FrontcachePlugin) type.getDeclaredConstructor().newInstance();
			return new Slot(manifest.id(), manifest, plugin, false, State.LOADED, null);
		} catch (Throwable t) {
			return new Slot(manifest.id(), manifest, null, false, State.REFUSED,
					"cannot instantiate " + manifest.entryClass() + ": " + t);
		}
	}

	@SuppressWarnings("deprecation")
	private static List<Slot> legacyModules(ClassLoader loader, Set<String> ids, Set<String> classes) {

		List<Slot> slots = new ArrayList<>();
		Iterator<FrontcacheModule> it = ServiceLoader.load(FrontcacheModule.class, loader).iterator();
		while (true)
		{
			FrontcacheModule module;
			try {
				if (!it.hasNext())
					break;
				module = it.next();
			} catch (ServiceConfigurationError | RuntimeException | LinkageError e) {
				logger.error("A Frontcache module could not be loaded - the node starts without it", e);
				continue;
			}

			if (classes.contains(module.getClass().getName()))
				continue; // it has a manifest as well, and was loaded from it

			String id;
			try {
				id = module.id();
			} catch (Throwable t) {
				id = module.getClass().getName();
			}
			PluginManifest manifest = PluginManifest.forLegacyModule(id, module.getClass().getName(),
					BuildInfo.current().getVersion(), "META-INF/services/" + FrontcacheModule.class.getName());
			if (ids.contains(id))
				slots.add(new Slot(id, manifest, null, true, State.REFUSED, "another plugin already has the id '" + id + "'"));
			else
			{
				ids.add(id);
				slots.add(new Slot(id, manifest, new LegacyModulePlugin(module), true, State.LOADED, null));
			}
		}
		return slots;
	}

	/**
	 * Starts every plugin; called by the engine on each start, {@code reload} included.
	 *
	 * @param publicDomain {@code FrontCacheEngine.getPublicDomain()}, null when unset
	 * @param guardRules   the engine's current guard rules, for the guard summary
	 */
	public static synchronized void start(String publicDomain, Supplier<List<GuardRule>> guardRules) {

		CoreEvents.setSite(publicDomain);

		List<Slot> ok = new ArrayList<>();
		Map<String, ManagementAction> byName = new LinkedHashMap<>();

		for (Slot slot : loaded())
		{
			if (slot.refused())
				continue;
			if (startOne(slot, publicDomain, byName))
				ok.add(slot);
		}

		started = Collections.unmodifiableList(ok);
		actions = Collections.unmodifiableMap(byName);

		FcEventBus.startGuardSummary(guardRules, GUARD_SUMMARY_INTERVAL_MILLIS);

		for (Slot slot : loaded())
			CoreEvents.pluginStateChanged(slot.id, slot.state.label(), slot.reason);
	}

	private static boolean startOne(Slot slot, String publicDomain, Map<String, ManagementAction> byName) {

		FrontcachePlugin plugin = slot.plugin;
		try {
			plugin.start(new CorePluginContext(slot.manifest, publicDomain));
		} catch (Throwable t) {
			slot.counters.errors.increment();
			fail(slot, State.FAILED, "start threw " + t, t);
			return false;
		}

		List<EventListener> listeners;
		Map<String, ManagementAction> own;
		try {
			listeners = nonNull(plugin.eventListeners());
			own = nonNull(plugin.managementActions());
		} catch (Throwable t) {
			slot.counters.errors.increment();
			fail(slot, State.FAILED, "listing its contributions threw " + t, t);
			stopQuietly(slot, false);
			return false;
		}

		String refusal = null;
		if (!listeners.isEmpty() && !slot.manifest.declares(PluginKind.OBSERVE))
			refusal = "it has event listeners but does not declare kind 'observe'";
		else if (!own.isEmpty() && !slot.manifest.declares(PluginKind.MANAGE))
			refusal = "it has management actions but does not declare kind 'manage'";
		if (null != refusal)
		{
			fail(slot, State.REFUSED, refusal, null);
			stopQuietly(slot, false);
			return false;
		}

		int capacity = queueCapacity(slot.id);
		for (EventListener listener : listeners)
		{
			try {
				FcEventBus.subscribe(slot.id, listener, capacity, slot.counters);
			} catch (Throwable t) {
				slot.counters.errors.increment();
				logger.error("Plugin '" + slot.id + "' has an event listener that could not be subscribed", t);
			}
		}

		for (Map.Entry<String, ManagementAction> e : own.entrySet())
		{
			if (byName.containsKey(e.getKey()))
				logger.warn("Plugin '{}' declares management action '{}', which another plugin already answers - the"
						+ " first one wins", slot.id, e.getKey());
			else
				byName.put(e.getKey(), e.getValue());
		}

		slot.state = State.STARTED;
		slot.reason = null;
		logger.info("Plugin '{}' started: {}", slot.id, slot.manifest);
		return true;
	}

	private static void fail(Slot slot, State state, String reason, Throwable t) {
		slot.state = state;
		slot.reason = reason;
		if (null == t)
			logger.error("Plugin '{}' {} - the node runs without it: {}", slot.id, state.label(), reason);
		else
			logger.error("Plugin '" + slot.id + "' " + state.label() + " - the node runs without it", t);
	}

	private static int queueCapacity(String id) {
		String value = FCConfig.getProperty(String.format(QUEUE_PROPERTY, id));
		if (null == value)
			return FcEventBus.DEFAULT_QUEUE_CAPACITY;
		try {
			int capacity = Integer.parseInt(value.trim());
			if (0 < capacity)
				return capacity;
		} catch (NumberFormatException e) {
			// fall through
		}
		logger.warn("{} is not a positive number: '{}' - using {}", String.format(QUEUE_PROPERTY, id), value,
				FcEventBus.DEFAULT_QUEUE_CAPACITY);
		return FcEventBus.DEFAULT_QUEUE_CAPACITY;
	}

	/** @param shutdown see {@link FrontcachePlugin#stop(boolean)} */
	public static synchronized void stop(boolean shutdown) {

		FcEventBus.stopAll();

		for (Slot slot : started)
			if (stopQuietly(slot, shutdown))
				slot.state = State.STOPPED;

		started = List.of();
		actions = Map.of();
	}

	private static boolean stopQuietly(Slot slot, boolean shutdown) {
		try {
			slot.plugin.stop(shutdown);
			return true;
		} catch (Throwable t) {
			slot.counters.errors.increment();
			logger.error("Plugin '" + slot.id + "' failed to stop", t);
			return false;
		}
	}

	/**
	 * Called by {@code FrontCacheServlet} / {@code FrontCacheFilter} as they start - possibly before the engine, so
	 * it reaches every loaded plugin rather than only the started ones.
	 */
	public static void modeKnown(NodeMode deployedAs) {

		if (null == deployedAs || deployedAs == mode)
			return;
		mode = deployedAs;

		for (Slot slot : loaded())
		{
			if (slot.refused())
				continue;
			try {
				slot.plugin.onModeKnown(deployedAs);
			} catch (Throwable t) {
				slot.counters.errors.increment();
				logger.error("Plugin '" + slot.id + "' failed on mode " + deployedAs, t);
			}
		}
	}

	/** The management action a started plugin answers under {@code name}, or null. */
	public static ManagementAction managementAction(String name) {
		return (null == name) ? null : actions.get(name);
	}

	/** Every plugin action's description, by name - for the {@code help} action. */
	public static Map<String, String> managementActionDescriptions() {

		Map<String, String> descriptions = new TreeMap<>();
		for (Map.Entry<String, ManagementAction> e : actions.entrySet())
		{
			try {
				descriptions.put(e.getKey(), e.getValue().description());
			} catch (Throwable t) {
				descriptions.put(e.getKey(), "");
			}
		}
		return descriptions;
	}

	/** The management action names the started plugins answer. */
	public static Set<String> managementActionNames() {
		return actions.keySet();
	}

	/**
	 * Lets every loaded plugin register its meters - see {@link FrontcachePlugin#bindMeters(Object)}. Loaded rather
	 * than started: the exporter may be built before the engine, and meters read their state at scrape time anyway.
	 */
	public static void bindMeters(Object meterRegistry) {

		for (Slot slot : loaded())
		{
			if (slot.refused())
				continue;
			try {
				slot.plugin.bindMeters(meterRegistry);
			} catch (Throwable t) {
				slot.counters.errors.increment();
				logger.error("Plugin '" + slot.id + "' failed to register its meters", t);
			}
		}
	}

	/** The ids of the started plugins, in load order - {@code get-node-stats}' {@code modules}. */
	public static List<String> ids() {
		List<String> ids = new ArrayList<>();
		for (Slot slot : started)
			ids.add(slot.id);
		return ids;
	}

	/** Every plugin found, started or not - {@code get-node-stats}' {@code plugins}. */
	public static List<PluginStats> stats() {

		List<PluginStats> all = new ArrayList<>();
		for (Slot slot : loaded())
			all.add(stats(slot));
		return all;
	}

	/** The ids of every plugin found, for the metrics exporter's per-plugin series. */
	public static List<String> loadedIds() {
		List<String> ids = new ArrayList<>();
		for (Slot slot : loaded())
			ids.add(slot.id);
		return ids;
	}

	/** One plugin's stats by id, read at scrape time; null when there is none. */
	public static PluginStats stats(String id) {
		for (Slot slot : loaded())
			if (slot.id.equals(id))
				return stats(slot);
		return null;
	}

	private static PluginStats stats(Slot slot) {
		PluginStats stats = new PluginStats();
		stats.setId(slot.id);
		PluginManifest manifest = slot.manifest;
		if (null != manifest)
		{
			stats.setName(manifest.name());
			stats.setVersion(manifest.version());
			stats.setVendor(manifest.vendor());
			stats.setLicense(manifest.license());
			stats.setPluginApi(manifest.pluginApi());
			List<String> kinds = new ArrayList<>();
			for (PluginKind kind : manifest.kinds())
				kinds.add(kind.manifestName());
			stats.setKinds(kinds);
			stats.setRequestPath(manifest.isRequestPath());
		}
		stats.setState(slot.state.label());
		stats.setReason(slot.reason);
		stats.setLegacy(slot.legacy);
		stats.setEventsDelivered(slot.counters.delivered.sum());
		stats.setEventsDropped(slot.counters.dropped.sum());
		stats.setErrors(slot.counters.errors.sum());
		return stats;
	}

	private static <T> List<T> nonNull(List<T> list) {
		return (null == list) ? List.of() : list;
	}

	private static <K, V> Map<K, V> nonNull(Map<K, V> map) {
		return (null == map) ? Map.of() : map;
	}
}
