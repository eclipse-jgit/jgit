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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.Config;

/** Snapshot of the promisor remotes advertised by an upload-pack session. */
final class PromisorRemoteConfig {
	record Remote(String name, String url) {
	}

	final List<Remote> advertisedRemotes;
	final Set<String> advertisedNames;
	final @Nullable String advertisement;

	@SuppressWarnings("nls")
	PromisorRemoteConfig(Config config) {
		if (!config.getBoolean("promisor", null, "advertise", false)) {
			advertisedRemotes = List.of();
			advertisedNames = Set.of();
			advertisement = null;
			return;
		}

		List<Remote> remotes = new ArrayList<>();
		Set<String> names = new HashSet<>();
		for (String name : config.getSubsections("remote")) {
			if (name.isEmpty() || name.startsWith("/") || name.indexOf('\0') >= 0) {
				continue;
			}
			if (!config.getBoolean("remote", name, "promisor", false)
					&& !config.getNames("remote", name, true)
							.contains("partialCloneFilter")) {
				continue;
			}
			String url = config.getString("remote", name, "url");
			if (url != null && !url.isEmpty()) {
				remotes.add(new Remote(name, url));
				names.add(name);
			}
		}
		advertisedRemotes = List.copyOf(remotes);
		advertisedNames = Set.copyOf(names);
		advertisement = remotes.isEmpty() ? null : serialize(advertisedRemotes);
	}

	private static String serialize(List<Remote> remotes) {
		StringJoiner result = new StringJoiner(";"); //$NON-NLS-1$
		for (Remote remote : remotes) {
			result.add("name=" + encode(remote.name()) + ",url=" //$NON-NLS-1$ //$NON-NLS-2$
					+ encode(remote.url()));
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
