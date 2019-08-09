/*
 * Copyright (C) 2008, 2017, Google Inc.
 * Copyright (C) 2017, 2018, Thomas Wolf <thomas.wolf@paranor.ch>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.internal.transport.ssh;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.fnmatch.FileNameMatcher;
import org.eclipse.jgit.transport.SshConstants;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;

/**
 * Fairly complete configuration parser for the openssh ~/.ssh/config file.
 * <p>
 * Both JSch 0.1.54 and Apache MINA sshd 2.1.0 have parsers for this, but both
 * are buggy. Therefore we implement our own parser to read an openssh
 * configuration file.
 * </p>
 * <p>
 * Limitations compared to the full openssh 7.5 parser:
 * </p>
 * <ul>
 * <li>This parser does not handle Match or Include keywords.
 * <li>This parser does not do host name canonicalization.
 * </ul>
 * <p>
 * Note that openssh's readconf.c is a validating parser; this parser does not
 * validate entries.
 * </p>
 * <p>
 * This config does %-substitutions for the following tokens:
 * </p>
 * <ul>
 * <li>%% - single %
 * <li>%C - short-hand for %l%h%p%r.
 * <li>%d - home directory path
 * <li>%h - remote host name
 * <li>%L - local host name without domain
 * <li>%l - FQDN of the local host
 * <li>%n - host name as specified in {@link #lookup(String, int, String)}
 * <li>%p - port number; if not given in {@link #lookup(String, int, String)}
 * replaced only if set in the config
 * <li>%r - remote user name; if not given in
 * {@link #lookup(String, int, String)} replaced only if set in the config
 * <li>%u - local user name
 * </ul>
 * <p>
 * %i is not handled; Java has no concept of a "user ID". %T is always replaced
 * by NONE.
 * </p>
 *
 * @see <a href="http://man.openbsd.org/OpenBSD-current/man5/ssh_config.5">man
 *      ssh-config</a>
 */
public class OpenSshConfigFile {

	/**
	 * "Host" name of the HostEntry for the default options before the first
	 * host block in a config file.
	 */
	private static final String DEFAULT_NAME = ""; //$NON-NLS-1$

	/** The user's home directory, as key files may be relative to here. */
	private final File home;

	/** The .ssh/config file we read and monitor for updates. */
	private final File configFile;

	/** User name of the user on the host OS. */
	private final String localUserName;

	/** Modification time of {@link #configFile} when it was last loaded. */
	private Instant lastModified;

	/**
	 * Encapsulates entries read out of the configuration file, and a cache of
	 * fully resolved entries created from that.
	 */
	private static class State {
		// Keyed by pattern; if a "Host" line has multiple patterns, we generate
		// duplicate HostEntry objects
		Map<String, HostEntry> entries = new LinkedHashMap<>();

		// Keyed by user@hostname:port
		Map<String, HostEntry> hosts = new HashMap<>();

		@Override
		@SuppressWarnings("nls")
		public String toString() {
			return "State [entries=" + entries + ", hosts=" + hosts + "]";
		}
	}

	/** State read from the config file, plus the cache. */
	private State state;

	/**
	 * Creates a new {@link OpenSshConfigFile} that will read the config from
	 * file {@code config} use the given file {@code home} as "home" directory.
	 *
	 * @param home
	 *            user's home directory for the purpose of ~ replacement
	 * @param config
	 *            file to load.
	 * @param localUserName
	 *            user name of the current user on the local host OS
	 */
	public OpenSshConfigFile(@NonNull File home, @NonNull File config,
			@NonNull String localUserName) {
		this.home = home;
		this.configFile = config;
		this.localUserName = localUserName;
		state = new State();
	}

	/**
	 * Locate the configuration for a specific host request.
	 *
	 * @param hostName
	 *            the name the user has supplied to the SSH tool. This may be a
	 *            real host name, or it may just be a "Host" block in the
	 *            configuration file.
	 * @param port
	 *            the user supplied; <= 0 if none
	 * @param userName
	 *            the user supplied, may be {@code null} or empty if none given
	 * @return r configuration for the requested name.
	 */
	@NonNull
	public HostEntry lookup(@NonNull String hostName, int port,
			String userName) {
		final State cache = refresh();
		String cacheKey = toCacheKey(hostName, port, userName);
		HostEntry h = cache.hosts.get(cacheKey);
		if (h != null) {
			return h;
		}
		HostEntry fullConfig = new HostEntry();
		// Initialize with default entries at the top of the file, before the
		// first Host block.
		fullConfig.merge(cache.entries.get(DEFAULT_NAME));
		for (Map.Entry<String, HostEntry> e : cache.entries.entrySet()) {
			String pattern = e.getKey();
			if (isHostMatch(pattern, hostName)) {
				fullConfig.merge(e.getValue());
			}
		}
		fullConfig.substitute(hostName, port, userName, localUserName, home);
		cache.hosts.put(cacheKey, fullConfig);
		return fullConfig;
	}

	@NonNull
	private String toCacheKey(@NonNull String hostName, int port,
			String userName) {
		String key = hostName;
		if (port > 0) {
			key = key + ':' + Integer.toString(port);
		}
		if (userName != null && !userName.isEmpty()) {
			key = userName + '@' + key;
		}
		return key;
	}

	private synchronized State refresh() {
		final Instant mtime = FS.DETECTED.lastModifiedInstant(configFile);
		if (!mtime.equals(lastModified)) {
			State newState = new State();
			try (BufferedReader br = Files
					.newBufferedReader(configFile.toPath(), UTF_8)) {
				newState.entries = parse(br);
			} catch (IOException | RuntimeException none) {
				// Ignore -- we'll set and return an empty state
			}
			lastModified = mtime;
			state = newState;
		}
		return state;
	}

	private Map<String, HostEntry> parse(BufferedReader reader)
			throws IOException {
		final Map<String, HostEntry> entries = new LinkedHashMap<>();
		final List<HostEntry> current = new ArrayList<>(4);
		String line;

		// The man page doesn't say so, but the openssh parser (readconf.c)
		// starts out in active mode and thus always applies any lines that
		// occur before the first host block. We gather those options in a
		// HostEntry for DEFAULT_NAME.
		HostEntry defaults = new HostEntry();
		current.add(defaults);
		entries.put(DEFAULT_NAME, defaults);

		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) { //$NON-NLS-1$
				continue;
			}
			String[] parts = line.split("[ \t]*[= \t]", 2); //$NON-NLS-1$
			// Although the ssh-config man page doesn't say so, the openssh
			// parser does allow quoted keywords.
			String keyword = dequote(parts[0].trim());
			// man 5 ssh-config says lines had the format "keyword arguments",
			// with no indication that arguments were optional. However, let's
			// not crap out on missing arguments. See bug 444319.
			String argValue = parts.length > 1 ? parts[1].trim() : ""; //$NON-NLS-1$

			if (StringUtils.equalsIgnoreCase(SshConstants.HOST, keyword)) {
				current.clear();
				for (String name : parseList(argValue)) {
					if (name == null || name.isEmpty()) {
						// null should not occur, but better be safe than sorry.
						continue;
					}
					HostEntry c = entries.get(name);
					if (c == null) {
						c = new HostEntry();
						entries.put(name, c);
					}
					current.add(c);
				}
				continue;
			}

			if (current.isEmpty()) {
				// We received an option outside of a Host block. We
				// don't know who this should match against, so skip.
				continue;
			}

			if (HostEntry.isListKey(keyword)) {
				List<String> args = validate(keyword, parseList(argValue));
				for (HostEntry entry : current) {
					entry.setValue(keyword, args);
				}
			} else if (!argValue.isEmpty()) {
				argValue = validate(keyword, dequote(argValue));
				for (HostEntry entry : current) {
					entry.setValue(keyword, argValue);
				}
			}
		}

		return entries;
	}

	/**
	 * Splits the argument into a list of whitespace-separated elements.
	 * Elements containing whitespace must be quoted and will be de-quoted.
	 *
	 * @param argument
	 *            argument part of the configuration line as read from the
	 *            config file
	 * @return a {@link List} of elements, possibly empty and possibly
	 *         containing empty elements, but not containing {@code null}
	 */
	private List<String> parseList(String argument) {
		List<String> result = new ArrayList<>(4);
		int start = 0;
		int length = argument.length();
		while (start < length) {
			// Skip whitespace
			if (Character.isSpaceChar(argument.charAt(start))) {
				start++;
				continue;
			}
			if (argument.charAt(start) == '"') {
				int stop = argument.indexOf('"', ++start);
				if (stop < start) {
					// No closing double quote: skip
					break;
				}
				result.add(argument.substring(start, stop));
				start = stop + 1;
			} else {
				int stop = start + 1;
				while (stop < length
						&& !Character.isSpaceChar(argument.charAt(stop))) {
					stop++;
				}
				result.add(argument.substring(start, stop));
				start = stop + 1;
			}
		}
		return result;
	}

	/**
	 * Hook to perform validation on a single value, or to sanitize it. If this
	 * throws an (unchecked) exception, parsing of the file is abandoned.
	 *
	 * @param key
	 *            of the entry
	 * @param value
	 *            as read from the config file
	 * @return the validated and possibly sanitized value
	 */
	protected String validate(String key, String value) {
		if (String.CASE_INSENSITIVE_ORDER.compare(key,
				SshConstants.PREFERRED_AUTHENTICATIONS) == 0) {
			return stripWhitespace(value);
		}
		return value;
	}

	/**
	 * Hook to perform validation on values, or to sanitize them. If this throws
	 * an (unchecked) exception, parsing of the file is abandoned.
	 *
	 * @param key
	 *            of the entry
	 * @param value
	 *            list of arguments as read from the config file
	 * @return a {@link List} of values, possibly empty and possibly containing
	 *         empty elements, but not containing {@code null}
	 */
	protected List<String> validate(String key, List<String> value) {
		return value;
	}

	private static boolean isHostMatch(String pattern, String name) {
		if (pattern.startsWith("!")) { //$NON-NLS-1$
			return !patternMatchesHost(pattern.substring(1), name);
		} else {
			return patternMatchesHost(pattern, name);
		}
	}

	private static boolean patternMatchesHost(String pattern, String name) {
		if (pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0) {
			final FileNameMatcher fn;
			try {
				fn = new FileNameMatcher(pattern, null);
			} catch (InvalidPatternException e) {
				return false;
			}
			fn.append(name);
			return fn.isMatch();
		} else {
			// Not a pattern but a full host name
			return pattern.equals(name);
		}
	}

	private static String dequote(String value) {
		if (value.startsWith("\"") && value.endsWith("\"") //$NON-NLS-1$ //$NON-NLS-2$
				&& value.length() > 1)
			return value.substring(1, value.length() - 1);
		return value;
	}

	private static String stripWhitespace(String value) {
		final StringBuilder b = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			if (!Character.isSpaceChar(value.charAt(i)))
				b.append(value.charAt(i));
		}
		return b.toString();
	}

	private static File toFile(String path, File home) {
		if (path.startsWith("~/") || path.startsWith("~" + File.separator)) { //$NON-NLS-1$ //$NON-NLS-2$
			return new File(home, path.substring(2));
		}
		File ret = new File(path);
		if (ret.isAbsolute()) {
			return ret;
		}
		return new File(home, path);
	}

	/**
	 * Converts a positive value into an {@code int}.
	 *
	 * @param value
	 *            to convert
	 * @return the value, or -1 if it wasn't a positive integral value
	 */
	public static int positive(String value) {
		if (value != null) {
			try {
				return Integer.parseUnsignedInt(value);
			} catch (NumberFormatException e) {
				// Ignore
			}
		}
		return -1;
	}

	/**
	 * Converts a ssh config flag value (yes/true/on - no/false/off) into an
	 * {@code boolean}.
	 *
	 * @param value
	 *            to convert
	 * @return {@code true} if {@code value} is "yes", "on", or "true";
	 *         {@code false} otherwise
	 */
	public static boolean flag(String value) {
		if (value == null) {
			return false;
		}
		return SshConstants.YES.equals(value) || SshConstants.ON.equals(value)
				|| SshConstants.TRUE.equals(value);
	}

	/**
	 * Retrieves the local user name as given in the constructor.
	 *
	 * @return the user name
	 */
	public String getLocalUserName() {
		return localUserName;
	}

	/**
	 * A host entry from the ssh config file. Any merging of global values and
	 * of several matching host entries, %-substitutions, and ~ replacement have
	 * all been done.
	 */
	public static class HostEntry {

		/**
		 * Keys that can be specified multiple times, building up a list. (I.e.,
		 * those are the keys that do not follow the general rule of "first
		 * occurrence wins".)
		 */
		private static final Set<String> MULTI_KEYS = new TreeSet<>(
				String.CASE_INSENSITIVE_ORDER);

		static {
			MULTI_KEYS.add(SshConstants.CERTIFICATE_FILE);
			MULTI_KEYS.add(SshConstants.IDENTITY_FILE);
			MULTI_KEYS.add(SshConstants.LOCAL_FORWARD);
			MULTI_KEYS.add(SshConstants.REMOTE_FORWARD);
			MULTI_KEYS.add(SshConstants.SEND_ENV);
		}

		/**
		 * Keys that take a whitespace-separated list of elements as argument.
		 * Because the dequote-handling is different, we must handle those in
		 * the parser. There are a few other keys that take comma-separated
		 * lists as arguments, but for the parser those are single arguments
		 * that must be quoted if they contain whitespace, and taking them apart
		 * is the responsibility of the user of those keys.
		 */
		private static final Set<String> LIST_KEYS = new TreeSet<>(
				String.CASE_INSENSITIVE_ORDER);

		static {
			LIST_KEYS.add(SshConstants.CANONICAL_DOMAINS);
			LIST_KEYS.add(SshConstants.GLOBAL_KNOWN_HOSTS_FILE);
			LIST_KEYS.add(SshConstants.SEND_ENV);
			LIST_KEYS.add(SshConstants.USER_KNOWN_HOSTS_FILE);
		}

		private Map<String, String> options;

		private Map<String, List<String>> multiOptions;

		private Map<String, List<String>> listOptions;

		/**
		 * Retrieves the value of a single-valued key, or the first is the key
		 * has multiple values. Keys are case-insensitive, so
		 * {@code getValue("HostName") == getValue("HOSTNAME")}.
		 *
		 * @param key
		 *            to get the value of
		 * @return the value, or {@code null} if none
		 */
		public String getValue(String key) {
			String result = options != null ? options.get(key) : null;
			if (result == null) {
				// Let's be lenient and return at least the first value from
				// a list-valued or multi-valued key.
				List<String> values = listOptions != null ? listOptions.get(key)
						: null;
				if (values == null) {
					values = multiOptions != null ? multiOptions.get(key)
							: null;
				}
				if (values != null && !values.isEmpty()) {
					result = values.get(0);
				}
			}
			return result;
		}

		/**
		 * Retrieves the values of a multi or list-valued key. Keys are
		 * case-insensitive, so
		 * {@code getValue("HostName") == getValue("HOSTNAME")}.
		 *
		 * @param key
		 *            to get the values of
		 * @return a possibly empty list of values
		 */
		public List<String> getValues(String key) {
			List<String> values = listOptions != null ? listOptions.get(key)
					: null;
			if (values == null) {
				values = multiOptions != null ? multiOptions.get(key) : null;
			}
			if (values == null || values.isEmpty()) {
				return new ArrayList<>();
			}
			return new ArrayList<>(values);
		}

		/**
		 * Sets the value of a single-valued key if it not set yet, or adds a
		 * value to a multi-valued key. If the value is {@code null}, the key is
		 * removed altogether, whether it is single-, list-, or multi-valued.
		 *
		 * @param key
		 *            to modify
		 * @param value
		 *            to set or add
		 */
		public void setValue(String key, String value) {
			if (value == null) {
				if (multiOptions != null) {
					multiOptions.remove(key);
				}
				if (listOptions != null) {
					listOptions.remove(key);
				}
				if (options != null) {
					options.remove(key);
				}
				return;
			}
			if (MULTI_KEYS.contains(key)) {
				if (multiOptions == null) {
					multiOptions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				}
				List<String> values = multiOptions.get(key);
				if (values == null) {
					values = new ArrayList<>(4);
					multiOptions.put(key, values);
				}
				values.add(value);
			} else {
				if (options == null) {
					options = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				}
				if (!options.containsKey(key)) {
					options.put(key, value);
				}
			}
		}

		/**
		 * Sets the values of a multi- or list-valued key.
		 *
		 * @param key
		 *            to set
		 * @param values
		 *            a non-empty list of values
		 */
		public void setValue(String key, List<String> values) {
			if (values.isEmpty()) {
				return;
			}
			// Check multi-valued keys first; because of the replacement
			// strategy, they must take precedence over list-valued keys
			// which always follow the "first occurrence wins" strategy.
			//
			// Note that SendEnv is a multi-valued list-valued key. (It's
			// rather immaterial for JGit, though.)
			if (MULTI_KEYS.contains(key)) {
				if (multiOptions == null) {
					multiOptions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				}
				List<String> items = multiOptions.get(key);
				if (items == null) {
					items = new ArrayList<>(values);
					multiOptions.put(key, items);
				} else {
					items.addAll(values);
				}
			} else {
				if (listOptions == null) {
					listOptions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				}
				if (!listOptions.containsKey(key)) {
					listOptions.put(key, values);
				}
			}
		}

		/**
		 * Does the key take a whitespace-separated list of values?
		 *
		 * @param key
		 *            to check
		 * @return {@code true} if the key is a list-valued key.
		 */
		public static boolean isListKey(String key) {
			return LIST_KEYS.contains(key.toUpperCase(Locale.ROOT));
		}

		void merge(HostEntry entry) {
			if (entry == null) {
				// Can occur if we could not read the config file
				return;
			}
			if (entry.options != null) {
				if (options == null) {
					options = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				}
				for (Map.Entry<String, String> item : entry.options
						.entrySet()) {
					if (!options.containsKey(item.getKey())) {
						options.put(item.getKey(), item.getValue());
					}
				}
			}
			if (entry.listOptions != null) {
				if (listOptions == null) {
					listOptions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				}
				for (Map.Entry<String, List<String>> item : entry.listOptions
						.entrySet()) {
					if (!listOptions.containsKey(item.getKey())) {
						listOptions.put(item.getKey(), item.getValue());
					}
				}

			}
			if (entry.multiOptions != null) {
				if (multiOptions == null) {
					multiOptions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				}
				for (Map.Entry<String, List<String>> item : entry.multiOptions
						.entrySet()) {
					List<String> values = multiOptions.get(item.getKey());
					if (values == null) {
						values = new ArrayList<>(item.getValue());
						multiOptions.put(item.getKey(), values);
					} else {
						values.addAll(item.getValue());
					}
				}
			}
		}

		private List<String> substitute(List<String> values, String allowed,
				Replacer r) {
			List<String> result = new ArrayList<>(values.size());
			for (String value : values) {
				result.add(r.substitute(value, allowed));
			}
			return result;
		}

		private List<String> replaceTilde(List<String> values, File home) {
			List<String> result = new ArrayList<>(values.size());
			for (String value : values) {
				result.add(toFile(value, home).getPath());
			}
			return result;
		}

		void substitute(String originalHostName, int port, String userName,
				String localUserName, File home) {
			int p = port >= 0 ? port : positive(getValue(SshConstants.PORT));
			if (p < 0) {
				p = SshConstants.SSH_DEFAULT_PORT;
			}
			String u = userName != null && !userName.isEmpty() ? userName
					: getValue(SshConstants.USER);
			if (u == null || u.isEmpty()) {
				u = localUserName;
			}
			Replacer r = new Replacer(originalHostName, p, u, localUserName,
					home);
			if (options != null) {
				// HOSTNAME first
				String hostName = options.get(SshConstants.HOST_NAME);
				if (hostName == null || hostName.isEmpty()) {
					options.put(SshConstants.HOST_NAME, originalHostName);
				} else {
					hostName = r.substitute(hostName, "h"); //$NON-NLS-1$
					options.put(SshConstants.HOST_NAME, hostName);
					r.update('h', hostName);
				}
			}
			if (multiOptions != null) {
				List<String> values = multiOptions
						.get(SshConstants.IDENTITY_FILE);
				if (values != null) {
					values = substitute(values, "dhlru", r); //$NON-NLS-1$
					values = replaceTilde(values, home);
					multiOptions.put(SshConstants.IDENTITY_FILE, values);
				}
				values = multiOptions.get(SshConstants.CERTIFICATE_FILE);
				if (values != null) {
					values = substitute(values, "dhlru", r); //$NON-NLS-1$
					values = replaceTilde(values, home);
					multiOptions.put(SshConstants.CERTIFICATE_FILE, values);
				}
			}
			if (listOptions != null) {
				List<String> values = listOptions
						.get(SshConstants.USER_KNOWN_HOSTS_FILE);
				if (values != null) {
					values = replaceTilde(values, home);
					listOptions.put(SshConstants.USER_KNOWN_HOSTS_FILE, values);
				}
			}
			if (options != null) {
				// HOSTNAME already done above
				String value = options.get(SshConstants.IDENTITY_AGENT);
				if (value != null) {
					value = r.substitute(value, "dhlru"); //$NON-NLS-1$
					value = toFile(value, home).getPath();
					options.put(SshConstants.IDENTITY_AGENT, value);
				}
				value = options.get(SshConstants.CONTROL_PATH);
				if (value != null) {
					value = r.substitute(value, "ChLlnpru"); //$NON-NLS-1$
					value = toFile(value, home).getPath();
					options.put(SshConstants.CONTROL_PATH, value);
				}
				value = options.get(SshConstants.LOCAL_COMMAND);
				if (value != null) {
					value = r.substitute(value, "CdhlnprTu"); //$NON-NLS-1$
					options.put(SshConstants.LOCAL_COMMAND, value);
				}
				value = options.get(SshConstants.REMOTE_COMMAND);
				if (value != null) {
					value = r.substitute(value, "Cdhlnpru"); //$NON-NLS-1$
					options.put(SshConstants.REMOTE_COMMAND, value);
				}
				value = options.get(SshConstants.PROXY_COMMAND);
				if (value != null) {
					value = r.substitute(value, "hpr"); //$NON-NLS-1$
					options.put(SshConstants.PROXY_COMMAND, value);
				}
			}
			// Match is not implemented and would need to be done elsewhere
			// anyway.
		}

		/**
		 * Retrieves an unmodifiable map of all single-valued options, with
		 * case-insensitive lookup by keys.
		 *
		 * @return all single-valued options
		 */
		@NonNull
		public Map<String, String> getOptions() {
			if (options == null) {
				return Collections.emptyMap();
			}
			return Collections.unmodifiableMap(options);
		}

		/**
		 * Retrieves an unmodifiable map of all multi-valued options, with
		 * case-insensitive lookup by keys.
		 *
		 * @return all multi-valued options
		 */
		@NonNull
		public Map<String, List<String>> getMultiValuedOptions() {
			if (listOptions == null && multiOptions == null) {
				return Collections.emptyMap();
			}
			Map<String, List<String>> allValues = new TreeMap<>(
					String.CASE_INSENSITIVE_ORDER);
			if (multiOptions != null) {
				allValues.putAll(multiOptions);
			}
			if (listOptions != null) {
				allValues.putAll(listOptions);
			}
			return Collections.unmodifiableMap(allValues);
		}

		@Override
		@SuppressWarnings("nls")
		public String toString() {
			return "HostEntry [options=" + options + ", multiOptions="
					+ multiOptions + ", listOptions=" + listOptions + "]";
		}
	}

	private static class Replacer {
		private final Map<Character, String> replacements = new HashMap<>();

		public Replacer(String host, int port, String user,
				String localUserName, File home) {
			replacements.put(Character.valueOf('%'), "%"); //$NON-NLS-1$
			replacements.put(Character.valueOf('d'), home.getPath());
			replacements.put(Character.valueOf('h'), host);
			String localhost = SystemReader.getInstance().getHostname();
			replacements.put(Character.valueOf('l'), localhost);
			int period = localhost.indexOf('.');
			if (period > 0) {
				localhost = localhost.substring(0, period);
			}
			replacements.put(Character.valueOf('L'), localhost);
			replacements.put(Character.valueOf('n'), host);
			replacements.put(Character.valueOf('p'), Integer.toString(port));
			replacements.put(Character.valueOf('r'), user == null ? "" : user); //$NON-NLS-1$
			replacements.put(Character.valueOf('u'), localUserName);
			replacements.put(Character.valueOf('C'),
					substitute("%l%h%p%r", "hlpr")); //$NON-NLS-1$ //$NON-NLS-2$
			replacements.put(Character.valueOf('T'), "NONE"); //$NON-NLS-1$
		}

		public void update(char key, String value) {
			replacements.put(Character.valueOf(key), value);
			if ("lhpr".indexOf(key) >= 0) { //$NON-NLS-1$
				replacements.put(Character.valueOf('C'),
						substitute("%l%h%p%r", "hlpr")); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		public String substitute(String input, String allowed) {
			if (input == null || input.length() <= 1
					|| input.indexOf('%') < 0) {
				return input;
			}
			StringBuilder builder = new StringBuilder();
			int start = 0;
			int length = input.length();
			while (start < length) {
				int percent = input.indexOf('%', start);
				if (percent < 0 || percent + 1 >= length) {
					builder.append(input.substring(start));
					break;
				}
				String replacement = null;
				char ch = input.charAt(percent + 1);
				if (ch == '%' || allowed.indexOf(ch) >= 0) {
					replacement = replacements.get(Character.valueOf(ch));
				}
				if (replacement == null) {
					builder.append(input.substring(start, percent + 2));
				} else {
					builder.append(input.substring(start, percent))
							.append(replacement);
				}
				start = percent + 2;
			}
			return builder.toString();
		}
	}

	/** {@inheritDoc} */
	@Override
	@SuppressWarnings("nls")
	public String toString() {
		return "OpenSshConfig [home=" + home + ", configFile=" + configFile
				+ ", lastModified=" + lastModified + ", state=" + state + "]";
	}
}
