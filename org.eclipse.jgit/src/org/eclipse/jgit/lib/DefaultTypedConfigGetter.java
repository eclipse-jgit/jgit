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
import org.eclipse.jgit.annotations.Nullable;
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

	@SuppressWarnings("boxed")
	@Override
	public boolean getBoolean(Config config, String section, String subsection,
			String name, boolean defaultValue) {
		return neverNull(getBoolean(config, section, subsection, name,
				Boolean.valueOf(defaultValue)));
	}

	@Nullable
	@Override
	public Boolean getBoolean(Config config, String section, String subsection,
			String name, @Nullable Boolean defaultValue) {
		String n = config.getString(section, subsection, name);
		if (n == null) {
			return defaultValue;
		}
		if (Config.isMissing(n)) {
			return Boolean.TRUE;
		}
		try {
			return Boolean.valueOf(StringUtils.toBoolean(n));
		} catch (IllegalArgumentException err) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().invalidBooleanValue, section, name, n), err);
		}
	}

	@Nullable
	@Override
	public <T extends Enum<?>> T getEnum(Config config, T[] all, String section,
			String subsection, String name, @Nullable T defaultValue) {
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

	@Override
	public int getInt(Config config, String section, String subsection,
			String name, int defaultValue) {
		return neverNull(getInt(config, section, subsection, name,
				Integer.valueOf(defaultValue)));
	}

	@Nullable
	@Override
	@SuppressWarnings("boxing")
	public Integer getInt(Config config, String section, String subsection,
			String name, @Nullable Integer defaultValue) {
		Long longDefault = defaultValue != null
				? Long.valueOf(defaultValue.longValue())
				: null;
		Long val = config.getLong(section, subsection, name);
		if (val == null) {
			val = longDefault;
		}
		if (val == null) {
			return null;
		}
		if (Integer.MIN_VALUE <= val && val <= Integer.MAX_VALUE) {
			return Integer.valueOf(Math.toIntExact(val));
		}
		throw new IllegalArgumentException(MessageFormat
				.format(JGitText.get().integerValueOutOfRange, section, name));
	}

	@Override
	public int getIntInRange(Config config, String section, String subsection,
			String name, int minValue, int maxValue, int defaultValue) {
		return neverNull(getIntInRange(config, section, subsection, name,
				minValue, maxValue, Integer.valueOf(defaultValue)));
	}

	@Override
	@SuppressWarnings("boxing")
	public Integer getIntInRange(Config config, String section,
			String subsection, String name, int minValue, int maxValue,
			Integer defaultValue) {
		Integer val = getInt(config, section, subsection, name, defaultValue);
		if (val == null) {
			return null;
		}
		if ((val >= minValue && val <= maxValue) || val == UNSET_INT) {
			return val;
		}
		if (subsection == null) {
			throw new IllegalArgumentException(
					MessageFormat.format(JGitText.get().integerValueNotInRange,
							section, name, val, minValue, maxValue));
		}
		throw new IllegalArgumentException(MessageFormat.format(
				JGitText.get().integerValueNotInRangeSubSection, section,
				subsection, name, val, minValue, maxValue));
	}

	@Override
	public long getLong(Config config, String section, String subsection,
			String name, long defaultValue) {
		return neverNull(getLong(config, section, subsection, name,
				Long.valueOf(defaultValue)));
	}

	@Nullable
	@Override
	public Long getLong(Config config, String section, String subsection,
			String name, @Nullable Long defaultValue) {
		String str = config.getString(section, subsection, name);
		if (str == null) {
			return defaultValue;
		}
		try {
			return Long.valueOf(StringUtils.parseLongWithSuffix(str, false));
		} catch (StringIndexOutOfBoundsException e) {
			// Empty
			return defaultValue;
		} catch (NumberFormatException nfe) {
			throw new IllegalArgumentException(
					MessageFormat.format(JGitText.get().invalidIntegerValue,
							section, name, str),
					nfe);
		}
	}

	@Override
	public long getTimeUnit(Config config, String section, String subsection,
			String name, long defaultValue, TimeUnit wantUnit) {
		return neverNull(getTimeUnit(config, section, subsection, name,
				Long.valueOf(defaultValue), wantUnit));
	}

	@Override
	public Long getTimeUnit(Config config, String section, String subsection,
			String name, @Nullable Long defaultValue, TimeUnit wantUnit) {
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
				.matcher(s);
		if (!m.matches()) {
			throw notTimeUnit(section, subsection, name, valueString);
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
			return Long.valueOf(wantUnit
					.convert(Long.parseLong(digits) * inputMul, inputUnit));
		} catch (NumberFormatException nfe) {
			IllegalArgumentException iae = notTimeUnit(section, subsection,
					unitName, valueString);
			iae.initCause(nfe);
			throw iae;
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

	// Trick for the checkers. When we use this, one is never null, but
	// they don't know.
	@NonNull
	private static <T> T neverNull(T one) {
		if (one == null) {
			throw new IllegalArgumentException();
		}
		return one;
	}
}
