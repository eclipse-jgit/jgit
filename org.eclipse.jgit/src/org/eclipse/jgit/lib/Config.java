/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 * Copyright (C) 2009, Constantine Plotnikov <constantine.plotnikov@gmail.com>
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2009, Google, Inc.
 * Copyright (C) 2009, JetBrains s.r.o.
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Thad Hughes <thadh@thad.corp.google.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.events.ConfigChangedEvent;
import org.eclipse.jgit.events.ConfigChangedListener;
import org.eclipse.jgit.events.ListenerHandle;
import org.eclipse.jgit.events.ListenerList;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.StringUtils;

/**
 * Git style {@code .config}, {@code .gitconfig}, {@code .gitmodules} file.
 */
public class Config {

	private static final String[] EMPTY_STRING_ARRAY = {};

	private static final int MAX_DEPTH = 10;

	private static final TypedConfigGetter DEFAULT_GETTER = new DefaultTypedConfigGetter();

	private static TypedConfigGetter typedGetter = DEFAULT_GETTER;

	/** the change listeners */
	private final ListenerList listeners = new ListenerList();

	/**
	 * Immutable current state of the configuration data.
	 * <p>
	 * This state is copy-on-write. It should always contain an immutable list
	 * of the configuration keys/values.
	 */
	private final AtomicReference<ConfigSnapshot> state;

	private final Config baseConfig;

	/**
	 * Magic value indicating a missing entry.
	 * <p>
	 * This value is tested for reference equality in some contexts, so we
	 * must ensure it is a special copy of the empty string.  It also must
	 * be treated like the empty string.
	 */
	private static final String MISSING_ENTRY = new String();

	/**
	 * Create a configuration with no default fallback.
	 */
	public Config() {
		this(null);
	}

	/**
	 * Create an empty configuration with a fallback for missing keys.
	 *
	 * @param defaultConfig
	 *            the base configuration to be consulted when a key is missing
	 *            from this configuration instance.
	 */
	public Config(Config defaultConfig) {
		baseConfig = defaultConfig;
		state = new AtomicReference<>(newState());
	}

	/**
	 * Retrieves this config's base config.
	 *
	 * @return the base configuration of this config.
	 *
	 * @since 5.5.2
	 */
	public Config getBaseConfig() {
		return baseConfig;
	}

	/**
	 * Check if a given string is the "missing" value.
	 *
	 * @param value
	 *            string to be checked.
	 * @return true if the given string is the "missing" value.
	 * @since 5.4
	 */
	@SuppressWarnings({ "ReferenceEquality", "StringEquality" })
	public static boolean isMissing(String value) {
		return value == MISSING_ENTRY;
	}

	/**
	 * Globally sets a {@link org.eclipse.jgit.lib.TypedConfigGetter} that is
	 * subsequently used to read typed values from all git configs.
	 *
	 * @param getter
	 *            to use; if {@code null} use the default getter.
	 * @since 4.9
	 */
	public static void setTypedConfigGetter(TypedConfigGetter getter) {
		typedGetter = getter == null ? DEFAULT_GETTER : getter;
	}

	/**
	 * Escape the value before saving
	 *
	 * @param x
	 *            the value to escape
	 * @return the escaped value
	 */
	static String escapeValue(String x) {
		if (x.isEmpty()) {
			return ""; //$NON-NLS-1$
		}

		boolean needQuote = x.charAt(0) == ' ' || x.charAt(x.length() - 1) == ' ';
		StringBuilder r = new StringBuilder(x.length());
		for (int k = 0; k < x.length(); k++) {
			char c = x.charAt(k);
			// git-config(1) lists the limited set of supported escape sequences, but
			// the documentation is otherwise not especially normative. In particular,
			// which ones of these produce and/or require escaping and/or quoting
			// around them is not documented and was discovered by trial and error.
			// In summary:
			//
			// * Quotes are only required if there is leading/trailing whitespace or a
			//   comment character.
			// * Bytes that have a supported escape sequence are escaped, except for
			//   \b for some reason which isn't.
			// * Needing an escape sequence is not sufficient reason to quote the
			//   value.
			switch (c) {
			case '\0':
				// Unix command line calling convention cannot pass a '\0' as an
				// argument, so there is no equivalent way in C git to store a null byte
				// in a config value.
				throw new IllegalArgumentException(
						JGitText.get().configValueContainsNullByte);

			case '\n':
				r.append('\\').append('n');
				break;

			case '\t':
				r.append('\\').append('t');
				break;

			case '\b':
				// Doesn't match `git config foo.bar $'x\by'`, which doesn't escape the
				// \x08, but since both escaped and unescaped forms are readable, we'll
				// prefer internal consistency here.
				r.append('\\').append('b');
				break;

			case '\\':
				r.append('\\').append('\\');
				break;

			case '"':
				r.append('\\').append('"');
				break;

			case '#':
			case ';':
				needQuote = true;
				r.append(c);
				break;

			default:
				r.append(c);
				break;
			}
		}

		return needQuote ? '"' + r.toString() + '"' : r.toString();
	}

	static String escapeSubsection(String x) {
		if (x.isEmpty()) {
			return "\"\""; //$NON-NLS-1$
		}

		StringBuilder r = new StringBuilder(x.length() + 2).append('"');
		for (int k = 0; k < x.length(); k++) {
			char c = x.charAt(k);

			// git-config(1) lists the limited set of supported escape sequences
			// (which is even more limited for subsection names than for values).
			switch (c) {
			case '\0':
				throw new IllegalArgumentException(
						JGitText.get().configSubsectionContainsNullByte);

			case '\n':
				throw new IllegalArgumentException(
						JGitText.get().configSubsectionContainsNewline);

			case '\\':
			case '"':
				r.append('\\').append(c);
				break;

			default:
				r.append(c);
				break;
			}
		}

		return r.append('"').toString();
	}

	/**
	 * Obtain an integer value from the configuration.
	 *
	 * @param section
	 *            section the key is grouped within.
	 * @param name
	 *            name of the key to get.
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @return an integer value from the configuration, or defaultValue.
	 */
	public int getInt(final String section, final String name,
			final int defaultValue) {
		return typedGetter.getInt(this, section, null, name, defaultValue);
	}

	/**
	 * Obtain an integer value from the configuration.
	 *
	 * @param section
	 *            section the key is grouped within.
	 * @param subsection
	 *            subsection name, such a remote or branch name.
	 * @param name
	 *            name of the key to get.
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @return an integer value from the configuration, or defaultValue.
	 */
	public int getInt(final String section, String subsection,
			final String name, final int defaultValue) {
		return typedGetter.getInt(this, section, subsection, name,
				defaultValue);
	}

	/**
	 * Obtain an integer value from the configuration which must be inside given
	 * range.
	 *
	 * @param section
	 *            section the key is grouped within.
	 * @param name
	 *            name of the key to get.
	 * @param minValue
	 *            minimum value
	 * @param maxValue
	 *            maximum value
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @return an integer value from the configuration, or defaultValue.
	 * @since 6.1
	 */
	public int getIntInRange(String section, String name, int minValue,
			int maxValue, int defaultValue) {
		return typedGetter.getIntInRange(this, section, null, name, minValue,
				maxValue, defaultValue);
	}

	/**
	 * Obtain an integer value from the configuration which must be inside given
	 * range.
	 *
	 * @param section
	 *            section the key is grouped within.
	 * @param subsection
	 *            subsection name, such a remote or branch name.
	 * @param name
	 *            name of the key to get.
	 * @param minValue
	 *            minimum value
	 * @param maxValue
	 *            maximum value
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @return an integer value from the configuration, or defaultValue.
	 * @since 6.1
	 */
	public int getIntInRange(String section, String subsection, String name,
			int minValue, int maxValue, int defaultValue) {
		return typedGetter.getIntInRange(this, section, subsection, name,
				minValue, maxValue, defaultValue);
	}

	/**
	 * Obtain an integer value from the configuration.
	 *
	 * @param section
	 *            section the key is grouped within.
	 * @param name
	 *            name of the key to get.
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @return an integer value from the configuration, or defaultValue.
	 */
	public long getLong(String section, String name, long defaultValue) {
		return typedGetter.getLong(this, section, null, name, defaultValue);
	}

	/**
	 * Obtain an integer value from the configuration.
	 *
	 * @param section
	 *            section the key is grouped within.
	 * @param subsection
	 *            subsection name, such a remote or branch name.
	 * @param name
	 *            name of the key to get.
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @return an integer value from the configuration, or defaultValue.
	 */
	public long getLong(final String section, String subsection,
			final String name, final long defaultValue) {
		return typedGetter.getLong(this, section, subsection, name,
				defaultValue);
	}

	/**
	 * Get a boolean value from the git config
	 *
	 * @param section
	 *            section the key is grouped within.
	 * @param name
	 *            name of the key to get.
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @return true if any value or defaultValue is true, false for missing or
	 *         explicit false
	 */
	public boolean getBoolean(final String section, final String name,
			final boolean defaultValue) {
		return typedGetter.getBoolean(this, section, null, name, defaultValue);
	}

	/**
	 * Get a boolean value from the git config
	 *
	 * @param section
	 *            section the key is grouped within.
	 * @param subsection
	 *            subsection name, such a remote or branch name.
	 * @param name
	 *            name of the key to get.
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @return true if any value or defaultValue is true, false for missing or
	 *         explicit false
	 */
	public boolean getBoolean(final String section, String subsection,
			final String name, final boolean defaultValue) {
		return typedGetter.getBoolean(this, section, subsection, name,
				defaultValue);
	}

	/**
	 * Parse an enumeration from the configuration.
	 *
	 * @param section
	 *            section the key is grouped within.
	 * @param subsection
	 *            subsection name, such a remote or branch name.
	 * @param name
	 *            name of the key to get.
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @return the selected enumeration value, or {@code defaultValue}.
	 */
	public <T extends Enum<?>> T getEnum(final String section,
			final String subsection, final String name, final T defaultValue) {
		final T[] all = allValuesOf(defaultValue);
		return typedGetter.getEnum(this, all, section, subsection, name,
				defaultValue);
	}

	@SuppressWarnings("unchecked")
	private static <T> T[] allValuesOf(T value) {
		try {
			return (T[]) value.getClass().getMethod("values").invoke(null); //$NON-NLS-1$
		} catch (Exception err) {
			String typeName = value.getClass().getName();
			String msg = MessageFormat.format(
					JGitText.get().enumValuesNotAvailable, typeName);
			throw new IllegalArgumentException(msg, err);
		}
	}

	/**
	 * Parse an enumeration from the configuration.
	 *
	 * @param all
	 *            all possible values in the enumeration which should be
	 *            recognized. Typically {@code EnumType.values()}.
	 * @param section
	 *            section the key is grouped within.
	 * @param subsection
	 *            subsection name, such a remote or branch name.
	 * @param name
	 *            name of the key to get.
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @return the selected enumeration value, or {@code defaultValue}.
	 */
	public <T extends Enum<?>> T getEnum(final T[] all, final String section,
			final String subsection, final String name, final T defaultValue) {
		return typedGetter.getEnum(this, all, section, subsection, name,
				defaultValue);
	}

	/**
	 * Get string value or null if not found.
	 *
	 * @param section
	 *            the section
	 * @param subsection
	 *            the subsection for the value
	 * @param name
	 *            the key name
	 * @return a String value from the config, <code>null</code> if not found
	 */
	public String getString(final String section, String subsection,
			final String name) {
		return getRawString(section, subsection, name);
	}

	/**
	 * Get a list of string values
	 * <p>
	 * If this instance was created with a base, the base's values are returned
	 * first (if any).
	 *
	 * @param section
	 *            the section
	 * @param subsection
	 *            the subsection for the value
	 * @param name
	 *            the key name
	 * @return array of zero or more values from the configuration.
	 */
	public String[] getStringList(final String section, String subsection,
			final String name) {
		String[] base;
		if (baseConfig != null)
			base = baseConfig.getStringList(section, subsection, name);
		else
			base = EMPTY_STRING_ARRAY;

		String[] self = getRawStringList(section, subsection, name);
		if (self == null)
			return base;
		if (base.length == 0)
			return self;
		String[] res = new String[base.length + self.length];
		int n = base.length;
		System.arraycopy(base, 0, res, 0, n);
		System.arraycopy(self, 0, res, n, self.length);
		return res;
	}

	/**
	 * Parse a numerical time unit, such as "1 minute", from the configuration.
	 *
	 * @param section
	 *            section the key is in.
	 * @param subsection
	 *            subsection the key is in, or null if not in a subsection.
	 * @param name
	 *            the key name.
	 * @param defaultValue
	 *            default value to return if no value was present.
	 * @param wantUnit
	 *            the units of {@code defaultValue} and the return value, as
	 *            well as the units to assume if the value does not contain an
	 *            indication of the units.
	 * @return the value, or {@code defaultValue} if not set, expressed in
	 *         {@code units}.
	 * @since 4.5
	 */
	public long getTimeUnit(String section, String subsection, String name,
			long defaultValue, TimeUnit wantUnit) {
		return typedGetter.getTimeUnit(this, section, subsection, name,
				defaultValue, wantUnit);
	}

	/**
	 * Parse a string value and treat it as a file path, replacing a ~/ prefix
	 * by the user's home directory.
	 * <p>
	 * <b>Note:</b> this may throw {@link InvalidPathException} if the string is
	 * not a valid path.
	 * </p>
	 *
	 * @param section
	 *            section the key is in.
	 * @param subsection
	 *            subsection the key is in, or null if not in a subsection.
	 * @param name
	 *            the key name.
	 * @param fs
	 *            to use to convert the string into a path.
	 * @param resolveAgainst
	 *            directory to resolve the path against if it is a relative
	 *            path; {@code null} to use the Java process's current
	 *            directory.
	 * @param defaultValue
	 *            to return if no value was present
	 * @return the {@link Path}, or {@code defaultValue} if not set
	 * @since 5.10
	 */
	public Path getPath(String section, String subsection, String name,
			@NonNull FS fs, File resolveAgainst, Path defaultValue) {
		return typedGetter.getPath(this, section, subsection, name, fs,
				resolveAgainst, defaultValue);
	}

	/**
	 * Parse a list of {@link org.eclipse.jgit.transport.RefSpec}s from the
	 * configuration.
	 *
	 * @param section
	 *            section the key is in.
	 * @param subsection
	 *            subsection the key is in, or null if not in a subsection.
	 * @param name
	 *            the key name.
	 * @return a possibly empty list of
	 *         {@link org.eclipse.jgit.transport.RefSpec}s
	 * @since 4.9
	 */
	public List<RefSpec> getRefSpecs(String section, String subsection,
			String name) {
		return typedGetter.getRefSpecs(this, section, subsection, name);
	}

	/**
	 * Get set of all subsections of specified section within this configuration
	 * and its base configuration
	 *
	 * @param section
	 *            section to search for.
	 * @return set of all subsections of specified section within this
	 *         configuration and its base configuration; may be empty if no
	 *         subsection exists. The set's iterator returns sections in the
	 *         order they are declared by the configuration starting from this
	 *         instance and progressing through the base.
	 */
	public Set<String> getSubsections(String section) {
		return getState().getSubsections(section);
	}

	/**
	 * Get the sections defined in this {@link org.eclipse.jgit.lib.Config}.
	 *
	 * @return the sections defined in this {@link org.eclipse.jgit.lib.Config}.
	 *         The set's iterator returns sections in the order they are
	 *         declared by the configuration starting from this instance and
	 *         progressing through the base.
	 */
	public Set<String> getSections() {
		return getState().getSections();
	}

	/**
	 * Get the list of names defined for this section
	 *
	 * @param section
	 *            the section
	 * @return the list of names defined for this section
	 */
	public Set<String> getNames(String section) {
		return getNames(section, null);
	}

	/**
	 * Get the list of names defined for this subsection
	 *
	 * @param section
	 *            the section
	 * @param subsection
	 *            the subsection
	 * @return the list of names defined for this subsection
	 */
	public Set<String> getNames(String section, String subsection) {
		return getState().getNames(section, subsection);
	}

	/**
	 * Get the list of names defined for this section
	 *
	 * @param section
	 *            the section
	 * @param recursive
	 *            if {@code true} recursively adds the names defined in all base
	 *            configurations
	 * @return the list of names defined for this section
	 * @since 3.2
	 */
	public Set<String> getNames(String section, boolean recursive) {
		return getState().getNames(section, null, recursive);
	}

	/**
	 * Get the list of names defined for this section
	 *
	 * @param section
	 *            the section
	 * @param subsection
	 *            the subsection
	 * @param recursive
	 *            if {@code true} recursively adds the names defined in all base
	 *            configurations
	 * @return the list of names defined for this subsection
	 * @since 3.2
	 */
	public Set<String> getNames(String section, String subsection,
			boolean recursive) {
		return getState().getNames(section, subsection, recursive);
	}

	/**
	 * Obtain a handle to a parsed set of configuration values.
	 *
	 * @param <T>
	 *            type of configuration model to return.
	 * @param parser
	 *            parser which can create the model if it is not already
	 *            available in this configuration file. The parser is also used
	 *            as the key into a cache and must obey the hashCode and equals
	 *            contract in order to reuse a parsed model.
	 * @return the parsed object instance, which is cached inside this config.
	 */
	@SuppressWarnings("unchecked")
	public <T> T get(SectionParser<T> parser) {
		final ConfigSnapshot myState = getState();
		T obj = (T) myState.cache.get(parser);
		if (obj == null) {
			obj = parser.parse(this);
			myState.cache.put(parser, obj);
		}
		return obj;
	}

	/**
	 * Remove a cached configuration object.
	 * <p>
	 * If the associated configuration object has not yet been cached, this
	 * method has no effect.
	 *
	 * @param parser
	 *            parser used to obtain the configuration object.
	 * @see #get(SectionParser)
	 */
	public void uncache(SectionParser<?> parser) {
		state.get().cache.remove(parser);
	}

	/**
	 * Adds a listener to be notified about changes.
	 * <p>
	 * Clients are supposed to remove the listeners after they are done with
	 * them using the {@link org.eclipse.jgit.events.ListenerHandle#remove()}
	 * method
	 *
	 * @param listener
	 *            the listener
	 * @return the handle to the registered listener
	 */
	public ListenerHandle addChangeListener(ConfigChangedListener listener) {
		return listeners.addConfigChangedListener(listener);
	}

	/**
	 * Determine whether to issue change events for transient changes.
	 * <p>
	 * If <code>true</code> is returned (which is the default behavior),
	 * {@link #fireConfigChangedEvent()} will be called upon each change.
	 * <p>
	 * Subclasses that override this to return <code>false</code> are
	 * responsible for issuing {@link #fireConfigChangedEvent()} calls
	 * themselves.
	 *
	 * @return <code></code>
	 */
	protected boolean notifyUponTransientChanges() {
		return true;
	}

	/**
	 * Notifies the listeners
	 */
	protected void fireConfigChangedEvent() {
		listeners.dispatch(new ConfigChangedEvent());
	}

	String getRawString(final String section, final String subsection,
			final String name) {
		String[] lst = getRawStringList(section, subsection, name);
		if (lst != null) {
			return lst[lst.length - 1];
		} else if (baseConfig != null) {
			return baseConfig.getRawString(section, subsection, name);
		} else {
			return null;
		}
	}

	private String[] getRawStringList(String section, String subsection,
			String name) {
		return state.get().get(section, subsection, name);
	}

	private ConfigSnapshot getState() {
		ConfigSnapshot cur, upd;
		do {
			cur = state.get();
			final ConfigSnapshot base = getBaseState();
			if (cur.baseState == base)
				return cur;
			upd = new ConfigSnapshot(cur.entryList, base);
		} while (!state.compareAndSet(cur, upd));
		return upd;
	}

	private ConfigSnapshot getBaseState() {
		return baseConfig != null ? baseConfig.getState() : null;
	}

	/**
	 * Add or modify a configuration value. The parameters will result in a
	 * configuration entry like this.
	 *
	 * <pre>
	 * [section &quot;subsection&quot;]
	 *         name = value
	 * </pre>
	 *
	 * @param section
	 *            section name, e.g "branch"
	 * @param subsection
	 *            optional subsection value, e.g. a branch name
	 * @param name
	 *            parameter name, e.g. "filemode"
	 * @param value
	 *            parameter value
	 */
	public void setInt(final String section, final String subsection,
			final String name, final int value) {
		setLong(section, subsection, name, value);
	}

	/**
	 * Add or modify a configuration value. The parameters will result in a
	 * configuration entry like this.
	 *
	 * <pre>
	 * [section &quot;subsection&quot;]
	 *         name = value
	 * </pre>
	 *
	 * @param section
	 *            section name, e.g "branch"
	 * @param subsection
	 *            optional subsection value, e.g. a branch name
	 * @param name
	 *            parameter name, e.g. "filemode"
	 * @param value
	 *            parameter value
	 */
	public void setLong(final String section, final String subsection,
			final String name, final long value) {
		setString(section, subsection, name,
				StringUtils.formatWithSuffix(value));
	}

	/**
	 * Add or modify a configuration value. The parameters will result in a
	 * configuration entry like this.
	 *
	 * <pre>
	 * [section &quot;subsection&quot;]
	 *         name = value
	 * </pre>
	 *
	 * @param section
	 *            section name, e.g "branch"
	 * @param subsection
	 *            optional subsection value, e.g. a branch name
	 * @param name
	 *            parameter name, e.g. "filemode"
	 * @param value
	 *            parameter value
	 */
	public void setBoolean(final String section, final String subsection,
			final String name, final boolean value) {
		setString(section, subsection, name, value ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Add or modify a configuration value. The parameters will result in a
	 * configuration entry like this.
	 *
	 * <pre>
	 * [section &quot;subsection&quot;]
	 *         name = value
	 * </pre>
	 *
	 * @param section
	 *            section name, e.g "branch"
	 * @param subsection
	 *            optional subsection value, e.g. a branch name
	 * @param name
	 *            parameter name, e.g. "filemode"
	 * @param value
	 *            parameter value
	 */
	public <T extends Enum<?>> void setEnum(final String section,
			final String subsection, final String name, final T value) {
		String n;
		if (value instanceof ConfigEnum)
			n = ((ConfigEnum) value).toConfigValue();
		else
			n = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		setString(section, subsection, name, n);
	}

	/**
	 * Add or modify a configuration value. The parameters will result in a
	 * configuration entry like this.
	 *
	 * <pre>
	 * [section &quot;subsection&quot;]
	 *         name = value
	 * </pre>
	 *
	 * @param section
	 *            section name, e.g "branch"
	 * @param subsection
	 *            optional subsection value, e.g. a branch name
	 * @param name
	 *            parameter name, e.g. "filemode"
	 * @param value
	 *            parameter value, e.g. "true"
	 */
	public void setString(final String section, final String subsection,
			final String name, final String value) {
		setStringList(section, subsection, name, Collections
				.singletonList(value));
	}

	/**
	 * Remove a configuration value.
	 *
	 * @param section
	 *            section name, e.g "branch"
	 * @param subsection
	 *            optional subsection value, e.g. a branch name
	 * @param name
	 *            parameter name, e.g. "filemode"
	 */
	public void unset(final String section, final String subsection,
			final String name) {
		setStringList(section, subsection, name, Collections
				.<String> emptyList());
	}

	/**
	 * Remove all configuration values under a single section.
	 *
	 * @param section
	 *            section name, e.g "branch"
	 * @param subsection
	 *            optional subsection value, e.g. a branch name
	 */
	public void unsetSection(String section, String subsection) {
		ConfigSnapshot src, res;
		do {
			src = state.get();
			res = unsetSection(src, section, subsection);
		} while (!state.compareAndSet(src, res));
	}

	private ConfigSnapshot unsetSection(final ConfigSnapshot srcState,
			final String section,
			final String subsection) {
		final int max = srcState.entryList.size();
		final ArrayList<ConfigLine> r = new ArrayList<>(max);

		boolean lastWasMatch = false;
		for (ConfigLine e : srcState.entryList) {
			if (e.includedFrom == null && e.match(section, subsection)) {
				// Skip this record, it's for the section we are removing.
				lastWasMatch = true;
				continue;
			}

			if (lastWasMatch && e.section == null && e.subsection == null)
				continue; // skip this padding line in the section.
			r.add(e);
		}

		return newState(r);
	}

	/**
	 * Set a configuration value.
	 *
	 * <pre>
	 * [section &quot;subsection&quot;]
	 *         name = value1
	 *         name = value2
	 * </pre>
	 *
	 * @param section
	 *            section name, e.g "branch"
	 * @param subsection
	 *            optional subsection value, e.g. a branch name
	 * @param name
	 *            parameter name, e.g. "filemode"
	 * @param values
	 *            list of zero or more values for this key.
	 */
	public void setStringList(final String section, final String subsection,
			final String name, final List<String> values) {
		ConfigSnapshot src, res;
		do {
			src = state.get();
			res = replaceStringList(src, section, subsection, name, values);
		} while (!state.compareAndSet(src, res));
		if (notifyUponTransientChanges())
			fireConfigChangedEvent();
	}

	private ConfigSnapshot replaceStringList(final ConfigSnapshot srcState,
			final String section, final String subsection, final String name,
			final List<String> values) {
		final List<ConfigLine> entries = copy(srcState, values);
		int entryIndex = 0;
		int valueIndex = 0;
		int insertPosition = -1;

		// Reset the first n Entry objects that match this input name.
		//
		while (entryIndex < entries.size() && valueIndex < values.size()) {
			final ConfigLine e = entries.get(entryIndex);
			if (e.includedFrom == null && e.match(section, subsection, name)) {
				entries.set(entryIndex, e.forValue(values.get(valueIndex++)));
				insertPosition = entryIndex + 1;
			}
			entryIndex++;
		}

		// Remove any extra Entry objects that we no longer need.
		//
		if (valueIndex == values.size() && entryIndex < entries.size()) {
			while (entryIndex < entries.size()) {
				final ConfigLine e = entries.get(entryIndex++);
				if (e.includedFrom == null
						&& e.match(section, subsection, name))
					entries.remove(--entryIndex);
			}
		}

		// Insert new Entry objects for additional/new values.
		//
		if (valueIndex < values.size() && entryIndex == entries.size()) {
			if (insertPosition < 0) {
				// We didn't find a matching key above, but maybe there
				// is already a section available that matches. Insert
				// after the last key of that section.
				//
				insertPosition = findSectionEnd(entries, section, subsection,
						true);
			}
			if (insertPosition < 0) {
				// We didn't find any matching section header for this key,
				// so we must create a new section header at the end.
				//
				final ConfigLine e = new ConfigLine();
				e.section = section;
				e.subsection = subsection;
				entries.add(e);
				insertPosition = entries.size();
			}
			while (valueIndex < values.size()) {
				final ConfigLine e = new ConfigLine();
				e.section = section;
				e.subsection = subsection;
				e.name = name;
				e.value = values.get(valueIndex++);
				entries.add(insertPosition++, e);
			}
		}

		return newState(entries);
	}

	private static List<ConfigLine> copy(final ConfigSnapshot src,
			final List<String> values) {
		// At worst we need to insert 1 line for each value, plus 1 line
		// for a new section header. Assume that and allocate the space.
		//
		final int max = src.entryList.size() + values.size() + 1;
		final ArrayList<ConfigLine> r = new ArrayList<>(max);
		r.addAll(src.entryList);
		return r;
	}

	private static int findSectionEnd(final List<ConfigLine> entries,
			final String section, final String subsection,
			boolean skipIncludedLines) {
		for (int i = 0; i < entries.size(); i++) {
			ConfigLine e = entries.get(i);
			if (e.includedFrom != null && skipIncludedLines) {
				continue;
			}

			if (e.match(section, subsection, null)) {
				i++;
				while (i < entries.size()) {
					e = entries.get(i);
					if (e.match(section, subsection, e.name))
						i++;
					else
						break;
				}
				return i;
			}
		}
		return -1;
	}

	/**
	 * Get this configuration, formatted as a Git style text file.
	 *
	 * @return this configuration, formatted as a Git style text file.
	 */
	public String toText() {
		final StringBuilder out = new StringBuilder();
		for (ConfigLine e : state.get().entryList) {
			if (e.includedFrom != null)
				continue;
			if (e.prefix != null)
				out.append(e.prefix);
			if (e.section != null && e.name == null) {
				out.append('[');
				out.append(e.section);
				if (e.subsection != null) {
					out.append(' ');
					String escaped = escapeValue(e.subsection);
					// make sure to avoid double quotes here
					boolean quoted = escaped.startsWith("\"") //$NON-NLS-1$
							&& escaped.endsWith("\""); //$NON-NLS-1$
					if (!quoted)
						out.append('"');
					out.append(escaped);
					if (!quoted)
						out.append('"');
				}
				out.append(']');
			} else if (e.section != null && e.name != null) {
				if (e.prefix == null || "".equals(e.prefix)) //$NON-NLS-1$
					out.append('\t');
				out.append(e.name);
				if (!isMissing(e.value)) {
					out.append(" ="); //$NON-NLS-1$
					if (e.value != null) {
						out.append(' ');
						out.append(escapeValue(e.value));
					}
				}
				if (e.suffix != null)
					out.append(' ');
			}
			if (e.suffix != null)
				out.append(e.suffix);
			out.append('\n');
		}
		return out.toString();
	}

	/**
	 * Clear this configuration and reset to the contents of the parsed string.
	 *
	 * @param text
	 *            Git style text file listing configuration properties.
	 * @throws org.eclipse.jgit.errors.ConfigInvalidException
	 *             the text supplied is not formatted correctly. No changes were
	 *             made to {@code this}.
	 */
	public void fromText(String text) throws ConfigInvalidException {
		state.set(newState(fromTextRecurse(text, 1, null)));
	}

	private List<ConfigLine> fromTextRecurse(String text, int depth,
			String includedFrom) throws ConfigInvalidException {
		if (depth > MAX_DEPTH) {
			throw new ConfigInvalidException(
					JGitText.get().tooManyIncludeRecursions);
		}
		final List<ConfigLine> newEntries = new ArrayList<>();
		final StringReader in = new StringReader(text);
		ConfigLine last = null;
		ConfigLine e = new ConfigLine();
		e.includedFrom = includedFrom;
		for (;;) {
			int input = in.read();
			if (-1 == input) {
				if (e.section != null)
					newEntries.add(e);
				break;
			}

			final char c = (char) input;
			if ('\n' == c) {
				// End of this entry.
				newEntries.add(e);
				if (e.section != null)
					last = e;
				e = new ConfigLine();
				e.includedFrom = includedFrom;
			} else if (e.suffix != null) {
				// Everything up until the end-of-line is in the suffix.
				e.suffix += c;

			} else if (';' == c || '#' == c) {
				// The rest of this line is a comment; put into suffix.
				e.suffix = String.valueOf(c);

			} else if (e.section == null && Character.isWhitespace(c)) {
				// Save the leading whitespace (if any).
				if (e.prefix == null)
					e.prefix = ""; //$NON-NLS-1$
				e.prefix += c;

			} else if ('[' == c) {
				// This is a section header.
				e.section = readSectionName(in);
				input = in.read();
				if ('"' == input) {
					e.subsection = readSubsectionName(in);
					input = in.read();
				}
				if (']' != input)
					throw new ConfigInvalidException(JGitText.get().badGroupHeader);
				e.suffix = ""; //$NON-NLS-1$

			} else if (last != null) {
				// Read a value.
				e.section = last.section;
				e.subsection = last.subsection;
				in.reset();
				e.name = readKeyName(in);
				if (e.name.endsWith("\n")) { //$NON-NLS-1$
					e.name = e.name.substring(0, e.name.length() - 1);
					e.value = MISSING_ENTRY;
				} else
					e.value = readValue(in);

				if (e.section.equalsIgnoreCase("include")) { //$NON-NLS-1$
					addIncludedConfig(newEntries, e, depth);
				}
			} else
				throw new ConfigInvalidException(JGitText.get().invalidLineInConfigFile);
		}

		return newEntries;
	}

	/**
	 * Read the included config from the specified (possibly) relative path
	 *
	 * @param relPath
	 *            possibly relative path to the included config, as specified in
	 *            this config
	 * @return the read bytes, or null if the included config should be ignored
	 * @throws org.eclipse.jgit.errors.ConfigInvalidException
	 *             if something went wrong while reading the config
	 * @since 4.10
	 */
	protected byte[] readIncludedConfig(String relPath)
			throws ConfigInvalidException {
		return null;
	}

	private void addIncludedConfig(final List<ConfigLine> newEntries,
			ConfigLine line, int depth) throws ConfigInvalidException {
		if (!line.name.equalsIgnoreCase("path") || //$NON-NLS-1$
				line.value == null || line.value.equals(MISSING_ENTRY)) {
			throw new ConfigInvalidException(MessageFormat.format(
					JGitText.get().invalidLineInConfigFileWithParam, line));
		}
		byte[] bytes = readIncludedConfig(line.value);
		if (bytes == null) {
			return;
		}

		String decoded;
		if (isUtf8(bytes)) {
			decoded = RawParseUtils.decode(UTF_8, bytes, 3, bytes.length);
		} else {
			decoded = RawParseUtils.decode(bytes);
		}
		try {
			newEntries.addAll(fromTextRecurse(decoded, depth + 1, line.value));
		} catch (ConfigInvalidException e) {
			throw new ConfigInvalidException(MessageFormat
					.format(JGitText.get().cannotReadFile, line.value), e);
		}
	}

	private ConfigSnapshot newState() {
		return new ConfigSnapshot(Collections.<ConfigLine> emptyList(),
				getBaseState());
	}

	private ConfigSnapshot newState(List<ConfigLine> entries) {
		return new ConfigSnapshot(Collections.unmodifiableList(entries),
				getBaseState());
	}

	/**
	 * Clear the configuration file
	 */
	protected void clear() {
		state.set(newState());
	}

	/**
	 * Check if bytes should be treated as UTF-8 or not.
	 *
	 * @param bytes
	 *            the bytes to check encoding for.
	 * @return true if bytes should be treated as UTF-8, false otherwise.
	 * @since 4.4
	 */
	protected boolean isUtf8(final byte[] bytes) {
		return bytes.length >= 3 && bytes[0] == (byte) 0xEF
				&& bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF;
	}

	private static String readSectionName(StringReader in)
			throws ConfigInvalidException {
		final StringBuilder name = new StringBuilder();
		for (;;) {
			int c = in.read();
			if (c < 0)
				throw new ConfigInvalidException(JGitText.get().unexpectedEndOfConfigFile);

			if (']' == c) {
				in.reset();
				break;
			}

			if (' ' == c || '\t' == c) {
				for (;;) {
					c = in.read();
					if (c < 0)
						throw new ConfigInvalidException(JGitText.get().unexpectedEndOfConfigFile);

					if ('"' == c) {
						in.reset();
						break;
					}

					if (' ' == c || '\t' == c)
						continue; // Skipped...
					throw new ConfigInvalidException(MessageFormat.format(JGitText.get().badSectionEntry, name));
				}
				break;
			}

			if (Character.isLetterOrDigit((char) c) || '.' == c || '-' == c)
				name.append((char) c);
			else
				throw new ConfigInvalidException(MessageFormat.format(JGitText.get().badSectionEntry, name));
		}
		return name.toString();
	}

	private static String readKeyName(StringReader in)
			throws ConfigInvalidException {
		final StringBuilder name = new StringBuilder();
		for (;;) {
			int c = in.read();
			if (c < 0)
				throw new ConfigInvalidException(JGitText.get().unexpectedEndOfConfigFile);

			if ('=' == c)
				break;

			if (' ' == c || '\t' == c) {
				for (;;) {
					c = in.read();
					if (c < 0)
						throw new ConfigInvalidException(JGitText.get().unexpectedEndOfConfigFile);

					if ('=' == c)
						break;

					if (';' == c || '#' == c || '\n' == c) {
						in.reset();
						break;
					}

					if (' ' == c || '\t' == c)
						continue; // Skipped...
					throw new ConfigInvalidException(JGitText.get().badEntryDelimiter);
				}
				break;
			}

			if (Character.isLetterOrDigit((char) c) || c == '-') {
				// From the git-config man page:
				// The variable names are case-insensitive and only
				// alphanumeric characters and - are allowed.
				name.append((char) c);
			} else if ('\n' == c) {
				in.reset();
				name.append((char) c);
				break;
			} else
				throw new ConfigInvalidException(MessageFormat.format(JGitText.get().badEntryName, name));
		}
		return name.toString();
	}

	private static String readSubsectionName(StringReader in)
			throws ConfigInvalidException {
		StringBuilder r = new StringBuilder();
		for (;;) {
			int c = in.read();
			if (c < 0) {
				break;
			}

			if ('\n' == c) {
				throw new ConfigInvalidException(
						JGitText.get().newlineInQuotesNotAllowed);
			}
			if ('\\' == c) {
				c = in.read();
				switch (c) {
				case -1:
					throw new ConfigInvalidException(JGitText.get().endOfFileInEscape);

				case '\\':
				case '"':
					r.append((char) c);
					continue;

				default:
					// C git simply drops backslashes if the escape sequence is not
					// recognized.
					r.append((char) c);
					continue;
				}
			}
			if ('"' == c) {
				break;
			}

			r.append((char) c);
		}
		return r.toString();
	}

	private static String readValue(StringReader in)
			throws ConfigInvalidException {
		StringBuilder value = new StringBuilder();
		StringBuilder trailingSpaces = null;
		boolean quote = false;
		boolean inLeadingSpace = true;

		for (;;) {
			int c = in.read();
			if (c < 0) {
				break;
			}
			if ('\n' == c) {
				if (quote) {
					throw new ConfigInvalidException(
							JGitText.get().newlineInQuotesNotAllowed);
				}
				in.reset();
				break;
			}

			if (!quote && (';' == c || '#' == c)) {
				if (trailingSpaces != null) {
					trailingSpaces.setLength(0);
				}
				in.reset();
				break;
			}

			char cc = (char) c;
			if (Character.isWhitespace(cc)) {
				if (inLeadingSpace) {
					continue;
				}
				if (trailingSpaces == null) {
					trailingSpaces = new StringBuilder();
				}
				trailingSpaces.append(cc);
				continue;
			}
			inLeadingSpace = false;
			if (trailingSpaces != null) {
				value.append(trailingSpaces);
				trailingSpaces.setLength(0);
			}

			if ('\\' == c) {
				c = in.read();
				switch (c) {
				case -1:
					throw new ConfigInvalidException(JGitText.get().endOfFileInEscape);
				case '\n':
					continue;
				case 't':
					value.append('\t');
					continue;
				case 'b':
					value.append('\b');
					continue;
				case 'n':
					value.append('\n');
					continue;
				case '\\':
					value.append('\\');
					continue;
				case '"':
					value.append('"');
					continue;
				case '\r': {
					int next = in.read();
					if (next == '\n') {
						continue; // CR-LF
					} else if (next >= 0) {
						in.reset();
					}
					break;
				}
				default:
					break;
				}
				throw new ConfigInvalidException(
						MessageFormat.format(JGitText.get().badEscape,
								Character.isAlphabetic(c)
										? Character.valueOf(((char) c))
										: toUnicodeLiteral(c)));
			}

			if ('"' == c) {
				quote = !quote;
				continue;
			}

			value.append(cc);
		}
		return value.length() > 0 ? value.toString() : null;
	}

	private static String toUnicodeLiteral(int c) {
		return String.format("\\u%04x", //$NON-NLS-1$
				Integer.valueOf(c));
	}

	/**
	 * Parses a section of the configuration into an application model object.
	 * <p>
	 * Instances must implement hashCode and equals such that model objects can
	 * be cached by using the {@code SectionParser} as a key of a HashMap.
	 * <p>
	 * As the {@code SectionParser} itself is used as the key of the internal
	 * HashMap applications should be careful to ensure the SectionParser key
	 * does not retain unnecessary application state which may cause memory to
	 * be held longer than expected.
	 *
	 * @param <T>
	 *            type of the application model created by the parser.
	 */
	public static interface SectionParser<T> {
		/**
		 * Create a model object from a configuration.
		 *
		 * @param cfg
		 *            the configuration to read values from.
		 * @return the application model instance.
		 */
		T parse(Config cfg);
	}

	private static class StringReader {
		private final char[] buf;

		private int pos;

		StringReader(String in) {
			buf = in.toCharArray();
		}

		int read() {
			if (pos >= buf.length) {
				return -1;
			}
			return buf[pos++];
		}

		void reset() {
			pos--;
		}
	}

	/**
	 * Converts enumeration values into configuration options and vice-versa,
	 * allowing to match a config option with an enum value.
	 *
	 */
	public static interface ConfigEnum {
		/**
		 * Converts enumeration value into a string to be save in config.
		 *
		 * @return the enum value as config string
		 */
		String toConfigValue();

		/**
		 * Checks if the given string matches with enum value.
		 *
		 * @param in
		 *            the string to match
		 * @return true if the given string matches enum value, false otherwise
		 */
		boolean matchConfigValue(String in);
	}
}
