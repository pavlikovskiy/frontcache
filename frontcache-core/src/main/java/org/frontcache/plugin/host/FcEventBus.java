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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.frontcache.guard.GuardRule;
import org.frontcache.plugin.event.CircuitStateChanged;
import org.frontcache.plugin.event.ConfigReloaded;
import org.frontcache.plugin.event.EventListener;
import org.frontcache.plugin.event.FcEvent;
import org.frontcache.plugin.event.GuardMatched;
import org.frontcache.plugin.event.GuardSummary;
import org.frontcache.plugin.event.PluginStateChanged;
import org.frontcache.plugin.event.RateLimitExceeded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The E1 event bus: where the node hands events to the plugins that observe it.
 *
 * <h2>On the request path</h2>
 *
 * An emit point asks {@link #wants(long)} - one volatile read of a bitmask, no allocation - and only when some
 * listener subscribed to that type does it build the event and {@link #publish} it, which {@code offer}s it into
 * each subscriber's <b>own bounded queue</b>. A full queue drops the event and counts it; nothing here ever blocks
 * a request thread, and a listener that blocks only fills its own queue.
 *
 * <h2>Delivery</h2>
 *
 * One daemon thread per subscriber drains its queue and calls {@link EventListener#onEvents} with whatever has
 * accumulated, up to {@value #MAX_BATCH} at a time: prompt when traffic is quiet, batched when it is not. A listener
 * that throws is logged (once a minute) and keeps receiving.
 *
 * <p>Subscribers are replaced wholesale on every engine start ({@link FrontcachePlugins}); {@link #stopAll()} on
 * engine stop counts whatever is still queued as dropped.
 */
public final class FcEventBus {

	private static final Logger logger = LoggerFactory.getLogger(FcEventBus.class);

	public static final long GUARD_MATCHED = 1L;
	public static final long RATE_LIMIT_EXCEEDED = 1L << 1;
	public static final long GUARD_SUMMARY = 1L << 2;
	public static final long CIRCUIT_STATE_CHANGED = 1L << 3;
	public static final long CONFIG_RELOADED = 1L << 4;
	public static final long PLUGIN_STATE_CHANGED = 1L << 5;

	/** Every event type this node emits, and its bit. A type a listener names that is not here is never emitted. */
	static final Map<Class<? extends FcEvent>, Long> BITS = Map.of(
			GuardMatched.class, GUARD_MATCHED,
			RateLimitExceeded.class, RATE_LIMIT_EXCEEDED,
			GuardSummary.class, GUARD_SUMMARY,
			CircuitStateChanged.class, CIRCUIT_STATE_CHANGED,
			ConfigReloaded.class, CONFIG_RELOADED,
			PluginStateChanged.class, PLUGIN_STATE_CHANGED);

	public static final int DEFAULT_QUEUE_CAPACITY = 10_000;

	static final int MAX_BATCH = 500;

	private static final long ERROR_LOG_INTERVAL_MILLIS = 60_000L;

	/** The union of every subscriber's mask - what {@link #wants} reads. */
	private static volatile long mask = 0L;

	private static volatile List<Subscriber> subscribers = List.of();

	private static GuardSummaryTicker ticker = null;

	private FcEventBus() {
	}

	/** Whether any listener subscribed to the event type with this bit - check it before building the event. */
	public static boolean wants(long bit) {
		return 0L != (mask & bit);
	}

	/** Offers the event to every subscriber of its type; never blocks. */
	public static void publish(FcEvent event) {

		Long bit = BITS.get(event.getClass());
		if (null == bit || !wants(bit))
			return;

		for (Subscriber subscriber : subscribers)
			subscriber.offer(event, bit);
	}

	/**
	 * Starts a dispatcher for one listener.
	 *
	 * @return the bits the listener subscribed to that this node emits
	 */
	static synchronized long subscribe(String pluginId, EventListener listener, int capacity, PluginCounters counters) {

		Set<Class<? extends FcEvent>> types = listener.subscribes();
		long listenerMask = 0L;
		if (null != types)
			for (Class<? extends FcEvent> type : types)
			{
				Long bit = BITS.get(type);
				if (null == bit)
					logger.warn("Plugin '{}' subscribes to {}, which this node does not emit - ignored", pluginId,
							(null == type) ? "null" : type.getName());
				else
					listenerMask |= bit;
			}

		if (0L == listenerMask)
		{
			logger.warn("Plugin '{}' has an event listener that subscribes to nothing this node emits", pluginId);
			return 0L;
		}

		Subscriber subscriber = new Subscriber(pluginId, listener, listenerMask, Math.max(1, capacity), counters);
		List<Subscriber> next = new ArrayList<>(subscribers);
		next.add(subscriber);
		subscribers = Collections.unmodifiableList(next);
		mask |= listenerMask;
		subscriber.start();
		return listenerMask;
	}

	/** Starts the 10-second {@link GuardSummary} ticker - only when someone subscribed to it. */
	static synchronized void startGuardSummary(Supplier<List<GuardRule>> rules, long intervalMillis) {
		if (null != ticker || !wants(GUARD_SUMMARY))
			return;
		ticker = new GuardSummaryTicker(rules, intervalMillis);
		ticker.start();
	}

	/** Stops the ticker and every dispatcher; what is still queued is counted as dropped. */
	static synchronized void stopAll() {

		if (null != ticker)
		{
			ticker.stop();
			ticker = null;
		}

		List<Subscriber> stopping = subscribers;
		mask = 0L;
		subscribers = List.of();
		for (Subscriber subscriber : stopping)
			subscriber.stop();
	}

	/** One listener: its queue, its thread, its share of the plugin's counters. */
	static final class Subscriber implements Runnable {

		private final String pluginId;
		private final EventListener listener;
		private final long mask;
		private final ArrayBlockingQueue<FcEvent> queue;
		private final PluginCounters counters;
		private final Thread thread;
		private volatile boolean running = true;
		private long lastErrorLog = 0L;

		Subscriber(String pluginId, EventListener listener, long mask, int capacity, PluginCounters counters) {
			this.pluginId = pluginId;
			this.listener = listener;
			this.mask = mask;
			this.queue = new ArrayBlockingQueue<>(capacity);
			this.counters = counters;
			this.thread = new Thread(this, "fc-plugin-" + pluginId + "-events");
			this.thread.setDaemon(true);
		}

		void start() {
			thread.start();
		}

		void offer(FcEvent event, long bit) {
			if (0L != (mask & bit) && !queue.offer(event))
				counters.dropped.increment();
		}

		void stop() {
			running = false;
			thread.interrupt();
			try {
				thread.join(1000L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			int left = queue.size();
			queue.clear();
			counters.dropped.add(left);
		}

		@Override
		public void run() {
			List<FcEvent> batch = new ArrayList<>(MAX_BATCH);
			while (running)
			{
				FcEvent first;
				try {
					first = queue.poll(500, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					return;
				}
				if (null == first)
					continue;

				batch.clear();
				batch.add(first);
				queue.drainTo(batch, MAX_BATCH - 1);
				try {
					listener.onEvents(Collections.unmodifiableList(new ArrayList<>(batch)));
					counters.delivered.add(batch.size());
				} catch (Throwable t) {
					counters.errors.increment();
					long now = System.currentTimeMillis();
					if (now - lastErrorLog >= ERROR_LOG_INTERVAL_MILLIS)
					{
						lastErrorLog = now;
						logger.error("Plugin '" + pluginId + "' threw from its event listener - it keeps receiving"
								+ " (logged once a minute)", t);
					}
				}
			}
		}
	}
}
