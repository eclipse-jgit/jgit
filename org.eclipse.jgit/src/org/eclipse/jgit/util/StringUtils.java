/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import java.text.MessageFormat;
import java.util.Collection;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.JGitText;

/**
 * Miscellaneous string comparison utility methods.
 */
public final class StringUtils {

	private static final long KiB = 1024;

	private static final long MiB = 1024 * KiB;

	private static final long GiB = 1024 * MiB;

	private static final char[] LC;

	static {
		LC = new char['Z' + 1];
		for (char c = 0; c < LC.length; c++)
			LC[c] = c;
		for (char c = 'A'; c <= 'Z'; c++)
			LC[c] = (char) ('a' + (c - 'A'));
	}

	/**
	 * Convert the input to lowercase.
	 * <p>
	 * This method does not honor the JVM locale, but instead always behaves as
	 * though it is in the US-ASCII locale. Only characters in the range 'A'
	 * through 'Z' are converted. All other characters are left as-is, even if
	 * they otherwise would have a lowercase character equivalent.
	 *
	 * @param c
	 *            the input character.
	 * @return lowercase version of the input.
	 */
	public static char toLowerCase(char c) {
		return c <= 'Z' ? LC[c] : c;
	}

	/**
	 * Convert the input string to lower case, according to the "C" locale.
	 * <p>
	 * This method does not honor the JVM locale, but instead always behaves as
	 * though it is in the US-ASCII locale. Only characters in the range 'A'
	 * through 'Z' are converted, all other characters are left as-is, even if
	 * they otherwise would have a lowercase character equivalent.
	 *
	 * @param in
	 *            the input string. Must not be null.
	 * @return a copy of the input string, after converting characters in the
	 *         range 'A'..'Z' to 'a'..'z'.
	 */
	public static String toLowerCase(String in) {
		final StringBuilder r = new StringBuilder(in.length());
		for (int i = 0; i < in.length(); i++)
			r.append(toLowerCase(in.charAt(i)));
		return r.toString();
	}


	/**
	 * Borrowed from commons-lang <code>StringUtils.capitalize()</code> method.
	 *
	 * <p>
	 * Capitalizes a String changing the first letter to title case as per
	 * {@link java.lang.Character#toTitleCase(char)}. No other letters are
	 * changed.
	 * </p>
	 * <p>
	 * A <code>null</code> input String returns <code>null</code>.
	 * </p>
	 *
	 * @param str
	 *            the String to capitalize, may be null
	 * @return the capitalized String, <code>null</code> if null String input
	 * @since 4.0
	 */
	public static String capitalize(String str) {
		int strLen;
		if (str == null || (strLen = str.length()) == 0) {
			return str;
		}
		return new StringBuilder(strLen)
				.append(Character.toTitleCase(str.charAt(0)))
				.append(str.substring(1)).toString();
	}

	/**
	 * Test if two strings are equal, ignoring case.
	 * <p>
	 * This method does not honor the JVM locale, but instead always behaves as
	 * though it is in the US-ASCII locale.
	 *
	 * @param a
	 *            first string to compare.
	 * @param b
	 *            second string to compare.
	 * @return true if a equals b
	 */
	public static boolean equalsIgnoreCase(String a, String b) {
		if (References.isSameObject(a, b)) {
			return true;
		}
		if (a.length() != b.length())
			return false;
		for (int i = 0; i < a.length(); i++) {
			if (toLowerCase(a.charAt(i)) != toLowerCase(b.charAt(i)))
				return false;
		}
		return true;
	}

	/**
	 * Compare two strings, ignoring case.
	 * <p>
	 * This method does not honor the JVM locale, but instead always behaves as
	 * though it is in the US-ASCII locale.
	 *
	 * @param a
	 *            first string to compare.
	 * @param b
	 *            second string to compare.
	 * @since 2.0
	 * @return an int.
	 */
	public static int compareIgnoreCase(String a, String b) {
		for (int i = 0; i < a.length() && i < b.length(); i++) {
			int d = toLowerCase(a.charAt(i)) - toLowerCase(b.charAt(i));
			if (d != 0)
				return d;
		}
		return a.length() - b.length();
	}

	/**
	 * Compare two strings, honoring case.
	 * <p>
	 * This method does not honor the JVM locale, but instead always behaves as
	 * though it is in the US-ASCII locale.
	 *
	 * @param a
	 *            first string to compare.
	 * @param b
	 *            second string to compare.
	 * @since 2.0
	 * @return an int.
	 */
	public static int compareWithCase(String a, String b) {
		for (int i = 0; i < a.length() && i < b.length(); i++) {
			int d = a.charAt(i) - b.charAt(i);
			if (d != 0)
				return d;
		}
		return a.length() - b.length();
	}

	/**
	 * Parse a string as a standard Git boolean value. See
	 * {@link #toBooleanOrNull(String)}.
	 *
	 * @param stringValue
	 *            the string to parse.
	 * @return the boolean interpretation of {@code value}.
	 * @throws java.lang.IllegalArgumentException
	 *             if {@code value} is not recognized as one of the standard
	 *             boolean names.
	 */
	public static boolean toBoolean(String stringValue) {
		if (stringValue == null)
			throw new NullPointerException(JGitText.get().expectedBooleanStringValue);

		final Boolean bool = toBooleanOrNull(stringValue);
		if (bool == null)
			throw new IllegalArgumentException(MessageFormat.format(JGitText.get().notABoolean, stringValue));

		return bool.booleanValue();
	}

	/**
	 * Parse a string as a standard Git boolean value.
	 * <p>
	 * The terms {@code yes}, {@code true}, {@code 1}, {@code on} can all be
	 * used to mean {@code true}.
	 * <p>
	 * The terms {@code no}, {@code false}, {@code 0}, {@code off} can all be
	 * used to mean {@code false}.
	 * <p>
	 * Comparisons ignore case, via {@link #equalsIgnoreCase(String, String)}.
	 *
	 * @param stringValue
	 *            the string to parse.
	 * @return the boolean interpretation of {@code value} or null in case the
	 *         string does not represent a boolean value
	 */
	public static Boolean toBooleanOrNull(String stringValue) {
		if (stringValue == null)
			return null;

		if (equalsIgnoreCase("yes", stringValue) //$NON-NLS-1$
				|| equalsIgnoreCase("true", stringValue) //$NON-NLS-1$
				|| equalsIgnoreCase("1", stringValue) //$NON-NLS-1$
				|| equalsIgnoreCase("on", stringValue)) //$NON-NLS-1$
			return Boolean.TRUE;
		else if (equalsIgnoreCase("no", stringValue) //$NON-NLS-1$
				|| equalsIgnoreCase("false", stringValue) //$NON-NLS-1$
				|| equalsIgnoreCase("0", stringValue) //$NON-NLS-1$
				|| equalsIgnoreCase("off", stringValue)) //$NON-NLS-1$
			return Boolean.FALSE;
		else
			return null;
	}

	/**
	 * Join a collection of Strings together using the specified separator.
	 *
	 * @param parts
	 *            Strings to join
	 * @param separator
	 *            used to join
	 * @return a String with all the joined parts
	 */
	public static String join(Collection<String> parts, String separator) {
		return StringUtils.join(parts, separator, separator);
	}

	/**
	 * Join a collection of Strings together using the specified separator and a
	 * lastSeparator which is used for joining the second last and the last
	 * part.
	 *
	 * @param parts
	 *            Strings to join
	 * @param separator
	 *            separator used to join all but the two last elements
	 * @param lastSeparator
	 *            separator to use for joining the last two elements
	 * @return a String with all the joined parts
	 */
	public static String join(Collection<String> parts, String separator,
			String lastSeparator) {
		StringBuilder sb = new StringBuilder();
		int i = 0;
		int lastIndex = parts.size() - 1;
		for (String part : parts) {
			sb.append(part);
			if (i == lastIndex - 1) {
				sb.append(lastSeparator);
			} else if (i != lastIndex) {
				sb.append(separator);
			}
			i++;
		}
		return sb.toString();
	}

	private StringUtils() {
		// Do not create instances
	}

	/**
	 * Test if a string is empty or null.
	 *
	 * @param stringValue
	 *            the string to check
	 * @return <code>true</code> if the string is <code>null</code> or empty
	 */
	public static boolean isEmptyOrNull(String stringValue) {
		return stringValue == null || stringValue.length() == 0;
	}

	/**
	 * Replace CRLF, CR or LF with a single space.
	 *
	 * @param in
	 *            A string with line breaks
	 * @return in without line breaks
	 * @since 3.1
	 */
	public static String replaceLineBreaksWithSpace(String in) {
		char[] buf = new char[in.length()];
		int o = 0;
		for (int i = 0; i < buf.length; ++i) {
			char ch = in.charAt(i);
			switch (ch) {
			case '\r':
				if (i + 1 < buf.length && in.charAt(i + 1) == '\n') {
					buf[o++] = ' ';
					++i;
				} else
					buf[o++] = ' ';
				break;
			case '\n':
				buf[o++] = ' ';
				break;
			default:
				buf[o++] = ch;
				break;
			}
		}
		return new String(buf, 0, o);
	}

	/**
	 * Parses a number with optional case-insensitive suffix 'k', 'm', or 'g'
	 * indicating KiB, MiB, and GiB, respectively. The suffix may follow the
	 * number with optional separation by one or more blanks.
	 *
	 * @param value
	 *            {@link String} to parse; with leading and trailing whitespace
	 *            ignored
	 * @param positiveOnly
	 *            {@code true} to only accept positive numbers, {@code false} to
	 *            allow negative numbers, too
	 * @return the value parsed
	 * @throws NumberFormatException
	 *             if the {@value} is not parseable, or beyond the range of
	 *             {@link Long}
	 * @throws StringIndexOutOfBoundsException
	 *             if the string is empty or contains only whitespace, or
	 *             contains only the letter 'k', 'm', or 'g'
	 * @since 6.0
	 */
	public static long parseLongWithSuffix(@NonNull String value,
			boolean positiveOnly)
			throws NumberFormatException, StringIndexOutOfBoundsException {
		String n = value.strip();
		if (n.isEmpty()) {
			throw new StringIndexOutOfBoundsException();
		}
		long mul = 1;
		switch (n.charAt(n.length() - 1)) {
		case 'g':
		case 'G':
			mul = GiB;
			break;
		case 'm':
		case 'M':
			mul = MiB;
			break;
		case 'k':
		case 'K':
			mul = KiB;
			break;
		default:
			break;
		}
		if (mul > 1) {
			n = n.substring(0, n.length() - 1).trim();
		}
		if (n.isEmpty()) {
			throw new StringIndexOutOfBoundsException();
		}
		long number;
		if (positiveOnly) {
			number = Long.parseUnsignedLong(n);
			if (number < 0) {
				throw new NumberFormatException(
						MessageFormat.format(JGitText.get().valueExceedsRange,
								value, Long.class.getSimpleName()));
			}
		} else {
			number = Long.parseLong(n);
		}
		if (mul == 1) {
			return number;
		}
		try {
			return Math.multiplyExact(mul, number);
		} catch (ArithmeticException e) {
			NumberFormatException nfe = new NumberFormatException(
					e.getLocalizedMessage());
			nfe.initCause(e);
			throw nfe;
		}
	}

	/**
	 * Parses a number with optional case-insensitive suffix 'k', 'm', or 'g'
	 * indicating KiB, MiB, and GiB, respectively. The suffix may follow the
	 * number with optional separation by blanks.
	 *
	 * @param value
	 *            {@link String} to parse; with leading and trailing whitespace
	 *            ignored
	 * @param positiveOnly
	 *            {@code true} to only accept positive numbers, {@code false} to
	 *            allow negative numbers, too
	 * @return the value parsed
	 * @throws NumberFormatException
	 *             if the {@value} is not parseable or beyond the range of
	 *             {@link Integer}
	 * @throws StringIndexOutOfBoundsException
	 *             if the string is empty or contains only whitespace, or
	 *             contains only the letter 'k', 'm', or 'g'
	 * @since 6.0
	 */
	public static int parseIntWithSuffix(@NonNull String value,
			boolean positiveOnly)
			throws NumberFormatException, StringIndexOutOfBoundsException {
		try {
			return Math.toIntExact(parseLongWithSuffix(value, positiveOnly));
		} catch (ArithmeticException e) {
			NumberFormatException nfe = new NumberFormatException(
					MessageFormat.format(JGitText.get().valueExceedsRange,
							value, Integer.class.getSimpleName()));
			nfe.initCause(e);
			throw nfe;
		}
	}

	/**
	 * Formats an integral value as a decimal number with 'k', 'm', or 'g'
	 * suffix if it is an exact multiple of 1024, otherwise returns the value
	 * representation as a decimal number without suffix.
	 *
	 * @param value
	 *            Value to format
	 * @return the value's String representation
	 * @since 6.0
	 */
	public static String formatWithSuffix(long value) {
		if (value >= GiB && (value % GiB) == 0) {
			return String.valueOf(value / GiB) + 'g';
		}
		if (value >= MiB && (value % MiB) == 0) {
			return String.valueOf(value / MiB) + 'm';
		}
		if (value >= KiB && (value % KiB) == 0) {
			return String.valueOf(value / KiB) + 'k';
		}
		return String.valueOf(value);
	}

	public static String getCommonPrefix(String a, String b){
		int minLength = Math.min(a.length(), b.length());
		for (int i = 0; i < minLength; i++) {
			if (a.charAt(i) != b.charAt(i)) {
				return a.substring(0, i);
			}
		}
		return a.substring(0, minLength);
	}
}
