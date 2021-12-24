/*
 * Copyright (C) 2010, 2021 Google Inc. and others
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

import org.slf4j.helpers.MarkerIgnoringBase;

public class RecordingLogger extends MarkerIgnoringBase {

	private static final long serialVersionUID = 1L;

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

	@Override
	public boolean isTraceEnabled() {
		// Ignore (not relevant to test failures)
		return false;
	}

	@Override
	public void trace(String msg) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void trace(String format, Object arg) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void trace(String format, Object arg1, Object arg2) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void trace(String format, Object... arguments) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void trace(String msg, Throwable t) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public boolean isDebugEnabled() {
		return false;
	}

	@Override
	public void debug(String msg) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void debug(String format, Object arg) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void debug(String format, Object arg1, Object arg2) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void debug(String format, Object... arguments) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void debug(String msg, Throwable t) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public boolean isInfoEnabled() {
		return false;
	}

	@Override
	public void info(String msg) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void info(String format, Object arg) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void info(String format, Object arg1, Object arg2) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void info(String format, Object... arguments) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void info(String msg, Throwable t) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public boolean isWarnEnabled() {
		return true;
	}

	@Override
	public void warn(String msg) {
		synchronized (warnings) {
			warnings.add(new Warning(msg));
		}
	}

	@Override
	public void warn(String format, Object arg) {
		// Fix InfiniteRecursion bug pattern flagged by error prone:
		// https://errorprone.info/bugpattern/InfiniteRecursion
		Object [] singleton = { arg };
		warn(format, singleton);
	}

	@Override
	public void warn(String format, Object... arguments) {
		synchronized (warnings) {
			int i = 0;
			int index = format.indexOf("{}");
			while (index >= 0) {
				format = format.replaceFirst("\\{\\}", "{" + i++ + "}");
				index = format.indexOf("{}");
			}
			warnings.add(new Warning(MessageFormat.format(format, arguments)));
		}
	}

	@Override
	public void warn(String format, Object arg1, Object arg2) {
		warn(format, new Object[] { arg1, arg2 });
	}

	@Override
	public void warn(String msg, Throwable t) {
		synchronized (warnings) {
			warnings.add(new Warning(msg, t));
		}
	}

	@Override
	public boolean isErrorEnabled() {
		return false;
	}

	@Override
	public void error(String msg) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void error(String format, Object arg) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void error(String format, Object arg1, Object arg2) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void error(String format, Object... arguments) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void error(String msg, Throwable t) {
		// Ignore (not relevant to test failures)
	}
}
