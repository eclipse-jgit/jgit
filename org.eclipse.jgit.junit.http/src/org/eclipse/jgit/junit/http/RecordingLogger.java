/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.junit.http;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.util.log.Logger;

/**
 * Log warnings into an array for later inspection.
 */
public class RecordingLogger implements Logger {
	private static List<Warning> warnings = new ArrayList<>();

	/**
	 * Clear the warnings, automatically done by
	 * {@link org.eclipse.jgit.junit.http.AppServer#setUp()}
	 */
	public static void clear() {
		synchronized (warnings) {
			warnings.clear();
		}
	}

	/**
	 * Get the <code>warnings</code>.
	 *
	 * @return the warnings (if any) from the last execution
	 */
	public static List<Warning> getWarnings() {
		synchronized (warnings) {
			ArrayList<Warning> copy = new ArrayList<>(warnings);
			return Collections.unmodifiableList(copy);
		}
	}

	@SuppressWarnings("serial")
	public static class Warning extends Exception {
		public Warning(String msg) {
			super(msg);
		}

		public Warning(String msg, Throwable cause) {
			super(msg, cause);
		}

		public Warning(Throwable thrown) {
			super(thrown);
		}
	}

	private final String name;

	/**
	 * Constructor for <code>RecordingLogger</code>.
	 */
	public RecordingLogger() {
		this("");
	}

	/**
	 * Constructor for <code>RecordingLogger</code>.
	 *
	 * @param name
	 */
	public RecordingLogger(String name) {
		this.name = name;
	}

	/** {@inheritDoc} */
	@Override
	public Logger getLogger(@SuppressWarnings("hiding") String name) {
		return new RecordingLogger(name);
	}

	/** {@inheritDoc} */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * Warning
	 *
	 * @param msg
	 * @param arg0
	 * @param arg1
	 */
	public void warn(String msg, Object arg0, Object arg1) {
		synchronized (warnings) {
			warnings.add(new Warning(MessageFormat.format(msg, arg0, arg1)));
		}
	}

	/** {@inheritDoc} */
	@Override
	public void warn(String msg, Throwable th) {
		synchronized (warnings) {
			warnings.add(new Warning(msg, th));
		}
	}

	/**
	 * Warning
	 *
	 * @param msg
	 *            warning message
	 */
	public void warn(String msg) {
		synchronized (warnings) {
			warnings.add(new Warning(msg));
		}
	}

	/**
	 * Debug log
	 *
	 * @param msg
	 * @param arg0
	 * @param arg1
	 */
	public void debug(String msg, Object arg0, Object arg1) {
		// Ignore (not relevant to test failures)
	}

	/** {@inheritDoc} */
	@Override
	public void debug(String msg, Throwable th) {
		// Ignore (not relevant to test failures)
	}

	/**
	 * Debug log
	 *
	 * @param msg
	 *            debug message
	 */
	public void debug(String msg) {
		// Ignore (not relevant to test failures)
	}

	/**
	 * Info
	 *
	 * @param msg
	 * @param arg0
	 * @param arg1
	 */
	public void info(String msg, Object arg0, Object arg1) {
		// Ignore (not relevant to test failures)
	}

	/**
	 * Info
	 *
	 * @param msg
	 */
	public void info(String msg) {
		// Ignore (not relevant to test failures)
	}

	/** {@inheritDoc} */
	@Override
	public boolean isDebugEnabled() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public void setDebugEnabled(boolean enabled) {
		// Ignore (not relevant to test failures)
	}

	/** {@inheritDoc} */
	@Override
	public void warn(String msg, Object... args) {
		synchronized (warnings) {
			int i = 0;
			int index = msg.indexOf("{}");
			while (index >= 0) {
				msg = msg.replaceFirst("\\{\\}", "{" + i++ + "}");
				index = msg.indexOf("{}");
			}
			warnings.add(new Warning(MessageFormat.format(msg, args)));
		}
	}

	/** {@inheritDoc} */
	@Override
	public void warn(Throwable thrown) {
		synchronized (warnings) {
			warnings.add(new Warning(thrown));
		}
	}

	/** {@inheritDoc} */
	@Override
	public void info(String msg, Object... args) {
		// Ignore (not relevant to test failures)
	}

	/** {@inheritDoc} */
	@Override
	public void info(Throwable thrown) {
		// Ignore (not relevant to test failures)
	}

	/** {@inheritDoc} */
	@Override
	public void info(String msg, Throwable thrown) {
		// Ignore (not relevant to test failures)
	}

	/** {@inheritDoc} */
	@Override
	public void debug(String msg, Object... args) {
		// Ignore (not relevant to test failures)
	}

	/** {@inheritDoc} */
	@Override
	public void debug(Throwable thrown) {
		// Ignore (not relevant to test failures)
	}

	/** {@inheritDoc} */
	@Override
	public void ignore(Throwable arg0) {
		// Ignore (not relevant to test failures)
	}

	/** {@inheritDoc} */
	@Override
	public void debug(String msg, long value) {
		// Ignore (not relevant to test failures)
	}
}
