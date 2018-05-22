/*
 * Copyright (C) 2017, Thomas Wolf <thomas.wolf@paranor.ch>
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

package org.eclipse.jgit.lib;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config.ConfigEnum;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.util.StringUtils;

/**
 * An {@link org.eclipse.jgit.lib.TypedConfigGetter} that throws
 * {@link java.lang.IllegalArgumentException} on invalid values.
 *
 * @since 4.9
 */
public class DefaultTypedConfigGetter implements TypedConfigGetter {

	/** {@inheritDoc} */
	@Override
	public boolean getBoolean(Config config, String section, String subsection,
			String name, boolean defaultValue) {
		String n = config.getRawString(section, subsection, name);
		if (n == null) {
			return defaultValue;
		}
		if (Config.MAGIC_EMPTY_VALUE == n) {
			return true;
		}
		try {
			return StringUtils.toBoolean(n);
		} catch (IllegalArgumentException err) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().invalidBooleanValue, section, name, n));
		}
	}

	/** {@inheritDoc} */
	@Override
	public <T extends Enum<?>> T getEnum(Config config, T[] all, String section,
			String subsection, String name, T defaultValue) {
		String value = config.getString(section, subsection, name);
		if (value == null) {
			return defaultValue;
		}
		if (all[0] instanceof ConfigEnum) {
			for (T t : all) {
				if (((ConfigEnum) t).matchConfigValue(value)) {
					return t;
				}
			}
		}

		String n = value.replace(' ', '_');

		// Because of c98abc9c0586c73ef7df4172644b7dd21c979e9d being used in
		// the real world before its breakage was fully understood, we must
		// also accept '-' as though it were ' '.
		n = n.replace('-', '_');

		T trueState = null;
		T falseState = null;
		for (T e : all) {
			if (StringUtils.equalsIgnoreCase(e.name(), n)) {
				return e;
			} else if (StringUtils.equalsIgnoreCase(e.name(), "TRUE")) { //$NON-NLS-1$
				trueState = e;
			} else if (StringUtils.equalsIgnoreCase(e.name(), "FALSE")) { //$NON-NLS-1$
				falseState = e;
			}
		}

		// This is an odd little fallback. C Git sometimes allows boolean
		// values in a tri-state with other things. If we have both a true
		// and a false value in our enumeration, assume its one of those.
		//
		if (trueState != null && falseState != null) {
			try {
				return StringUtils.toBoolean(n) ? trueState : falseState;
			} catch (IllegalArgumentException err) {
				// Fall through and use our custom error below.
			}
		}

		if (subsection != null) {
			throw new IllegalArgumentException(
					MessageFormat.format(JGitText.get().enumValueNotSupported3,
							section, subsection, name, value));
		} else {
			throw new IllegalArgumentException(
					MessageFormat.format(JGitText.get().enumValueNotSupported2,
							section, name, value));
		}
	}

	/** {@inheritDoc} */
	@Override
	public int getInt(Config config, String section, String subsection,
			String name, int defaultValue) {
		long val = config.getLong(section, subsection, name, defaultValue);
		if (Integer.MIN_VALUE <= val && val <= Integer.MAX_VALUE) {
			return (int) val;
		}
		throw new IllegalArgumentException(MessageFormat
				.format(JGitText.get().integerValueOutOfRange, section, name));
	}

	/** {@inheritDoc} */
	@Override
	public long getLong(Config config, String section, String subsection,
			String name, long defaultValue) {
		final String str = config.getString(section, subsection, name);
		if (str == null) {
			return defaultValue;
		}
		String n = str.trim();
		if (n.length() == 0) {
			return defaultValue;
		}
		long mul = 1;
		switch (StringUtils.toLowerCase(n.charAt(n.length() - 1))) {
		case 'g':
			mul = Config.GiB;
			break;
		case 'm':
			mul = Config.MiB;
			break;
		case 'k':
			mul = Config.KiB;
			break;
		}
		if (mul > 1) {
			n = n.substring(0, n.length() - 1).trim();
		}
		if (n.length() == 0) {
			return defaultValue;
		}
		try {
			return mul * Long.parseLong(n);
		} catch (NumberFormatException nfe) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().invalidIntegerValue, section, name, str));
		}
	}

	/** {@inheritDoc} */
	@Override
	public long getTimeUnit(Config config, String section, String subsection,
			String name, long defaultValue, TimeUnit wantUnit) {
		String valueString = config.getString(section, subsection, name);

		if (valueString == null) {
			return defaultValue;
		}

		String s = valueString.trim();
		if (s.length() == 0) {
			return defaultValue;
		}

		if (s.startsWith("-")/* negative */) { //$NON-NLS-1$
			throw notTimeUnit(section, subsection, name, valueString);
		}

		Matcher m = Pattern.compile("^(0|[1-9][0-9]*)\\s*(.*)$") //$NON-NLS-1$
				.matcher(valueString);
		if (!m.matches()) {
			return defaultValue;
		}

		String digits = m.group(1);
		String unitName = m.group(2).trim();

		TimeUnit inputUnit;
		int inputMul;

		if (unitName.isEmpty()) {
			inputUnit = wantUnit;
			inputMul = 1;

		} else if (match(unitName, "ms", "milliseconds")) { //$NON-NLS-1$ //$NON-NLS-2$
			inputUnit = TimeUnit.MILLISECONDS;
			inputMul = 1;

		} else if (match(unitName, "s", "sec", "second", "seconds")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			inputUnit = TimeUnit.SECONDS;
			inputMul = 1;

		} else if (match(unitName, "m", "min", "minute", "minutes")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			inputUnit = TimeUnit.MINUTES;
			inputMul = 1;

		} else if (match(unitName, "h", "hr", "hour", "hours")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			inputUnit = TimeUnit.HOURS;
			inputMul = 1;

		} else if (match(unitName, "d", "day", "days")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			inputUnit = TimeUnit.DAYS;
			inputMul = 1;

		} else if (match(unitName, "w", "week", "weeks")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			inputUnit = TimeUnit.DAYS;
			inputMul = 7;

		} else if (match(unitName, "mon", "month", "months")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			inputUnit = TimeUnit.DAYS;
			inputMul = 30;

		} else if (match(unitName, "y", "year", "years")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			inputUnit = TimeUnit.DAYS;
			inputMul = 365;

		} else {
			throw notTimeUnit(section, subsection, name, valueString);
		}

		try {
			return wantUnit.convert(Long.parseLong(digits) * inputMul,
					inputUnit);
		} catch (NumberFormatException nfe) {
			throw notTimeUnit(section, subsection, unitName, valueString);
		}
	}

	private static boolean match(String a, String... cases) {
		for (String b : cases) {
			if (b != null && b.equalsIgnoreCase(a)) {
				return true;
			}
		}
		return false;
	}

	private static IllegalArgumentException notTimeUnit(String section,
			String subsection, String name, String valueString) {
		if (subsection != null) {
			return new IllegalArgumentException(
					MessageFormat.format(JGitText.get().invalidTimeUnitValue3,
							section, subsection, name, valueString));
		}
		return new IllegalArgumentException(
				MessageFormat.format(JGitText.get().invalidTimeUnitValue2,
						section, name, valueString));
	}

	/** {@inheritDoc} */
	@Override
	public @NonNull List<RefSpec> getRefSpecs(Config config, String section,
			String subsection, String name) {
		String[] values = config.getStringList(section, subsection, name);
		List<RefSpec> result = new ArrayList<>(values.length);
		for (String spec : values) {
			result.add(new RefSpec(spec));
		}
		return result;
	}
}
