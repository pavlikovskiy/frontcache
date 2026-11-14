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
package org.frontcache.io;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One plugin as {@code get-node-stats} reports it: what it is (from its manifest), its state, and its counters.
 * The counters are the process's - a reload does not reset them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginStats {

	private String id;
	private String name;
	private String version;
	private String vendor;
	private String license;
	private int pluginApi;
	private List<String> kinds = new ArrayList<>();
	private boolean requestPath;
	private String state;
	private String reason;
	private boolean legacy;
	private long eventsDelivered;
	private long eventsDropped;
	private long errors;

	public PluginStats() { // for JSON mapper
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getVendor() {
		return vendor;
	}

	public void setVendor(String vendor) {
		this.vendor = vendor;
	}

	/** SPDX, as the plugin states it. */
	public String getLicense() {
		return license;
	}

	public void setLicense(String license) {
		this.license = license;
	}

	public int getPluginApi() {
		return pluginApi;
	}

	public void setPluginApi(int pluginApi) {
		this.pluginApi = pluginApi;
	}

	/** The declared kinds, as a manifest spells them: {@code observe}, {@code manage}, ... */
	public List<String> getKinds() {
		return kinds;
	}

	public void setKinds(List<String> kinds) {
		this.kinds = (null == kinds) ? new ArrayList<>() : kinds;
	}

	/** Whether a declared kind runs on the request path (decide / transform / replace) - shown as a badge. */
	public boolean isRequestPath() {
		return requestPath;
	}

	public void setRequestPath(boolean requestPath) {
		this.requestPath = requestPath;
	}

	/** {@code loaded}, {@code started}, {@code failed}, {@code refused} or {@code stopped}. */
	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	/** Why it failed or was refused; null otherwise. */
	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	/** A pre-2.13 {@code FrontcacheModule} without a manifest, run through the deprecated adapter. */
	public boolean isLegacy() {
		return legacy;
	}

	public void setLegacy(boolean legacy) {
		this.legacy = legacy;
	}

	public long getEventsDelivered() {
		return eventsDelivered;
	}

	public void setEventsDelivered(long eventsDelivered) {
		this.eventsDelivered = eventsDelivered;
	}

	public long getEventsDropped() {
		return eventsDropped;
	}

	public void setEventsDropped(long eventsDropped) {
		this.eventsDropped = eventsDropped;
	}

	public long getErrors() {
		return errors;
	}

	public void setErrors(long errors) {
		this.errors = errors;
	}
}
