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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;

/**
 * Interface to read values from the system.
 * <p>
 * When writing unit tests, extending this interface with a custom class
 * permits to simulate an access to a system variable or property and
 * permits to control the user's global configuration.
 * </p>
 */
public abstract class SystemReader {
	private static SystemReader DEFAULT = new SystemReader() {
		private volatile String hostname;

		public String getenv(String variable) {
			return System.getenv(variable);
		}

		public String getProperty(String key) {
			return System.getProperty(key);
		}

		public FileBasedConfig openSystemConfig(Config parent, FS fs) {
			File prefix = fs.gitPrefix();
			if (prefix == null) {
				return new FileBasedConfig(null, fs) {
					public void load() {
						// empty, do not load
					}

					public boolean isOutdated() {
						// regular class would bomb here
						return false;
					}
				};
			}
			File etc = fs.resolve(prefix, "etc"); //$NON-NLS-1$
			File config = fs.resolve(etc, "gitconfig"); //$NON-NLS-1$
			return new FileBasedConfig(parent, config, fs);
		}

		public FileBasedConfig openUserConfig(Config parent, FS fs) {
			final File home = fs.userHome();
			return new FileBasedConfig(parent, new File(home, ".gitconfig"), fs); //$NON-NLS-1$
		}

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
	};

	private static SystemReader INSTANCE = DEFAULT;

	/** @return the live instance to read system properties. */
	public static SystemReader getInstance() {
		return INSTANCE;
	}

	/**
	 * @param newReader
	 *            the new instance to use when accessing properties.
	 */
	public static void setInstance(SystemReader newReader) {
		if (newReader == null)
			INSTANCE = DEFAULT;
		else
			INSTANCE = newReader;
	}

	/**
	 * Gets the hostname of the local host. If no hostname can be found, the
	 * hostname is set to the default value "localhost".
	 *
	 * @return the canonical hostname
	 */
	public abstract String getHostname();

	/**
	 * @param variable system variable to read
	 * @return value of the system variable
	 */
	public abstract String getenv(String variable);

	/**
	 * @param key of the system property to read
	 * @return value of the system property
	 */
	public abstract String getProperty(String key);

	/**
	 * @param parent
	 *            a config with values not found directly in the returned config
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 * @return the git configuration found in the user home
	 */
	public abstract FileBasedConfig openUserConfig(Config parent, FS fs);

	/**
	 * @param parent
	 *            a config with values not found directly in the returned
	 *            config. Null is a reasonable value here.
	 * @param fs
	 *            the file system abstraction which will be necessary to perform
	 *            certain file system operations.
	 * @return the gitonfig configuration found in the system-wide "etc"
	 *         directory
	 */
	public abstract FileBasedConfig openSystemConfig(Config parent, FS fs);

	/**
	 * @return the current system time
	 */
	public abstract long getCurrentTime();

	/**
	 * @param when TODO
	 * @return the local time zone
	 */
	public abstract int getTimezone(long when);

	/**
	 * @return system time zone, possibly mocked for testing
	 * @since 1.2
	 */
	public TimeZone getTimeZone() {
		return TimeZone.getDefault();
	}

	/**
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
	 *            {@link SimpleDateFormat#SimpleDateFormat(String)}
	 * @return the simple date format
	 * @since 2.0
	 */
	public SimpleDateFormat getSimpleDateFormat(String pattern) {
		return new SimpleDateFormat(pattern);
	}

	/**
	 * Returns a date/time format instance for the given styles.
	 *
	 * @param dateStyle
	 *            the date style as specified in
	 *            {@link DateFormat#getDateTimeInstance(int, int)}
	 * @param timeStyle
	 *            the time style as specified in
	 *            {@link DateFormat#getDateTimeInstance(int, int)}
	 * @return the date format
	 * @since 2.0
	 */
	public DateFormat getDateTimeInstance(int dateStyle, int timeStyle) {
		return DateFormat.getDateTimeInstance(dateStyle, timeStyle);
	}

	/**
	 * @return true if we are running on a Windows.
	 */
	public boolean isWindows() {
		String osDotName = AccessController
				.doPrivileged(new PrivilegedAction<String>() {
					public String run() {
						return getProperty("os.name"); //$NON-NLS-1$
					}
				});
		return osDotName.startsWith("Windows"); //$NON-NLS-1$
	}

	/**
	 * @return true if we are running on Mac OS X
	 */
	public boolean isMacOS() {
		String osDotName = AccessController
				.doPrivileged(new PrivilegedAction<String>() {
					public String run() {
						return getProperty("os.name"); //$NON-NLS-1$
					}
				});
		return "Mac OS X".equals(osDotName) || "Darwin".equals(osDotName); //$NON-NLS-1$ //$NON-NLS-2$
	}

}
