/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2009, Yann Simon <yann.simon.fr@gmail.com>
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
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jgit.util.time.MonotonicClock;
import org.eclipse.jgit.util.time.ProposedTimestamp;

/**
 * Mock {@link org.eclipse.jgit.util.SystemReader} for tests.
 */
public class MockSystemReader extends SystemReader {
	private final class MockConfig extends FileBasedConfig {
		private MockConfig(File cfgLocation, FS fs) {
			super(cfgLocation, fs);
		}

		@Override
		public void load() throws IOException, ConfigInvalidException {
			// Do nothing
		}

		@Override
		public boolean isOutdated() {
			return false;
		}
	}

	long now = 1250379778668L; // Sat Aug 15 20:12:58 GMT-03:30 2009

	final Map<String, String> values = new HashMap<>();

	FileBasedConfig userGitConfig;

	FileBasedConfig systemGitConfig;

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
		systemGitConfig = new MockConfig(null, null);
		setCurrentPlatform();
	}

	private void init(final String n) {
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
		return new MonotonicClock() {
			@Override
			public ProposedTimestamp propose() {
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
			}
		};
	}

	/**
	 * Adjusts the current time in seconds.
	 *
	 * @param secDelta
	 *            number of seconds to add to the current time.
	 * @since 4.2
	 */
	public void tick(final int secDelta) {
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
}
