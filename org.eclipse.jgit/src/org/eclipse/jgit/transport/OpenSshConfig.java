/*
 * Copyright (C) 2008, 2017, Google Inc.
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

package org.eclipse.jgit.transport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.fnmatch.FileNameMatcher;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;

import com.jcraft.jsch.ConfigRepository;

/**
 * Fairly complete configuration parser for the OpenSSH ~/.ssh/config file.
 * <p>
 * JSch does have its own config file parser
 * {@link com.jcraft.jsch.OpenSSHConfig} since version 0.1.50, but it has a
 * number of problems:
 * <ul>
 * <li>it splits lines of the format "keyword = value" wrongly: you'd end up
 * with the value "= value".
 * <li>its "Host" keyword is not case insensitive.
 * <li>it doesn't handle quoted values.
 * <li>JSch's OpenSSHConfig doesn't monitor for config file changes.
 * </ul>
 * <p>
 * Therefore implement our own parser to read an OpenSSH configuration file. It
 * makes the critical options available to
 * {@link org.eclipse.jgit.transport.SshSessionFactory} via
 * {@link org.eclipse.jgit.transport.OpenSshConfig.Host} objects returned by
 * {@link #lookup(String)}, and implements a fully conforming
 * {@link com.jcraft.jsch.ConfigRepository} providing
 * {@link com.jcraft.jsch.ConfigRepository.Config}s via
 * {@link #getConfig(String)}.
 * </p>
 * <p>
 * Limitations compared to the full OpenSSH 7.5 parser:
 * </p>
 * <ul>
 * <li>This parser does not handle Match or Include keywords.
 * <li>This parser does not do host name canonicalization (Jsch ignores it
 * anyway).
 * </ul>
 * <p>
 * Note that OpenSSH's readconf.c is a validating parser; Jsch's
 * ConfigRepository OTOH treats all option values as plain strings, so any
 * validation must happen in Jsch outside of the parser. Thus this parser does
 * not validate option values, except for a few options when constructing a
 * {@link org.eclipse.jgit.transport.OpenSshConfig.Host} object.
 * </p>
 * <p>
 * This config does %-substitutions for the following tokens:
 * </p>
 * <ul>
 * <li>%% - single %
 * <li>%C - short-hand for %l%h%p%r. See %p and %r below; the replacement may be
 * done partially only and may leave %p or %r or both unreplaced.
 * <li>%d - home directory path
 * <li>%h - remote host name
 * <li>%L - local host name without domain
 * <li>%l - FQDN of the local host
 * <li>%n - host name as specified in {@link #lookup(String)}
 * <li>%p - port number; replaced only if set in the config
 * <li>%r - remote user name; replaced only if set in the config
 * <li>%u - local user name
 * </ul>
 * <p>
 * If the config doesn't set the port or the remote user name, %p and %r remain
 * un-substituted. It's the caller's responsibility to replace them with values
 * obtained from the connection URI. %i is not handled; Java has no concept of a
 * "user ID".
 * </p>
 */
public class OpenSshConfig implements ConfigRepository {

	/** IANA assigned port number for SSH. */
	static final int SSH_PORT = 22;

	/**
	 * Obtain the user's configuration data.
	 * <p>
	 * The configuration file is always returned to the caller, even if no file
	 * exists in the user's home directory at the time the call was made. Lookup
	 * requests are cached and are automatically updated if the user modifies
	 * the configuration file since the last time it was cached.
	 *
	 * @param fs
	 *            the file system abstraction which will be necessary to
	 *            perform certain file system operations.
	 * @return a caching reader of the user's configuration file.
	 */
	public static OpenSshConfig get(FS fs) {
		File home = fs.userHome();
		if (home == null)
			home = new File(".").getAbsoluteFile(); //$NON-NLS-1$

		final File config = new File(new File(home, ".ssh"), Constants.CONFIG); //$NON-NLS-1$
		final OpenSshConfig osc = new OpenSshConfig(home, config);
		osc.refresh();
		return osc;
	}

	/** The user's home directory, as key files may be relative to here. */
	private final File home;

	/** The .ssh/config file we read and monitor for updates. */
	private final File configFile;

	/** Modification time of {@link #configFile} when it was last loaded. */
	private long lastModified;

	/**
	 * Encapsulates entries read out of the configuration file, and
	 * {@link Host}s created from that.
	 */
	private static class State {
		Map<String, HostEntry> entries = new LinkedHashMap<>();
		Map<String, Host> hosts = new HashMap<>();

		@Override
		@SuppressWarnings("nls")
		public String toString() {
			return "State [entries=" + entries + ", hosts=" + hosts + "]";
		}
	}

	/** State read from the config file, plus {@link Host}s created from it. */
	private State state;

	OpenSshConfig(final File h, final File cfg) {
		home = h;
		configFile = cfg;
		state = new State();
	}

	/**
	 * Locate the configuration for a specific host request.
	 *
	 * @param hostName
	 *            the name the user has supplied to the SSH tool. This may be a
	 *            real host name, or it may just be a "Host" block in the
	 *            configuration file.
	 * @return r configuration for the requested name. Never null.
	 */
	public Host lookup(final String hostName) {
		final State cache = refresh();
		Host h = cache.hosts.get(hostName);
		if (h != null) {
			return h;
		}
		HostEntry fullConfig = new HostEntry();
		// Initialize with default entries at the top of the file, before the
		// first Host block.
		fullConfig.merge(cache.entries.get(HostEntry.DEFAULT_NAME));
		for (final Map.Entry<String, HostEntry> e : cache.entries.entrySet()) {
			String key = e.getKey();
			if (isHostMatch(key, hostName)) {
				fullConfig.merge(e.getValue());
			}
		}
		fullConfig.substitute(hostName, home);
		h = new Host(fullConfig, hostName, home);
		cache.hosts.put(hostName, h);
		return h;
	}

	private synchronized State refresh() {
		final long mtime = configFile.lastModified();
		if (mtime != lastModified) {
			State newState = new State();
			try (FileInputStream in = new FileInputStream(configFile)) {
				newState.entries = parse(in);
			} catch (IOException none) {
				// Ignore -- we'll set and return an empty state
			}
			lastModified = mtime;
			state = newState;
		}
		return state;
	}

	private Map<String, HostEntry> parse(final InputStream in)
			throws IOException {
		final Map<String, HostEntry> m = new LinkedHashMap<>();
		final BufferedReader br = new BufferedReader(new InputStreamReader(in));
		final List<HostEntry> current = new ArrayList<>(4);
		String line;

		// The man page doesn't say so, but the OpenSSH parser (readconf.c)
		// starts out in active mode and thus always applies any lines that
		// occur before the first host block. We gather those options in a
		// HostEntry for DEFAULT_NAME.
		HostEntry defaults = new HostEntry();
		current.add(defaults);
		m.put(HostEntry.DEFAULT_NAME, defaults);

		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) { //$NON-NLS-1$
				continue;
			}
			String[] parts = line.split("[ \t]*[= \t]", 2); //$NON-NLS-1$
			// Although the ssh-config man page doesn't say so, the OpenSSH
			// parser does allow quoted keywords.
			String keyword = dequote(parts[0].trim());
			// man 5 ssh-config says lines had the format "keyword arguments",
			// with no indication that arguments were optional. However, let's
			// not crap out on missing arguments. See bug 444319.
			String argValue = parts.length > 1 ? parts[1].trim() : ""; //$NON-NLS-1$

			if (StringUtils.equalsIgnoreCase("Host", keyword)) { //$NON-NLS-1$
				current.clear();
				for (String name : HostEntry.parseList(argValue)) {
					if (name == null || name.isEmpty()) {
						// null should not occur, but better be safe than sorry.
						continue;
					}
					HostEntry c = m.get(name);
					if (c == null) {
						c = new HostEntry();
						m.put(name, c);
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
				List<String> args = HostEntry.parseList(argValue);
				for (HostEntry entry : current) {
					entry.setValue(keyword, args);
				}
			} else if (!argValue.isEmpty()) {
				argValue = dequote(argValue);
				for (HostEntry entry : current) {
					entry.setValue(keyword, argValue);
				}
			}
		}

		return m;
	}

	private static boolean isHostMatch(final String pattern,
			final String name) {
		if (pattern.startsWith("!")) { //$NON-NLS-1$
			return !patternMatchesHost(pattern.substring(1), name);
		} else {
			return patternMatchesHost(pattern, name);
		}
	}

	private static boolean patternMatchesHost(final String pattern,
			final String name) {
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

	private static String dequote(final String value) {
		if (value.startsWith("\"") && value.endsWith("\"") //$NON-NLS-1$ //$NON-NLS-2$
				&& value.length() > 1)
			return value.substring(1, value.length() - 1);
		return value;
	}

	private static String nows(final String value) {
		final StringBuilder b = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			if (!Character.isSpaceChar(value.charAt(i)))
				b.append(value.charAt(i));
		}
		return b.toString();
	}

	private static Boolean yesno(final String value) {
		if (StringUtils.equalsIgnoreCase("yes", value)) //$NON-NLS-1$
			return Boolean.TRUE;
		return Boolean.FALSE;
	}

	private static File toFile(String path, File home) {
		if (path.startsWith("~/")) { //$NON-NLS-1$
			return new File(home, path.substring(2));
		}
		File ret = new File(path);
		if (ret.isAbsolute()) {
			return ret;
		}
		return new File(home, path);
	}

	private static int positive(final String value) {
		if (value != null) {
			try {
				return Integer.parseUnsignedInt(value);
			} catch (NumberFormatException e) {
				// Ignore
			}
		}
		return -1;
	}

	static String userName() {
		return AccessController.doPrivileged(new PrivilegedAction<String>() {
			@Override
			public String run() {
				return SystemReader.getInstance()
						.getProperty(Constants.OS_USER_NAME_KEY);
			}
		});
	}

	private static class HostEntry implements ConfigRepository.Config {

		/**
		 * "Host name" of the HostEntry for the default options before the first
		 * host block in a config file.
		 */
		public static final String DEFAULT_NAME = ""; //$NON-NLS-1$

		// See com.jcraft.jsch.OpenSSHConfig. Translates some command-line keys
		// to ssh-config keys.
		private static final Map<String, String> KEY_MAP = new HashMap<>();

		static {
			KEY_MAP.put("kex", "KexAlgorithms"); //$NON-NLS-1$//$NON-NLS-2$
			KEY_MAP.put("server_host_key", "HostKeyAlgorithms"); //$NON-NLS-1$ //$NON-NLS-2$
			KEY_MAP.put("cipher.c2s", "Ciphers"); //$NON-NLS-1$ //$NON-NLS-2$
			KEY_MAP.put("cipher.s2c", "Ciphers"); //$NON-NLS-1$ //$NON-NLS-2$
			KEY_MAP.put("mac.c2s", "Macs"); //$NON-NLS-1$ //$NON-NLS-2$
			KEY_MAP.put("mac.s2c", "Macs"); //$NON-NLS-1$ //$NON-NLS-2$
			KEY_MAP.put("compression.s2c", "Compression"); //$NON-NLS-1$ //$NON-NLS-2$
			KEY_MAP.put("compression.c2s", "Compression"); //$NON-NLS-1$ //$NON-NLS-2$
			KEY_MAP.put("compression_level", "CompressionLevel"); //$NON-NLS-1$ //$NON-NLS-2$
			KEY_MAP.put("MaxAuthTries", "NumberOfPasswordPrompts"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		/**
		 * Keys that can be specified multiple times, building up a list. (I.e.,
		 * those are the keys that do not follow the general rule of "first
		 * occurrence wins".)
		 */
		private static final Set<String> MULTI_KEYS = new HashSet<>();

		static {
			MULTI_KEYS.add("CERTIFICATEFILE"); //$NON-NLS-1$
			MULTI_KEYS.add("IDENTITYFILE"); //$NON-NLS-1$
			MULTI_KEYS.add("LOCALFORWARD"); //$NON-NLS-1$
			MULTI_KEYS.add("REMOTEFORWARD"); //$NON-NLS-1$
			MULTI_KEYS.add("SENDENV"); //$NON-NLS-1$
		}

		/**
		 * Keys that take a whitespace-separated list of elements as argument.
		 * Because the dequote-handling is different, we must handle those in
		 * the parser. There are a few other keys that take comma-separated
		 * lists as arguments, but for the parser those are single arguments
		 * that must be quoted if they contain whitespace, and taking them apart
		 * is the responsibility of the user of those keys.
		 */
		private static final Set<String> LIST_KEYS = new HashSet<>();

		static {
			LIST_KEYS.add("CANONICALDOMAINS"); //$NON-NLS-1$
			LIST_KEYS.add("GLOBALKNOWNHOSTSFILE"); //$NON-NLS-1$
			LIST_KEYS.add("SENDENV"); //$NON-NLS-1$
			LIST_KEYS.add("USERKNOWNHOSTSFILE"); //$NON-NLS-1$
		}

		private Map<String, String> options;

		private Map<String, List<String>> multiOptions;

		private Map<String, List<String>> listOptions;

		@Override
		public String getHostname() {
			return getValue("HOSTNAME"); //$NON-NLS-1$
		}

		@Override
		public String getUser() {
			return getValue("USER"); //$NON-NLS-1$
		}

		@Override
		public int getPort() {
			return positive(getValue("PORT")); //$NON-NLS-1$
		}

		private static String mapKey(String key) {
			String k = KEY_MAP.get(key);
			if (k == null) {
				k = key;
			}
			return k.toUpperCase(Locale.ROOT);
		}

		private String findValue(String key) {
			String k = mapKey(key);
			String result = options != null ? options.get(k) : null;
			if (result == null) {
				// Also check the list and multi options. Modern OpenSSH treats
				// UserKnownHostsFile and GlobalKnownHostsFile as list-valued,
				// and so does this parser. Jsch 0.1.54 in general doesn't know
				// about list-valued options (it _does_ know multi-valued
				// options, though), and will ask for a single value for such
				// options.
				//
				// Let's be lenient and return at least the first value from
				// a list-valued or multi-valued key for which Jsch asks for a
				// single value.
				List<String> values = listOptions != null ? listOptions.get(k)
						: null;
				if (values == null) {
					values = multiOptions != null ? multiOptions.get(k) : null;
				}
				if (values != null && !values.isEmpty()) {
					result = values.get(0);
				}
			}
			return result;
		}

		@Override
		public String getValue(String key) {
			// See com.jcraft.jsch.OpenSSHConfig.MyConfig.getValue() for this
			// special case.
			if (key.equals("compression.s2c") //$NON-NLS-1$
					|| key.equals("compression.c2s")) { //$NON-NLS-1$
				String foo = findValue(key);
				if (foo == null || foo.equals("no")) { //$NON-NLS-1$
					return "none,zlib@openssh.com,zlib"; //$NON-NLS-1$
				}
				return "zlib@openssh.com,zlib,none"; //$NON-NLS-1$
			}
			return findValue(key);
		}

		@Override
		public String[] getValues(String key) {
			String k = mapKey(key);
			List<String> values = listOptions != null ? listOptions.get(k)
					: null;
			if (values == null) {
				values = multiOptions != null ? multiOptions.get(k) : null;
			}
			if (values == null || values.isEmpty()) {
				return new String[0];
			}
			return values.toArray(new String[values.size()]);
		}

		public void setValue(String key, String value) {
			String k = key.toUpperCase(Locale.ROOT);
			if (MULTI_KEYS.contains(k)) {
				if (multiOptions == null) {
					multiOptions = new HashMap<>();
				}
				List<String> values = multiOptions.get(k);
				if (values == null) {
					values = new ArrayList<>(4);
					multiOptions.put(k, values);
				}
				values.add(value);
			} else {
				if (options == null) {
					options = new HashMap<>();
				}
				if (!options.containsKey(k)) {
					options.put(k, value);
				}
			}
		}

		public void setValue(String key, List<String> values) {
			if (values.isEmpty()) {
				// Can occur only on a missing argument: ignore.
				return;
			}
			String k = key.toUpperCase(Locale.ROOT);
			// Check multi-valued keys first; because of the replacement
			// strategy, they must take precedence over list-valued keys
			// which always follow the "first occurrence wins" strategy.
			//
			// Note that SendEnv is a multi-valued list-valued key. (It's
			// rather immaterial for JGit, though.)
			if (MULTI_KEYS.contains(k)) {
				if (multiOptions == null) {
					multiOptions = new HashMap<>(2 * MULTI_KEYS.size());
				}
				List<String> items = multiOptions.get(k);
				if (items == null) {
					items = new ArrayList<>(values);
					multiOptions.put(k, items);
				} else {
					items.addAll(values);
				}
			} else {
				if (listOptions == null) {
					listOptions = new HashMap<>(2 * LIST_KEYS.size());
				}
				if (!listOptions.containsKey(k)) {
					listOptions.put(k, values);
				}
			}
		}

		public static boolean isListKey(String key) {
			return LIST_KEYS.contains(key.toUpperCase(Locale.ROOT));
		}

		/**
		 * Splits the argument into a list of whitespace-separated elements.
		 * Elements containing whitespace must be quoted and will be de-quoted.
		 *
		 * @param argument
		 *            argument part of the configuration line as read from the
		 *            config file
		 * @return a {@link List} of elements, possibly empty and possibly
		 *         containing empty elements
		 */
		public static List<String> parseList(String argument) {
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

		protected void merge(HostEntry entry) {
			if (entry == null) {
				// Can occur if we could not read the config file
				return;
			}
			if (entry.options != null) {
				if (options == null) {
					options = new HashMap<>();
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
					listOptions = new HashMap<>(2 * LIST_KEYS.size());
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
					multiOptions = new HashMap<>(2 * MULTI_KEYS.size());
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

		private class Replacer {
			private final Map<Character, String> replacements = new HashMap<>();

			public Replacer(String originalHostName, File home) {
				replacements.put(Character.valueOf('%'), "%"); //$NON-NLS-1$
				replacements.put(Character.valueOf('d'), home.getPath());
				// Needs special treatment...
				String host = getValue("HOSTNAME"); //$NON-NLS-1$
				replacements.put(Character.valueOf('h'), originalHostName);
				if (host != null && host.indexOf('%') >= 0) {
					host = substitute(host, "h"); //$NON-NLS-1$
					options.put("HOSTNAME", host); //$NON-NLS-1$
				}
				if (host != null) {
					replacements.put(Character.valueOf('h'), host);
				}
				String localhost = SystemReader.getInstance().getHostname();
				replacements.put(Character.valueOf('l'), localhost);
				int period = localhost.indexOf('.');
				if (period > 0) {
					localhost = localhost.substring(0, period);
				}
				replacements.put(Character.valueOf('L'), localhost);
				replacements.put(Character.valueOf('n'), originalHostName);
				replacements.put(Character.valueOf('p'), getValue("PORT")); //$NON-NLS-1$
				replacements.put(Character.valueOf('r'), getValue("USER")); //$NON-NLS-1$
				replacements.put(Character.valueOf('u'), userName());
				replacements.put(Character.valueOf('C'),
						substitute("%l%h%p%r", "hlpr")); //$NON-NLS-1$ //$NON-NLS-2$
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

		protected void substitute(String originalHostName, File home) {
			Replacer r = new Replacer(originalHostName, home);
			if (multiOptions != null) {
				List<String> values = multiOptions.get("IDENTITYFILE"); //$NON-NLS-1$
				if (values != null) {
					values = substitute(values, "dhlru", r); //$NON-NLS-1$
					values = replaceTilde(values, home);
					multiOptions.put("IDENTITYFILE", values); //$NON-NLS-1$
				}
				values = multiOptions.get("CERTIFICATEFILE"); //$NON-NLS-1$
				if (values != null) {
					values = substitute(values, "dhlru", r); //$NON-NLS-1$
					values = replaceTilde(values, home);
					multiOptions.put("CERTIFICATEFILE", values); //$NON-NLS-1$
				}
			}
			if (listOptions != null) {
				List<String> values = listOptions.get("GLOBALKNOWNHOSTSFILE"); //$NON-NLS-1$
				if (values != null) {
					values = replaceTilde(values, home);
					listOptions.put("GLOBALKNOWNHOSTSFILE", values); //$NON-NLS-1$
				}
				values = listOptions.get("USERKNOWNHOSTSFILE"); //$NON-NLS-1$
				if (values != null) {
					values = replaceTilde(values, home);
					listOptions.put("USERKNOWNHOSTSFILE", values); //$NON-NLS-1$
				}
			}
			if (options != null) {
				// HOSTNAME already done in Replacer constructor
				String value = options.get("IDENTITYAGENT"); //$NON-NLS-1$
				if (value != null) {
					value = r.substitute(value, "dhlru"); //$NON-NLS-1$
					value = toFile(value, home).getPath();
					options.put("IDENTITYAGENT", value); //$NON-NLS-1$
				}
			}
			// Match is not implemented and would need to be done elsewhere
			// anyway. ControlPath, LocalCommand, ProxyCommand, and
			// RemoteCommand are not used by Jsch.
		}

		@Override
		@SuppressWarnings("nls")
		public String toString() {
			return "HostEntry [options=" + options + ", multiOptions="
					+ multiOptions + ", listOptions=" + listOptions + "]";
		}
	}

	/**
	 * Configuration of one "Host" block in the configuration file.
	 * <p>
	 * If returned from {@link OpenSshConfig#lookup(String)} some or all of the
	 * properties may not be populated. The properties which are not populated
	 * should be defaulted by the caller.
	 * <p>
	 * When returned from {@link OpenSshConfig#lookup(String)} any wildcard
	 * entries which appear later in the configuration file will have been
	 * already merged into this block.
	 */
	public static class Host {
		String hostName;

		int port;

		File identityFile;

		String user;

		String preferredAuthentications;

		Boolean batchMode;

		String strictHostKeyChecking;

		int connectionAttempts;

		private Config config;

		/**
		 * Creates a new uninitialized {@link Host}.
		 */
		public Host() {
			// For API backwards compatibility with pre-4.9 JGit
		}

		Host(Config config, String hostName, File homeDir) {
			this.config = config;
			complete(hostName, homeDir);
		}

		/**
		 * @return the value StrictHostKeyChecking property, the valid values
		 *         are "yes" (unknown hosts are not accepted), "no" (unknown
		 *         hosts are always accepted), and "ask" (user should be asked
		 *         before accepting the host)
		 */
		public String getStrictHostKeyChecking() {
			return strictHostKeyChecking;
		}

		/**
		 * @return the real IP address or host name to connect to; never null.
		 */
		public String getHostName() {
			return hostName;
		}

		/**
		 * @return the real port number to connect to; never 0.
		 */
		public int getPort() {
			return port;
		}

		/**
		 * @return path of the private key file to use for authentication; null
		 *         if the caller should use default authentication strategies.
		 */
		public File getIdentityFile() {
			return identityFile;
		}

		/**
		 * @return the real user name to connect as; never null.
		 */
		public String getUser() {
			return user;
		}

		/**
		 * @return the preferred authentication methods, separated by commas if
		 *         more than one authentication method is preferred.
		 */
		public String getPreferredAuthentications() {
			return preferredAuthentications;
		}

		/**
		 * @return true if batch (non-interactive) mode is preferred for this
		 *         host connection.
		 */
		public boolean isBatchMode() {
			return batchMode != null && batchMode.booleanValue();
		}

		/**
		 * @return the number of tries (one per second) to connect before
		 *         exiting. The argument must be an integer. This may be useful
		 *         in scripts if the connection sometimes fails. The default is
		 *         1.
		 * @since 3.4
		 */
		public int getConnectionAttempts() {
			return connectionAttempts;
		}


		private void complete(String initialHostName, File homeDir) {
			// Try to set values from the options.
			hostName = config.getHostname();
			user = config.getUser();
			port = config.getPort();
			connectionAttempts = positive(
					config.getValue("ConnectionAttempts")); //$NON-NLS-1$
			strictHostKeyChecking = config.getValue("StrictHostKeyChecking"); //$NON-NLS-1$
			String value = config.getValue("BatchMode"); //$NON-NLS-1$
			if (value != null) {
				batchMode = yesno(value);
			}
			value = config.getValue("PreferredAuthentications"); //$NON-NLS-1$
			if (value != null) {
				preferredAuthentications = nows(value);
			}
			// Fill in defaults if still not set
			if (hostName == null) {
				hostName = initialHostName;
			}
			if (user == null) {
				user = OpenSshConfig.userName();
			}
			if (port <= 0) {
				port = OpenSshConfig.SSH_PORT;
			}
			if (connectionAttempts <= 0) {
				connectionAttempts = 1;
			}
			String[] identityFiles = config.getValues("IdentityFile"); //$NON-NLS-1$
			if (identityFiles != null && identityFiles.length > 0) {
				identityFile = toFile(identityFiles[0], homeDir);
			}
		}

		Config getConfig() {
			return config;
		}

		@Override
		@SuppressWarnings("nls")
		public String toString() {
			return "Host [hostName=" + hostName + ", port=" + port
					+ ", identityFile=" + identityFile + ", user=" + user
					+ ", preferredAuthentications=" + preferredAuthentications
					+ ", batchMode=" + batchMode + ", strictHostKeyChecking="
					+ strictHostKeyChecking + ", connectionAttempts="
					+ connectionAttempts + ", config=" + config + "]";
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Retrieves the full {@link com.jcraft.jsch.ConfigRepository.Config Config}
	 * for the given host name. Should be called only by Jsch and tests.
	 *
	 * @since 4.9
	 */
	@Override
	public Config getConfig(String hostName) {
		Host host = lookup(hostName);
		return host.getConfig();
	}

	/** {@inheritDoc} */
	@Override
	@SuppressWarnings("nls")
	public String toString() {
		return "OpenSshConfig [home=" + home + ", configFile=" + configFile
				+ ", lastModified=" + lastModified + ", state=" + state + "]";
	}
}
