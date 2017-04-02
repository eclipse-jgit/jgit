/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.junit.http;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.util.log.Logger;

/** Logs warnings into an array for later inspection. */
public class RecordingLogger implements Logger {
	private static List<Warning> warnings = new ArrayList<>();

	/** Clear the warnings, automatically done by {@link AppServer#setUp()} */
	public static void clear() {
		synchronized (warnings) {
			warnings.clear();
		}
	}

	/** @return the warnings (if any) from the last execution */
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

	public RecordingLogger() {
		this("");
	}

	public RecordingLogger(final String name) {
		this.name = name;
	}

	@Override
	public Logger getLogger(@SuppressWarnings("hiding") String name) {
		return new RecordingLogger(name);
	}

	@Override
	public String getName() {
		return name;
	}

	public void warn(String msg, Object arg0, Object arg1) {
		synchronized (warnings) {
			warnings.add(new Warning(MessageFormat.format(msg, arg0, arg1)));
		}
	}

	@Override
	public void warn(String msg, Throwable th) {
		synchronized (warnings) {
			warnings.add(new Warning(msg, th));
		}
	}

	public void warn(String msg) {
		synchronized (warnings) {
			warnings.add(new Warning(msg));
		}
	}

	public void debug(@SuppressWarnings("unused") String msg,
			          @SuppressWarnings("unused") Object arg0,
			          @SuppressWarnings("unused") Object arg1) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void debug(String msg, Throwable th) {
		// Ignore (not relevant to test failures)
	}

	public void debug(@SuppressWarnings("unused") String msg) {
		// Ignore (not relevant to test failures)
	}

	public void info(@SuppressWarnings("unused") String msg,
			         @SuppressWarnings("unused") Object arg0,
			         @SuppressWarnings("unused") Object arg1) {
		// Ignore (not relevant to test failures)
	}

	public void info(@SuppressWarnings("unused") String msg) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public boolean isDebugEnabled() {
		return false;
	}

	@Override
	public void setDebugEnabled(boolean enabled) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void warn(String msg, Object... args) {
		synchronized (warnings) {
			warnings.add(new Warning(MessageFormat.format(msg, args)));
		}
	}

	@Override
	public void warn(Throwable thrown) {
		synchronized (warnings) {
			warnings.add(new Warning(thrown));
		}
	}

	@Override
	public void info(String msg, Object... args) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void info(Throwable thrown) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void info(String msg, Throwable thrown) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void debug(String msg, Object... args) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void debug(Throwable thrown) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void ignore(Throwable arg0) {
		// Ignore (not relevant to test failures)
	}

	@Override
	public void debug(String msg, long value) {
		// Ignore (not relevant to test failures)
	}
}
