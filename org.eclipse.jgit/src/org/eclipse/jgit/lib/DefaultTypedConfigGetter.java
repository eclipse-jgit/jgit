/*
 * Copyright (C) 2017, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
		if (Config.isMissing(n)) {
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
		}
		throw new IllegalArgumentException(MessageFormat.format(
				JGitText.get().enumValueNotSupported2, section, name, value));
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

		} else if (match(unitName, "ns", "nanoseconds")) { //$NON-NLS-1$ //$NON-NLS-2$
			inputUnit = TimeUnit.NANOSECONDS;
			inputMul = 1;

		} else if (match(unitName, "us", "microseconds")) { //$NON-NLS-1$ //$NON-NLS-2$
			inputUnit = TimeUnit.MICROSECONDS;
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
	@NonNull
	public List<RefSpec> getRefSpecs(Config config, String section,
			String subsection, String name) {
		String[] values = config.getStringList(section, subsection, name);
		List<RefSpec> result = new ArrayList<>(values.length);
		for (String spec : values) {
			result.add(new RefSpec(spec));
		}
		return result;
	}
}
