/*
 * Copyright (C) 2009-2010, Google Inc.
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

import java.text.MessageFormat;
import java.util.Collection;

import org.eclipse.jgit.internal.JGitText;

/** Miscellaneous string comparison utility methods. */
public final class StringUtils {
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
	public static char toLowerCase(final char c) {
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
	public static String toLowerCase(final String in) {
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
	 * {@link Character#toTitleCase(char)}. No other letters are changed.
	 * </p>
	 *
	 * A <code>null</code> input String returns <code>null</code>.</p>
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
		return new StringBuffer(strLen)
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
	public static boolean equalsIgnoreCase(final String a, final String b) {
		if (a == b)
			return true;
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
	 * @return negative, zero or positive if a sorts before, is equal to, or
	 *         sorts after b.
	 * @since 2.0
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
	 * @return negative, zero or positive if a sorts before, is equal to, or
	 *         sorts after b.
	 * @since 2.0
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
	 * @throws IllegalArgumentException
	 *             if {@code value} is not recognized as one of the standard
	 *             boolean names.
	 */
	public static boolean toBoolean(final String stringValue) {
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
	public static Boolean toBooleanOrNull(final String stringValue) {
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
			if (ch == '\r') {
				if (i + 1 < buf.length && in.charAt(i + 1) == '\n') {
					buf[o++] = ' ';
					++i;
				} else
					buf[o++] = ' ';
			} else if (ch == '\n')
				buf[o++] = ' ';
			else
				buf[o++] = ch;
		}
		return new String(buf, 0, o);
	}
}
