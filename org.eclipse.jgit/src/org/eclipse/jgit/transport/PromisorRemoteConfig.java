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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_ADVERTISE;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PARTIAL_CLONE_FILTER;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PROMISOR;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_URL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_PROMISOR_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_REMOTE_SECTION;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.Config;

/** Snapshot of the promisor remotes advertised by an upload-pack session. */
final class PromisorRemoteConfig {
	record Remote(String name, String url) {
		public String toWireString() {
			return "name=" + encode(name) + ",url=" + encode(url); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private final List<Remote> advertisedRemotes;

	@SuppressWarnings("nls")
	PromisorRemoteConfig(Config config) {
		if (!config.getBoolean(CONFIG_PROMISOR_SECTION, null,
				CONFIG_KEY_ADVERTISE, false)) {
			advertisedRemotes = List.of();
			return;
		}

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
				remotes.add(new Remote(name, url));
			}
		}
		advertisedRemotes = List.copyOf(remotes);
	}

	List<Remote> getAdvertisedRemotes() {
		return advertisedRemotes;
	}

	public @Nullable String toWireString() {
		if (advertisedRemotes.isEmpty()) {
			return null;
		}
		StringJoiner result = new StringJoiner(";"); //$NON-NLS-1$
		for (Remote remote : advertisedRemotes) {
			result.add(remote.toWireString());
		}
		return result.toString();
	}

	private static String encode(String value) {
		StringBuilder result = new StringBuilder(value.length());
		for (byte b : value.getBytes(UTF_8)) {
			int octet = Byte.toUnsignedInt(b);
			if (octet >= 0x21 && octet <= 0x7e && octet != ',' && octet != ';'
					&& octet != '%') {
				result.append((char) octet);
			} else {
				result.append('%').append(Character.forDigit(octet >>> 4, 16))
						.append(Character.forDigit(octet & 0xf, 16));
			}
		}
		return result.toString();
	}
}
