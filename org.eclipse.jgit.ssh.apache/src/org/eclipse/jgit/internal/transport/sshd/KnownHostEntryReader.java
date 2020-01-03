/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.text.MessageFormat.format;
import static org.apache.sshd.client.config.hosts.HostPatternsHolder.NON_STANDARD_PORT_PATTERN_ENCLOSURE_END_DELIM;
import static org.apache.sshd.client.config.hosts.HostPatternsHolder.NON_STANDARD_PORT_PATTERN_ENCLOSURE_START_DELIM;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.sshd.client.config.hosts.HostPatternValue;
import org.apache.sshd.client.config.hosts.HostPatternsHolder;
import org.apache.sshd.client.config.hosts.KnownHostEntry;
import org.apache.sshd.client.config.hosts.KnownHostHashValue;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Apache MINA sshd 2.0.0 KnownHostEntry cannot read a host entry line like
 * "host:port ssh-rsa <key>"; it complains about an illegal character in the
 * host name (correct would be "[host]:port"). The default known_hosts reader
 * also aborts reading on the first error.
 * <p>
 * This reader is a bit more robust and tries to handle this case if there is
 * only one colon (otherwise it might be an IPv6 address (without port)), and it
 * skips and logs invalid entries, but still returns all other valid entries
 * from the file.
 * </p>
 */
public class KnownHostEntryReader {

	private static final Logger LOG = LoggerFactory
			.getLogger(KnownHostEntryReader.class);

	private KnownHostEntryReader() {
		// No instantiation
	}

	/**
	 * Reads a known_hosts file and returns all valid entries. Invalid entries
	 * are skipped (and a message is logged).
	 *
	 * @param path
	 *            of the file to read
	 * @return a {@link List} of all valid entries read from the file
	 * @throws IOException
	 *             if the file cannot be read.
	 */
	public static List<KnownHostEntry> readFromFile(Path path)
			throws IOException {
		List<KnownHostEntry> result = new LinkedList<>();
		try (BufferedReader r = Files.newBufferedReader(path, UTF_8)) {
			r.lines().forEachOrdered(l -> {
				if (l == null) {
					return;
				}
				String line = clean(l);
				if (line.isEmpty()) {
					return;
				}
				try {
					KnownHostEntry entry = parseHostEntry(line);
					if (entry != null) {
						result.add(entry);
					} else {
						LOG.warn(format(SshdText.get().knownHostsInvalidLine,
								path, line));
					}
				} catch (RuntimeException e) {
					LOG.warn(format(SshdText.get().knownHostsInvalidLine, path,
							line), e);
				}
			});
		}
		return result;
	}

	private static String clean(String line) {
		int i = line.indexOf('#');
		return i < 0 ? line.trim() : line.substring(0, i).trim();
	}

	private static KnownHostEntry parseHostEntry(String line) {
		KnownHostEntry entry = new KnownHostEntry();
		entry.setConfigLine(line);
		String tmp = line;
		int i = 0;
		if (tmp.charAt(0) == KnownHostEntry.MARKER_INDICATOR) {
			// A marker
			i = tmp.indexOf(' ', 1);
			if (i < 0) {
				return null;
			}
			entry.setMarker(tmp.substring(1, i));
			tmp = tmp.substring(i + 1).trim();
		}
		i = tmp.indexOf(' ');
		if (i < 0) {
			return null;
		}
		// Hash, or host patterns
		if (tmp.charAt(0) == KnownHostHashValue.HASHED_HOST_DELIMITER) {
			// Hashed host entry
			KnownHostHashValue hash = KnownHostHashValue
					.parse(tmp.substring(0, i));
			if (hash == null) {
				return null;
			}
			entry.setHashedEntry(hash);
			entry.setPatterns(null);
		} else {
			Collection<HostPatternValue> patterns = parsePatterns(
					tmp.substring(0, i));
			if (patterns == null || patterns.isEmpty()) {
				return null;
			}
			entry.setHashedEntry(null);
			entry.setPatterns(patterns);
		}
		tmp = tmp.substring(i + 1).trim();
		AuthorizedKeyEntry key = AuthorizedKeyEntry
				.parseAuthorizedKeyEntry(tmp);
		if (key == null) {
			return null;
		}
		entry.setKeyEntry(key);
		return entry;
	}

	private static Collection<HostPatternValue> parsePatterns(String text) {
		if (text.isEmpty()) {
			return null;
		}
		List<String> items = Arrays.stream(text.split(",")) //$NON-NLS-1$
				.filter(item -> item != null && !item.isEmpty()).map(item -> {
					if (NON_STANDARD_PORT_PATTERN_ENCLOSURE_START_DELIM == item
							.charAt(0)) {
						return item;
					}
					int firstColon = item.indexOf(':');
					if (firstColon < 0) {
						return item;
					}
					int secondColon = item.indexOf(':', firstColon + 1);
					if (secondColon > 0) {
						// Assume an IPv6 address (without port).
						return item;
					}
					// We have "host:port", should be "[host]:port"
					return NON_STANDARD_PORT_PATTERN_ENCLOSURE_START_DELIM
							+ item.substring(0, firstColon)
							+ NON_STANDARD_PORT_PATTERN_ENCLOSURE_END_DELIM
							+ item.substring(firstColon);
				}).collect(Collectors.toList());
		return items.isEmpty() ? null : HostPatternsHolder.parsePatterns(items);
	}
}
