/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2009, Yann Simon <yann.simon.fr@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.junit;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jgit.util.time.MonotonicClock;
import org.eclipse.jgit.util.time.ProposedTimestamp;

/**
 * Mock {@link org.eclipse.jgit.util.SystemReader} for tests.
 */
public class MockSystemReader extends SystemReader {
	private static final class MockConfig extends FileBasedConfig {
		private MockConfig(File cfgLocation, FS fs) {
			super(cfgLocation, fs);
		}

		@Override
		public void load() throws IOException, ConfigInvalidException {
			// Do nothing
		}

		@Override
		public void save() throws IOException {
			// Do nothing
		}

		@Override
		public boolean isOutdated() {
			return false;
		}

		@Override
		public String toString() {
			return "MockConfig";
		}
	}

	long now = 1250379778668L; // Sat Aug 15 20:12:58 GMT-03:30 2009

	final Map<String, String> values = new HashMap<>();

	private FileBasedConfig userGitConfig;

	private FileBasedConfig jgitConfig;

	FileBasedConfig systemGitConfig;

	/**
	 * Set the user-level git config
	 *
	 * @param userGitConfig
	 *            set another user-level git config
	 * @return the old user-level git config
	 */
	public FileBasedConfig setUserGitConfig(FileBasedConfig userGitConfig) {
		FileBasedConfig old = this.userGitConfig;
		this.userGitConfig = userGitConfig;
		return old;
	}

	/**
	 * Set the jgit config stored at $XDG_CONFIG_HOME/jgit/config
	 *
	 * @param jgitConfig
	 *            set the jgit configuration
	 */
	public void setJGitConfig(FileBasedConfig jgitConfig) {
		this.jgitConfig = jgitConfig;
	}

	/**
	 * Set the system-level git config
	 *
	 * @param systemGitConfig
	 *            the new system-level git config
	 * @return the old system-level config
	 */
	public FileBasedConfig setSystemGitConfig(FileBasedConfig systemGitConfig) {
		FileBasedConfig old = this.systemGitConfig;
		this.systemGitConfig = systemGitConfig;
		return old;
	}

	/**
	 * Constructor for <code>MockSystemReader</code>
	 */
	public MockSystemReader() {
		init(Constants.OS_USER_NAME_KEY);
		init(Constants.GIT_AUTHOR_NAME_KEY);
		init(Constants.GIT_AUTHOR_EMAIL_KEY);
		init(Constants.GIT_COMMITTER_NAME_KEY);
		init(Constants.GIT_COMMITTER_EMAIL_KEY);
		setProperty(Constants.OS_USER_DIR, ".");
		userGitConfig = new MockConfig(null, null);
		jgitConfig = new MockConfig(null, null);
		systemGitConfig = new MockConfig(null, null);
		setCurrentPlatform();
	}

	private void init(String n) {
		setProperty(n, n);
	}

	/**
	 * Clear properties
	 */
	public void clearProperties() {
		values.clear();
	}

	/**
	 * Set a property
	 *
	 * @param key
	 * @param value
	 */
	public void setProperty(String key, String value) {
		values.put(key, value);
	}

	/** {@inheritDoc} */
	@Override
	public String getenv(String variable) {
		return values.get(variable);
	}

	/** {@inheritDoc} */
	@Override
	public String getProperty(String key) {
		return values.get(key);
	}

	/** {@inheritDoc} */
	@Override
	public FileBasedConfig openUserConfig(Config parent, FS fs) {
		assert parent == null || parent == systemGitConfig;
		return userGitConfig;
	}

	/** {@inheritDoc} */
	@Override
	public FileBasedConfig openSystemConfig(Config parent, FS fs) {
		assert parent == null;
		return systemGitConfig;
	}

	@Override
	public StoredConfig getUserConfig()
			throws IOException, ConfigInvalidException {
		return userGitConfig;
	}

	@Override
	public FileBasedConfig getJGitConfig() {
		return jgitConfig;
	}

	@Override
	public StoredConfig getSystemConfig()
			throws IOException, ConfigInvalidException {
		return systemGitConfig;
	}

	/** {@inheritDoc} */
	@Override
	public String getHostname() {
		return "fake.host.example.com";
	}

	/** {@inheritDoc} */
	@Override
	public long getCurrentTime() {
		return now;
	}

	/** {@inheritDoc} */
	@Override
	public MonotonicClock getClock() {
		return () -> {
			long t = getCurrentTime();
			return new ProposedTimestamp() {

				@Override
				public long read(TimeUnit unit) {
					return unit.convert(t, TimeUnit.MILLISECONDS);
				}

				@Override
				public void blockUntil(Duration maxWait) {
					// Do not wait.
				}
			};
		};
	}

	/**
	 * Adjusts the current time in seconds.
	 *
	 * @param secDelta
	 *            number of seconds to add to the current time.
	 * @since 4.2
	 */
	public void tick(int secDelta) {
		now += secDelta * 1000L;
	}

	/** {@inheritDoc} */
	@Override
	public int getTimezone(long when) {
		return getTimeZone().getOffset(when) / (60 * 1000);
	}

	/** {@inheritDoc} */
	@Override
	public TimeZone getTimeZone() {
		return TimeZone.getTimeZone("GMT-03:30");
	}

	/** {@inheritDoc} */
	@Override
	public Locale getLocale() {
		return Locale.US;
	}

	/** {@inheritDoc} */
	@Override
	public SimpleDateFormat getSimpleDateFormat(String pattern) {
		return new SimpleDateFormat(pattern, getLocale());
	}

	/** {@inheritDoc} */
	@Override
	public DateFormat getDateTimeInstance(int dateStyle, int timeStyle) {
		return DateFormat
				.getDateTimeInstance(dateStyle, timeStyle, getLocale());
	}

	/**
	 * Assign some properties for the currently executing platform
	 */
	public void setCurrentPlatform() {
		resetOsNames();
		setProperty("os.name", System.getProperty("os.name"));
		setProperty("file.separator", System.getProperty("file.separator"));
		setProperty("path.separator", System.getProperty("path.separator"));
		setProperty("line.separator", System.getProperty("line.separator"));
	}

	/**
	 * Emulate Windows
	 */
	public void setWindows() {
		resetOsNames();
		setProperty("os.name", "Windows");
		setProperty("file.separator", "\\");
		setProperty("path.separator", ";");
		setProperty("line.separator", "\r\n");
		setPlatformChecker();
	}

	/**
	 * Emulate Unix
	 */
	public void setUnix() {
		resetOsNames();
		setProperty("os.name", "*nix"); // Essentially anything but Windows
		setProperty("file.separator", "/");
		setProperty("path.separator", ":");
		setProperty("line.separator", "\n");
		setPlatformChecker();
	}

	private void resetOsNames() {
		Field field;
		try {
			field = SystemReader.class.getDeclaredField("isWindows");
			field.setAccessible(true);
			field.set(null, null);
			field = SystemReader.class.getDeclaredField("isMacOS");
			field.setAccessible(true);
			field.set(null, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public String toString() {
		return "MockSystemReader";
	}

	@Override
	public FileBasedConfig openJGitConfig(Config parent, FS fs) {
		return jgitConfig;
	}

}
