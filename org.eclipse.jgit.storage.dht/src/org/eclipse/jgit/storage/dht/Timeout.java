/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.storage.dht;

import java.text.MessageFormat;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.StringUtils;

/** Length of time to wait for an operation before giving up. */
public class Timeout {
	/**
	 * Construct a new timeout, expressed in milliseconds.
	 *
	 * @param millis
	 *            number of milliseconds to wait.
	 * @return the timeout.
	 */
	public static Timeout milliseconds(int millis) {
		return new Timeout(millis, TimeUnit.MILLISECONDS);
	}

	/**
	 * Construct a new timeout, expressed in seconds.
	 *
	 * @param sec
	 *            number of seconds to wait.
	 * @return the timeout.
	 */
	public static Timeout seconds(int sec) {
		return new Timeout(sec, TimeUnit.SECONDS);
	}

	/**
	 * Construct a new timeout, expressed in (possibly fractional) seconds.
	 *
	 * @param sec
	 *            number of seconds to wait.
	 * @return the timeout.
	 */
	public static Timeout seconds(double sec) {
		return new Timeout((long) (sec * 1000), TimeUnit.MILLISECONDS);
	}

	/**
	 * Obtain a timeout from the configuration.
	 *
	 * @param cfg
	 *            configuration to read.
	 * @param section
	 *            section key to read.
	 * @param subsection
	 *            subsection to read, may be null.
	 * @param name
	 *            variable to read.
	 * @param defaultValue
	 *            default to return if no timeout is specified in the
	 *            configuration.
	 * @return the configured timeout.
	 */
	public static Timeout getTimeout(Config cfg, String section,
			String subsection, String name, Timeout defaultValue) {
		String valStr = cfg.getString(section, subsection, name);
		if (valStr == null)
			return defaultValue;

		valStr = valStr.trim();
		if (valStr.length() == 0)
			return defaultValue;

		Matcher m = matcher("^([1-9][0-9]*(?:\\.[0-9]*)?)\\s*(.*)$", valStr);
		if (!m.matches())
			throw notTimeUnit(section, subsection, name, valStr);

		String digits = m.group(1);
		String unitName = m.group(2).trim();

		long multiplier;
		TimeUnit unit;
		if ("".equals(unitName)) {
			multiplier = 1;
			unit = TimeUnit.MILLISECONDS;

		} else if (anyOf(unitName, "ms", "millisecond", "milliseconds")) {
			multiplier = 1;
			unit = TimeUnit.MILLISECONDS;

		} else if (anyOf(unitName, "s", "sec", "second", "seconds")) {
			multiplier = 1;
			unit = TimeUnit.SECONDS;

		} else if (anyOf(unitName, "m", "min", "minute", "minutes")) {
			multiplier = 60;
			unit = TimeUnit.SECONDS;

		} else if (anyOf(unitName, "h", "hr", "hour", "hours")) {
			multiplier = 3600;
			unit = TimeUnit.SECONDS;

		} else
			throw notTimeUnit(section, subsection, name, valStr);

		if (digits.indexOf('.') == -1) {
			try {
				return new Timeout(multiplier * Long.parseLong(digits), unit);
			} catch (NumberFormatException nfe) {
				throw notTimeUnit(section, subsection, name, valStr);
			}
		} else {
			double inputTime;
			try {
				inputTime = multiplier * Double.parseDouble(digits);
			} catch (NumberFormatException nfe) {
				throw notTimeUnit(section, subsection, name, valStr);
			}

			if (unit == TimeUnit.MILLISECONDS) {
				TimeUnit newUnit = TimeUnit.NANOSECONDS;
				long t = (long) (inputTime * newUnit.convert(1, unit));
				return new Timeout(t, newUnit);

			} else if (unit == TimeUnit.SECONDS && multiplier == 1) {
				TimeUnit newUnit = TimeUnit.MILLISECONDS;
				long t = (long) (inputTime * newUnit.convert(1, unit));
				return new Timeout(t, newUnit);

			} else {
				return new Timeout((long) inputTime, unit);
			}
		}
	}

	private static Matcher matcher(String pattern, String valStr) {
		return Pattern.compile(pattern).matcher(valStr);
	}

	private static boolean anyOf(String a, String... cases) {
		for (String b : cases) {
			if (StringUtils.equalsIgnoreCase(a, b))
				return true;
		}
		return false;
	}

	private static IllegalArgumentException notTimeUnit(String section,
			String subsection, String name, String valueString) {
		String key = section
				+ (subsection != null ? "." + subsection : "")
				+ "." + name;
		return new IllegalArgumentException(MessageFormat.format(
				DhtText.get().notTimeUnit, key, valueString));
	}

	private final long time;

	private final TimeUnit unit;

	/**
	 * Construct a new timeout.
	 *
	 * @param time
	 *            how long to wait.
	 * @param unit
	 *            the unit that {@code time} was expressed in.
	 */
	public Timeout(long time, TimeUnit unit) {
		this.time = time;
		this.unit = unit;
	}

	/** @return how long to wait, expressed as {@link #getUnit()}s. */
	public long getTime() {
		return time;
	}

	/** @return the unit of measure for {@link #getTime()}. */
	public TimeUnit getUnit() {
		return unit;
	}

	@Override
	public int hashCode() {
		return (int) time;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Timeout)
			return getTime() == ((Timeout) other).getTime()
					&& getUnit().equals(((Timeout) other).getUnit());
		return false;
	}

	@Override
	public String toString() {
		return getTime() + " " + getUnit();
	}
}
