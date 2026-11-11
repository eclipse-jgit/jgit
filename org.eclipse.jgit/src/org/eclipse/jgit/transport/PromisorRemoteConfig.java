/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_ADVERTISE;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PARTIAL_CLONE_FILTER;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PROMISOR;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_SEND_FIELDS;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_URL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_PROMISOR_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_REMOTE_SECTION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.HttpSupport;

/** Snapshot of the promisor remotes advertised by an upload-pack session. */
final class PromisorRemoteConfig {
	record Remote(String name, String url, @Nullable String partialCloneFilter) {
		@SuppressWarnings("nls")
		public String toWireString(boolean sendFilter) {
			String entry = "name=" + encode(name) + ",url=" + encode(url);
			String filter = partialCloneFilter;
			if (sendFilter && filter != null && !filter.isEmpty()) {
				entry += ",partialCloneFilter=" + encode(filter);
			}
			return entry;
		}
	}

	private final List<Remote> advertisedRemotes;
	private final boolean sendFilter;

	@SuppressWarnings("nls")
	PromisorRemoteConfig(Config config) {
		if (!config.getBoolean(CONFIG_PROMISOR_SECTION, null,
				CONFIG_KEY_ADVERTISE, false)) {
			advertisedRemotes = List.of();
			sendFilter = false;
			return;
		}

		boolean includeFilter = false;
		String fields = config.getString(CONFIG_PROMISOR_SECTION, null,
				CONFIG_KEY_SEND_FIELDS);
		if (fields != null) {
			for (String field : fields.split("[,\\s]+", -1)) {
				if (CONFIG_KEY_PARTIAL_CLONE_FILTER.equalsIgnoreCase(field)) {
					includeFilter = true;
					break;
				}
			}
		}
		sendFilter = includeFilter;

		List<Remote> remotes = new ArrayList<>();
		for (String name : config.getSubsections(CONFIG_REMOTE_SECTION)) {
			if (name.isEmpty() || name.startsWith("/") || name.indexOf('\0') >= 0) {
				continue;
			}
			if (!config.getBoolean(CONFIG_REMOTE_SECTION, name,
					CONFIG_KEY_PROMISOR, false)
					&& !config.getNames(CONFIG_REMOTE_SECTION, name, true)
							.contains(CONFIG_KEY_PARTIAL_CLONE_FILTER)) {
				continue;
			}
			String url = config.getString(CONFIG_REMOTE_SECTION, name,
					CONFIG_KEY_URL);
			if (url != null && !url.isEmpty()) {
				String filter = config.getString(CONFIG_REMOTE_SECTION, name,
						CONFIG_KEY_PARTIAL_CLONE_FILTER);
				remotes.add(new Remote(name, url, filter));
			}
		}
		advertisedRemotes = Collections.unmodifiableList(remotes);
	}

	List<Remote> getAdvertisedRemotes() {
		return advertisedRemotes;
	}

	@Nullable String getAdvertisement() {
		if (advertisedRemotes.isEmpty()) {
			return null;
		}
		StringJoiner result = new StringJoiner(";"); //$NON-NLS-1$
		for (Remote remote : advertisedRemotes) {
			result.add(remote.toWireString(sendFilter));
		}
		return result.toString();
	}

	private static String encode(String value) {
		// The protocol uses percent encoding, not form encoding: a plus sign
		// is literal, so spaces must be encoded as %20 rather than +.
		return HttpSupport.urlEncode(value, false)
				.replace("+", "%20"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
