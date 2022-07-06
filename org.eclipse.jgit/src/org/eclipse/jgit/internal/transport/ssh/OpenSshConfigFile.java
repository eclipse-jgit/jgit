/*
 * Copyright (C) 2008, 2017, Google Inc.
 * Copyright (C) 2017, 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.fnmatch.FileNameMatcher;
import org.eclipse.jgit.transport.SshConfigStore;
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
public class OpenSshConfigFile implements SshConfigStore {

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
		List<HostEntry> entries = new LinkedList<>();

		// Previous lookups, keyed by user@hostname:port
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
	 *            the user supplied; &lt;= 0 if none
	 * @param userName
	 *            the user supplied, may be {@code null} or empty if none given
	 * @return the configuration for the requested name.
	 */
	@Override
	@NonNull
	public HostEntry lookup(@NonNull String hostName, int port,
			String userName) {
		return lookup(hostName, port, userName, false);
	}

	@Override
	@NonNull
	public HostEntry lookupDefault(@NonNull String hostName, int port,
			String userName) {
		return lookup(hostName, port, userName, true);
	}

	private HostEntry lookup(@NonNull String hostName, int port,
			String userName, boolean fillDefaults) {
		final State cache = refresh();
		String cacheKey = toCacheKey(hostName, port, userName);
		HostEntry h = cache.hosts.get(cacheKey);
		if (h != null) {
			return h;
		}
		HostEntry fullConfig = new HostEntry();
		Iterator<HostEntry> entries = cache.entries.iterator();
		if (entries.hasNext()) {
			// Should always have at least the first top entry containing
			// key-value pairs before the first Host block
			fullConfig.merge(entries.next());
			entries.forEachRemaining(entry -> {
				if (entry.matches(hostName)) {
					fullConfig.merge(entry);
				}
			});
		}
		fullConfig.substitute(hostName, port, userName, localUserName, home,
				fillDefaults);
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

	private List<HostEntry> parse(BufferedReader reader)
			throws IOException {
		final List<HostEntry> entries = new LinkedList<>();

		// The man page doesn't say so, but the openssh parser (readconf.c)
		// starts out in active mode and thus always applies any lines that
		// occur before the first host block. We gather those options in a
		// HostEntry.
		HostEntry defaults = new HostEntry();
		HostEntry current = defaults;
		entries.add(defaults);

		String line;
		while ((line = reader.readLine()) != null) {
			line = line.strip();
			if (line.isEmpty()) {
				continue;
			}
			String[] parts = line.split("[ \t]*[= \t]", 2); //$NON-NLS-1$
			String keyword = parts[0].strip();
			if (keyword.isEmpty()) {
				continue;
			}
			switch (keyword.charAt(0)) {
			case '#':
				continue;
			case '"':
				// Although the ssh-config man page doesn't say so, the openssh
				// parser does allow quoted keywords.
				List<String> dequoted = parseList(keyword);
				keyword = dequoted.isEmpty() ? "" : dequoted.get(0); //$NON-NLS-1$
				break;
			default:
				// Keywords never contain hashes, nor whitespace
				int i = keyword.indexOf('#');
				if (i >= 0) {
					keyword = keyword.substring(0, i);
				}
				break;
			}
			if (keyword.isEmpty()) {
				continue;
			}
			// man 5 ssh-config says lines had the format "keyword arguments",
			// with no indication that arguments were optional. However, let's
			// not crap out on missing arguments. See bug 444319.
			String argValue = parts.length > 1 ? parts[1].strip() : ""; //$NON-NLS-1$

			if (StringUtils.equalsIgnoreCase(SshConstants.HOST, keyword)) {
				current = new HostEntry(parseList(argValue));
				entries.add(current);
				continue;
			}

			if (HostEntry.isListKey(keyword)) {
				List<String> args = validate(keyword, parseList(argValue));
				current.setValue(keyword, args);
			} else if (!argValue.isEmpty()) {
				List<String> args = parseList(argValue);
				String arg = args.isEmpty() ? "" : args.get(0); //$NON-NLS-1$
				argValue = validate(keyword, arg);
				current.setValue(keyword, argValue);
			}
		}

		return entries;
	}

	/**
	 * Splits the argument into a list of whitespace-separated elements.
	 * Elements containing whitespace must be quoted and will be de-quoted.
	 * Backslash-escapes are handled for quotes and blanks.
	 *
	 * @param argument
	 *            argument part of the configuration line as read from the
	 *            config file
	 * @return a {@link List} of elements, possibly empty and possibly
	 *         containing empty elements, but not containing {@code null}
	 */
	private static List<String> parseList(String argument) {
		List<String> result = new ArrayList<>(4);
		int start = 0;
		int length = argument.length();
		while (start < length) {
			// Skip whitespace
			char ch = argument.charAt(start);
			if (Character.isWhitespace(ch)) {
				start++;
			} else if (ch == '#') {
				break; // Comment start
			} else {
				// Parse one token now.
				start = parseToken(argument, start, length, result);
			}
		}
		return result;
	}

	/**
	 * Parses a token up to the next whitespace not inside a string quoted by
	 * single or double quotes. Inside a string, quotes can be escaped by
	 * backslash characters. Outside of a string, "\ " can be used to include a
	 * space in a token; inside a string "\ " is taken literally as '\' followed
	 * by ' '.
	 *
	 * @param argument
	 *            to parse the token out of
	 * @param from
	 *            index at the beginning of the token
	 * @param to
	 *            index one after the last character to look at
	 * @param result
	 *            a list collecting tokens to which the parsed token is added
	 * @return the index after the token
	 */
	private static int parseToken(String argument, int from, int to,
			List<String> result) {
		StringBuilder b = new StringBuilder();
		int i = from;
		char quote = 0;
		boolean escaped = false;
		SCAN: while (i < to) {
			char ch = argument.charAt(i);
			switch (ch) {
			case '"':
			case '\'':
				if (quote == 0) {
					if (escaped) {
						b.append(ch);
					} else {
						quote = ch;
					}
				} else if (!escaped && quote == ch) {
					quote = 0;
				} else {
					b.append(ch);
				}
				escaped = false;
				break;
			case '\\':
				if (escaped) {
					b.append(ch);
				}
				escaped = !escaped;
				break;
			case ' ':
				if (quote == 0) {
					if (escaped) {
						b.append(ch);
						escaped = false;
					} else {
						break SCAN;
					}
				} else {
					if (escaped) {
						b.append('\\');
					}
					b.append(ch);
					escaped = false;
				}
				break;
			default:
				if (escaped) {
					b.append('\\');
				}
				if (quote == 0 && Character.isWhitespace(ch)) {
					break SCAN;
				}
				b.append(ch);
				escaped = false;
				break;
			}
			i++;
		}
		if (b.length() > 0) {
			result.add(b.toString());
		}
		return i;
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
		if (SshConstants.PREFERRED_AUTHENTICATIONS.equalsIgnoreCase(key)) {
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
		}
		// Not a pattern but a full host name
		return pattern.equals(name);
	}

	private static String stripWhitespace(String value) {
		final StringBuilder b = new StringBuilder();
		int length = value.length();
		for (int i = 0; i < length; i++) {
			char ch = value.charAt(i);
			if (!Character.isWhitespace(ch)) {
				b.append(ch);
			}
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
	 * Converts an OpenSSH time value into a number of seconds. The format is
	 * defined by OpenSSH as a sequence of (positive) integers with suffixes for
	 * seconds, minutes, hours, days, and weeks.
	 *
	 * @param value
	 *            to convert
	 * @return the parsed value as a number of seconds, or -1 if the value is
	 *         not a valid OpenSSH time value
	 * @see <a href="https://man.openbsd.org/sshd_config.5#TIME_FORMATS">OpenBSD
	 *      man 5 sshd_config, section TIME FORMATS</a>
	 */
	public static int timeSpec(String value) {
		if (value == null) {
			return -1;
		}
		try {
			int length = value.length();
			int i = 0;
			int seconds = 0;
			boolean valueSeen = false;
			while (i < length) {
				// Skip whitespace
				char ch = value.charAt(i);
				if (Character.isWhitespace(ch)) {
					i++;
					continue;
				}
				if (ch == '+') {
					// OpenSSH uses strtol with base 10: a leading plus sign is
					// allowed.
					i++;
				}
				int val = 0;
				int j = i;
				while (j < length) {
					ch = value.charAt(j++);
					if (ch >= '0' && ch <= '9') {
						val = Math.addExact(Math.multiplyExact(val, 10),
								ch - '0');
					} else {
						j--;
						break;
					}
				}
				if (i == j) {
					// No digits seen
					return -1;
				}
				i = j;
				int multiplier = 1;
				if (i < length) {
					ch = value.charAt(i++);
					switch (ch) {
					case 's':
					case 'S':
						break;
					case 'm':
					case 'M':
						multiplier = 60;
						break;
					case 'h':
					case 'H':
						multiplier = 3600;
						break;
					case 'd':
					case 'D':
						multiplier = 24 * 3600;
						break;
					case 'w':
					case 'W':
						multiplier = 7 * 24 * 3600;
						break;
					default:
						if (Character.isWhitespace(ch)) {
							break;
						}
						// Invalid time spec
						return -1;
					}
				}
				seconds = Math.addExact(seconds,
						Math.multiplyExact(val, multiplier));
				valueSeen = true;
			}
			return valueSeen ? seconds : -1;
		} catch (ArithmeticException e) {
			// Overflow
			return -1;
		}
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
	public static class HostEntry implements SshConfigStore.HostConfig {

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
			LIST_KEYS.add(SshConstants.ADD_KEYS_TO_AGENT); // confirm timeSpec
		}

		/**
		 * OpenSSH has renamed some config keys. This maps old names to new
		 * names.
		 */
		private static final Map<String, String> ALIASES = new TreeMap<>(
				String.CASE_INSENSITIVE_ORDER);

		static {
			// See https://github.com/openssh/openssh-portable/commit/ee9c0da80
			ALIASES.put("PubkeyAcceptedKeyTypes", //$NON-NLS-1$
					SshConstants.PUBKEY_ACCEPTED_ALGORITHMS);
		}

		private Map<String, String> options;

		private Map<String, List<String>> multiOptions;

		private Map<String, List<String>> listOptions;

		private final List<String> patterns;

		/**
		 * Constructor used to build the merged entry; never matches anything
		 */
		public HostEntry() {
			this.patterns = Collections.emptyList();
		}

		/**
		 * @param patterns
		 *            to be used in matching against host name.
		 */
		public HostEntry(List<String> patterns) {
			this.patterns = patterns;
		}

		boolean matches(String hostName) {
			boolean doesMatch = false;
			for (String pattern : patterns) {
				if (pattern.startsWith("!")) { //$NON-NLS-1$
					if (patternMatchesHost(pattern.substring(1), hostName)) {
						return false;
					}
				} else if (!doesMatch
						&& patternMatchesHost(pattern, hostName)) {
					doesMatch = true;
				}
			}
			return doesMatch;
		}

		private static String toKey(String key) {
			String k = ALIASES.get(key);
			return k != null ? k : key;
		}

		/**
		 * Retrieves the value of a single-valued key, or the first if the key
		 * has multiple values. Keys are case-insensitive, so
		 * {@code getValue("HostName") == getValue("HOSTNAME")}.
		 *
		 * @param key
		 *            to get the value of
		 * @return the value, or {@code null} if none
		 */
		@Override
		public String getValue(String key) {
			String k = toKey(key);
			String result = options != null ? options.get(k) : null;
			if (result == null) {
				// Let's be lenient and return at least the first value from
				// a list-valued or multi-valued key.
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

		/**
		 * Retrieves the values of a multi or list-valued key. Keys are
		 * case-insensitive, so
		 * {@code getValue("HostName") == getValue("HOSTNAME")}.
		 *
		 * @param key
		 *            to get the values of
		 * @return a possibly empty list of values
		 */
		@Override
		public List<String> getValues(String key) {
			String k = toKey(key);
			List<String> values = listOptions != null ? listOptions.get(k)
					: null;
			if (values == null) {
				values = multiOptions != null ? multiOptions.get(k) : null;
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
			String k = toKey(key);
			if (value == null) {
				if (multiOptions != null) {
					multiOptions.remove(k);
				}
				if (listOptions != null) {
					listOptions.remove(k);
				}
				if (options != null) {
					options.remove(k);
				}
				return;
			}
			if (MULTI_KEYS.contains(k)) {
				if (multiOptions == null) {
					multiOptions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				}
				List<String> values = multiOptions.get(k);
				if (values == null) {
					values = new ArrayList<>(4);
					multiOptions.put(k, values);
				}
				values.add(value);
			} else {
				if (options == null) {
					options = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				}
				if (!options.containsKey(k)) {
					options.put(k, value);
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
			String k = toKey(key);
			// Check multi-valued keys first; because of the replacement
			// strategy, they must take precedence over list-valued keys
			// which always follow the "first occurrence wins" strategy.
			//
			// Note that SendEnv is a multi-valued list-valued key. (It's
			// rather immaterial for JGit, though.)
			if (MULTI_KEYS.contains(k)) {
				if (multiOptions == null) {
					multiOptions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
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
					listOptions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				}
				if (!listOptions.containsKey(k)) {
					listOptions.put(k, values);
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
			return LIST_KEYS.contains(toKey(key));
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
				Replacer r, boolean withEnv) {
			List<String> result = new ArrayList<>(values.size());
			for (String value : values) {
				result.add(r.substitute(value, allowed, withEnv));
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
				String localUserName, File home, boolean fillDefaults) {
			int p = port > 0 ? port : positive(getValue(SshConstants.PORT));
			if (p <= 0) {
				p = SshConstants.SSH_DEFAULT_PORT;
			}
			String u = !StringUtils.isEmptyOrNull(userName) ? userName
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
					hostName = r.substitute(hostName, "h", false); //$NON-NLS-1$
					options.put(SshConstants.HOST_NAME, hostName);
					r.update('h', hostName);
				}
			} else if (fillDefaults) {
				setValue(SshConstants.HOST_NAME, originalHostName);
			}
			if (multiOptions != null) {
				List<String> values = multiOptions
						.get(SshConstants.IDENTITY_FILE);
				if (values != null) {
					values = substitute(values, Replacer.DEFAULT_TOKENS, r,
							true);
					values = replaceTilde(values, home);
					multiOptions.put(SshConstants.IDENTITY_FILE, values);
				}
				values = multiOptions.get(SshConstants.CERTIFICATE_FILE);
				if (values != null) {
					values = substitute(values, Replacer.DEFAULT_TOKENS, r,
							true);
					values = replaceTilde(values, home);
					multiOptions.put(SshConstants.CERTIFICATE_FILE, values);
				}
			}
			if (listOptions != null) {
				List<String> values = listOptions
						.get(SshConstants.USER_KNOWN_HOSTS_FILE);
				if (values != null) {
					values = substitute(values, Replacer.DEFAULT_TOKENS, r,
							true);
					values = replaceTilde(values, home);
					listOptions.put(SshConstants.USER_KNOWN_HOSTS_FILE, values);
				}
			}
			if (options != null) {
				// HOSTNAME already done above
				String value = options.get(SshConstants.IDENTITY_AGENT);
				if (value != null && !SshConstants.NONE.equals(value)
						&& !SshConstants.ENV_SSH_AUTH_SOCKET.equals(value)) {
					value = r.substitute(value, Replacer.DEFAULT_TOKENS, true);
					value = toFile(value, home).getPath();
					options.put(SshConstants.IDENTITY_AGENT, value);
				}
				value = options.get(SshConstants.CONTROL_PATH);
				if (value != null) {
					value = r.substitute(value, Replacer.DEFAULT_TOKENS, true);
					value = toFile(value, home).getPath();
					options.put(SshConstants.CONTROL_PATH, value);
				}
				value = options.get(SshConstants.LOCAL_COMMAND);
				if (value != null) {
					value = r.substitute(value, "CdhLlnprTu", false); //$NON-NLS-1$
					options.put(SshConstants.LOCAL_COMMAND, value);
				}
				value = options.get(SshConstants.REMOTE_COMMAND);
				if (value != null) {
					value = r.substitute(value, Replacer.DEFAULT_TOKENS, false);
					options.put(SshConstants.REMOTE_COMMAND, value);
				}
				value = options.get(SshConstants.PROXY_COMMAND);
				if (value != null) {
					value = r.substitute(value, "hnpr", false); //$NON-NLS-1$
					options.put(SshConstants.PROXY_COMMAND, value);
				}
			}
			// Match is not implemented and would need to be done elsewhere
			// anyway.
			if (fillDefaults) {
				String s = options.get(SshConstants.USER);
				if (StringUtils.isEmptyOrNull(s)) {
					options.put(SshConstants.USER, u);
				}
				if (positive(options.get(SshConstants.PORT)) <= 0) {
					options.put(SshConstants.PORT, Integer.toString(p));
				}
				if (positive(
						options.get(SshConstants.CONNECTION_ATTEMPTS)) <= 0) {
					options.put(SshConstants.CONNECTION_ATTEMPTS, "1"); //$NON-NLS-1$
				}
			}
		}

		/**
		 * Retrieves an unmodifiable map of all single-valued options, with
		 * case-insensitive lookup by keys.
		 *
		 * @return all single-valued options
		 */
		@Override
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
		@Override
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

		/**
		 * Tokens applicable to most keys.
		 *
		 * @see <a href="https://man.openbsd.org/ssh_config.5#TOKENS">man
		 *      ssh_config</a>
		 */
		public static final String DEFAULT_TOKENS = "CdhLlnpru"; //$NON-NLS-1$

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
					substitute("%l%h%p%r", "hlpr", false)); //$NON-NLS-1$ //$NON-NLS-2$
			replacements.put(Character.valueOf('T'), "NONE"); //$NON-NLS-1$
		}

		public void update(char key, String value) {
			replacements.put(Character.valueOf(key), value);
			if ("lhpr".indexOf(key) >= 0) { //$NON-NLS-1$
				replacements.put(Character.valueOf('C'),
						substitute("%l%h%p%r", "hlpr", false)); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		public String substitute(String input, String allowed,
				boolean withEnv) {
			if (input == null || input.length() <= 1
					|| (input.indexOf('%') < 0
							&& (!withEnv || input.indexOf("${") < 0))) { //$NON-NLS-1$
				return input;
			}
			StringBuilder builder = new StringBuilder();
			int start = 0;
			int length = input.length();
			while (start < length) {
				char ch = input.charAt(start);
				switch (ch) {
				case '%':
					if (start + 1 >= length) {
						break;
					}
					String replacement = null;
					ch = input.charAt(start + 1);
					if (ch == '%' || allowed.indexOf(ch) >= 0) {
						replacement = replacements.get(Character.valueOf(ch));
					}
					if (replacement == null) {
						builder.append('%').append(ch);
					} else {
						builder.append(replacement);
					}
					start += 2;
					continue;
				case '$':
					if (!withEnv || start + 2 >= length) {
						break;
					}
					ch = input.charAt(start + 1);
					if (ch == '{') {
						int close = input.indexOf('}', start + 2);
						if (close > start + 2) {
							String variable = SystemReader.getInstance()
									.getenv(input.substring(start + 2, close));
							if (!StringUtils.isEmptyOrNull(variable)) {
								builder.append(variable);
							}
							start = close + 1;
							continue;
						}
					}
					ch = '$';
					break;
				default:
					break;
				}
				builder.append(ch);
				start++;
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
