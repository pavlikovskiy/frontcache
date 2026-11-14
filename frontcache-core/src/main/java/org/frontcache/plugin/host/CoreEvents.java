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

import java.util.List;
import java.util.Objects;

import org.frontcache.FCConfig;
import org.frontcache.core.RequestContext;
import org.frontcache.plugin.event.CircuitStateChanged;
import org.frontcache.plugin.event.ConfigReloaded;
import org.frontcache.plugin.event.FcEvent;
import org.frontcache.plugin.event.GuardMatched;
import org.frontcache.plugin.event.PluginStateChanged;
import org.frontcache.plugin.event.RateLimitExceeded;

/**
 * The core's emit points, one method per event: each checks {@link FcEventBus#wants} before building anything, so
 * a node nobody observes pays one volatile read per point and allocates nothing. Never throws into its caller.
 */
public final class CoreEvents {

	/** The public domain of the engine that started the plugins; null when unset. */
	private static volatile String site = null;

	private CoreEvents() {
	}

	static void setSite(String publicDomain) {
		site = publicDomain;
	}

	static String site() {
		return site;
	}

	static String node() {
		return FCConfig.getProperty(FCConfig.FRONTCACHE_ID_KEY);
	}

	/** A guard rule acted on a request (rejected / redirected) or matched it as a dry run (status 0). */
	public static void guardMatched(RequestContext context, String rule, String disposition, int status) {
		if (!FcEventBus.wants(FcEventBus.GUARD_MATCHED))
			return;
		try {
			FcEventBus.publish(new GuardMatched(System.currentTimeMillis(), node(), site, context.getRequestId(), rule,
					disposition, status, uri(context), context.getClientType(), context.getRateLimitKey()));
		} catch (RuntimeException e) {
			// a request view that cannot answer (a recycled request) is no reason to fail the request
		}
	}

	/** A client crossed a bucket's limit - called once per client per window, by the rate-limit predicate. */
	public static void rateLimitExceeded(RequestContext context, String bucket, String rule, int limit,
			long windowMillis, String clientKey) {
		if (!FcEventBus.wants(FcEventBus.RATE_LIMIT_EXCEEDED))
			return;
		try {
			FcEventBus.publish(new RateLimitExceeded(System.currentTimeMillis(), node(), site, context.getRequestId(),
					bucket, rule, limit, windowMillis, uri(context), clientKey));
		} catch (RuntimeException e) {
			// as above
		}
	}

	public static void circuitStateChanged(String commandKey, String from, String to, float failureRate) {
		if (!FcEventBus.wants(FcEventBus.CIRCUIT_STATE_CHANGED))
			return;
		FcEventBus.publish(new CircuitStateChanged(System.currentTimeMillis(), node(), site, commandKey, from, to,
				failureRate));
	}

	public static void configReloaded(String file, boolean ok, int entries, List<String> reasons) {
		if (!FcEventBus.wants(FcEventBus.CONFIG_RELOADED))
			return;
		FcEventBus.publish(new ConfigReloaded(System.currentTimeMillis(), node(), site, file, ok, entries,
				(null == reasons) ? List.of() : reasons));
	}

	static void pluginStateChanged(String plugin, String state, String reason) {
		if (!FcEventBus.wants(FcEventBus.PLUGIN_STATE_CHANGED))
			return;
		FcEventBus.publish(new PluginStateChanged(System.currentTimeMillis(), node(), site, plugin, state, reason));
	}

	/** host + path + query, as the guard log line writes it, truncated. */
	static String uri(RequestContext context) {
		String uri = Objects.toString(context.getFrontCacheHost(), "") + Objects.toString(context.getRequestURI(), "")
				+ Objects.toString(context.getRequestQueryString(), "");
		return (uri.length() > FcEvent.MAX_URI_LENGTH) ? uri.substring(0, FcEvent.MAX_URI_LENGTH) : uri;
	}
}
