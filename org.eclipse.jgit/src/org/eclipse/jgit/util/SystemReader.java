/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2009, Yann Simon <yann.simon.fr@gmail.com>
 * Copyright (C) 2012, Daniel Megert <daniel_megert@ch.ibm.com>
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

package org.eclipse.jgit.util;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.time.MonotonicClock;
import org.eclipse.jgit.util.time.MonotonicSystemClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interface to read values from the system.
 * <p>
 * When writing unit tests, extending this interface with a custom class
 * permits to simulate an access to a system variable or property and
 * permits to control the user's global configuration.
 * </p>
 */
public abstract class SystemReader {

	private final static Logger LOG = LoggerFactory
			.getLogger(SystemReader.class);

	private static final SystemReader DEFAULT;

	private static Boolean isMacOS;

	private static Boolean isWindows;

	static {
		SystemReader r = new Default();
		r.init();
		DEFAULT = r;
	}

	private static class Default extends SystemReader {
		private volatile String hostname;

		private volatile FileBasedConfig systemConfig;

		private volatile FileBasedConfig userConfig;

		@Override
		public String getenv(String variable) {
			return System.getenv(variable);
		}

		@Override
		public String getProperty(String key) {
			return System.getProperty(key);
		}

		@Override
		public FileBasedConfig openSystemConfig(Config parent, FS fs) {
			if (systemConfig == null) {
				systemConfig = createSystemConfig(parent, fs);
			}
			return systemConfig;
		}

		protected FileBasedConfig createSystemConfig(Config parent, FS fs) {
			if (StringUtils.isEmptyOrNull(getenv(Constants.GIT_CONFIG_NOSYSTEM_KEY))) {
				File configFile = fs.getGitSystemConfig();
				if (configFile != null) {
					return new FileBasedConfig(parent, configFile, fs);
				}
			}
			return new FileBasedConfig(parent, null, fs) {
				@Override
				public void load() {
					// empty, do not load
				}

				@Override
				public boolean isOutdated() {
					// regular class would bomb here
					return false;
				}
			};
		}

		@Override
		public FileBasedConfig openUserConfig(Config parent, FS fs) {
			if (userConfig == null) {
				File home = fs.userHome();
				userConfig = new FileBasedConfig(parent,
						new File(home, ".gitconfig"), fs); //$NON-NLS-1$
			}
			return userConfig;
		}

		@Override
		public StoredConfig getSystemConfig()
				throws IOException, ConfigInvalidException {
			if (systemConfig == null) {
				systemConfig = createSystemConfig(null, FS.DETECTED);
			}
			if (systemConfig.isOutdated()) {
				LOG.debug("loading system config {}", systemConfig); //$NON-NLS-1$
				systemConfig.load();
			}
			return systemConfig;
		}

		@Override
		public StoredConfig getUserConfig()
				throws IOException, ConfigInvalidException {
			if (userConfig == null) {
				userConfig = openUserConfig(getSystemConfig(), FS.DETECTED);
			} else {
				getSystemConfig();
			}
			if (userConfig.isOutdated()) {
				LOG.debug("loading user config {}", userConfig); //$NON-NLS-1$
				userConfig.load();
			}
			return userConfig;
		}

		@Override
		public String getHostname() {
			if (hostname == null) {
				try {
					InetAddress localMachine = InetAddress.getLocalHost();
					hostname = localMachine.getCanonicalHostName();
				} catch (UnknownHostException e) {
					// we do nothing
					hostname = "localhost"; //$NON-NLS-1$
				}
				assert hostname != null;
			}
			return hostname;
		}

		@Override
		public long getCurrentTime() {
			return System.currentTimeMillis();
		}

		@Override
		public int getTimezone(long when) {
			return getTimeZone().getOffset(when) / (60 * 1000);
		}
	}

	private static volatile SystemReader INSTANCE = DEFAULT;

	/**
	 * Get the current SystemReader instance
	 *
	 * @return the current SystemReader instance.
	 */
	public static SystemReader getInstance() {
		return INSTANCE;
	}

	/**
	 * Set a new SystemReader instance to use when accessing properties.
	 *
	 * @param newReader
	 *            the new instance to use when accessing properties, or null for
	 *            the default instance.
	 */
	public static void setInstance(SystemReader newReader) {
		isMacOS = null;
		isWindows = null;
		if (newReader == null)
			INSTANCE = DEFAULT;
		else {
			newReader.init();
			INSTANCE = newReader;
		}
	}

	private ObjectChecker platformChecker;

	private void init() {
		// Creating ObjectChecker must be deferred. Unit tests change
		// behavior of is{Windows,MacOS} in constructor of subclass.
		if (platformChecker == null)
			setPlatformChecker();
	}

	/**
	 * Should be used in tests when the platform is explicitly changed.
	 *
	 * @since 3.6
	 */
	protected final void setPlatformChecker() {
		platformChecker = new ObjectChecker()
			.setSafeForWindows(isWindows())
			.setSafeForMacOS(isMacOS());
	}

	/**
	 * Gets the hostname of the local host. If no hostname can be found, the
	 * hostname is set to the default value "localhost".
	 *
	 * @return the canonical hostname
	 */
	public abstract String getHostname();

	/**
	 * Get value of the system variable
	 *
	 * @param variable
	 *            system variable to read
	 * @return value of the system variable
	 */
	public abstract String getenv(String variable);

	/**
	 * Get value of the system property
	 *
	 * @param key
	 *            of the system property to read
	 * @return value of the system property
	 */
	public abstract String getProperty(String key);

	/**
	 * Open the git configuration found in the user home. Use
	 * {@link #getUserConfig()} to get the current git configuration in the user
	 * home since it manages automatic reloading when the gitconfig file was
	 * modified and avoids unnecessary reloads.
	 *
	 * @param parent
	 *            a config with values not found directly in the returned config
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 * @return the git configuration found in the user home
	 */
	public abstract FileBasedConfig openUserConfig(Config parent, FS fs);

	/**
	 * Open the gitconfig configuration found in the system-wide "etc"
	 * directory. Use {@link #getSystemConfig()} to get the current system-wide
	 * git configuration since it manages automatic reloading when the gitconfig
	 * file was modified and avoids unnecessary reloads.
	 *
	 * @param parent
	 *            a config with values not found directly in the returned
	 *            config. Null is a reasonable value here.
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 * @return the gitconfig configuration found in the system-wide "etc"
	 *         directory
	 */
	public abstract FileBasedConfig openSystemConfig(Config parent, FS fs);

	/**
	 * Get the git configuration found in the user home. The configuration will
	 * be reloaded automatically if the configuration file was modified. Also
	 * reloads the system config if the system config file was modified. If the
	 * configuration file wasn't modified returns the cached configuration.
	 *
	 * @return the git configuration found in the user home
	 * @throws ConfigInvalidException
	 *             if configuration is invalid
	 * @throws IOException
	 *             if something went wrong when reading files
	 * @since 5.1.9
	 */
	public abstract StoredConfig getUserConfig()
			throws IOException, ConfigInvalidException;

	/**
	 * Get the gitconfig configuration found in the system-wide "etc" directory.
	 * The configuration will be reloaded automatically if the configuration
	 * file was modified otherwise returns the cached system level config.
	 *
	 * @return the gitconfig configuration found in the system-wide "etc"
	 *         directory
	 * @throws ConfigInvalidException
	 *             if configuration is invalid
	 * @throws IOException
	 *             if something went wrong when reading files
	 * @since 5.1.9
	 */
	public abstract StoredConfig getSystemConfig()
			throws IOException, ConfigInvalidException;

	/**
	 * Get the current system time
	 *
	 * @return the current system time
	 */
	public abstract long getCurrentTime();

	/**
	 * Get clock instance preferred by this system.
	 *
	 * @return clock instance preferred by this system.
	 * @since 4.6
	 */
	public MonotonicClock getClock() {
		return new MonotonicSystemClock();
	}

	/**
	 * Get the local time zone
	 *
	 * @param when
	 *            a system timestamp
	 * @return the local time zone
	 */
	public abstract int getTimezone(long when);

	/**
	 * Get system time zone, possibly mocked for testing
	 *
	 * @return system time zone, possibly mocked for testing
	 * @since 1.2
	 */
	public TimeZone getTimeZone() {
		return TimeZone.getDefault();
	}

	/**
	 * Get the locale to use
	 *
	 * @return the locale to use
	 * @since 1.2
	 */
	public Locale getLocale() {
		return Locale.getDefault();
	}

	/**
	 * Returns a simple date format instance as specified by the given pattern.
	 *
	 * @param pattern
	 *            the pattern as defined in
	 *            {@link java.text.SimpleDateFormat#SimpleDateFormat(String)}
	 * @return the simple date format
	 * @since 2.0
	 */
	public SimpleDateFormat getSimpleDateFormat(String pattern) {
		return new SimpleDateFormat(pattern);
	}

	/**
	 * Returns a simple date format instance as specified by the given pattern.
	 *
	 * @param pattern
	 *            the pattern as defined in
	 *            {@link java.text.SimpleDateFormat#SimpleDateFormat(String)}
	 * @param locale
	 *            locale to be used for the {@code SimpleDateFormat}
	 * @return the simple date format
	 * @since 3.2
	 */
	public SimpleDateFormat getSimpleDateFormat(String pattern, Locale locale) {
		return new SimpleDateFormat(pattern, locale);
	}

	/**
	 * Returns a date/time format instance for the given styles.
	 *
	 * @param dateStyle
	 *            the date style as specified in
	 *            {@link java.text.DateFormat#getDateTimeInstance(int, int)}
	 * @param timeStyle
	 *            the time style as specified in
	 *            {@link java.text.DateFormat#getDateTimeInstance(int, int)}
	 * @return the date format
	 * @since 2.0
	 */
	public DateFormat getDateTimeInstance(int dateStyle, int timeStyle) {
		return DateFormat.getDateTimeInstance(dateStyle, timeStyle);
	}

	/**
	 * Whether we are running on Windows.
	 *
	 * @return true if we are running on Windows.
	 */
	public boolean isWindows() {
		if (isWindows == null) {
			String osDotName = getOsName();
			isWindows = Boolean.valueOf(osDotName.startsWith("Windows")); //$NON-NLS-1$
		}
		return isWindows.booleanValue();
	}

	/**
	 * Whether we are running on Mac OS X
	 *
	 * @return true if we are running on Mac OS X
	 */
	public boolean isMacOS() {
		if (isMacOS == null) {
			String osDotName = getOsName();
			isMacOS = Boolean.valueOf(
					"Mac OS X".equals(osDotName) || "Darwin".equals(osDotName)); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return isMacOS.booleanValue();
	}

	private String getOsName() {
		return AccessController.doPrivileged(new PrivilegedAction<String>() {
			@Override
			public String run() {
				return getProperty("os.name"); //$NON-NLS-1$
			}
		});
	}

	/**
	 * Check tree path entry for validity.
	 * <p>
	 * Scans a multi-directory path string such as {@code "src/main.c"}.
	 *
	 * @param path path string to scan.
	 * @throws org.eclipse.jgit.errors.CorruptObjectException path is invalid.
	 * @since 3.6
	 */
	public void checkPath(String path) throws CorruptObjectException {
		platformChecker.checkPath(path);
	}

	/**
	 * Check tree path entry for validity.
	 * <p>
	 * Scans a multi-directory path string such as {@code "src/main.c"}.
	 *
	 * @param path
	 *            path string to scan.
	 * @throws org.eclipse.jgit.errors.CorruptObjectException
	 *             path is invalid.
	 * @since 4.2
	 */
	public void checkPath(byte[] path) throws CorruptObjectException {
		platformChecker.checkPath(path, 0, path.length);
	}
}
