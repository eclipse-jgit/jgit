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

import org.eclipse.jgit.JGitText;

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
	 * @return the boolean interpretation of {@code value}.
	 * @throws IllegalArgumentException
	 *             if {@code value} is not recognized as one of the standard
	 *             boolean names.
	 */
	public static boolean toBoolean(final String stringValue) {
		if (stringValue == null)
			throw new NullPointerException(JGitText.get().expectedBooleanStringValue);

		if (equalsIgnoreCase("yes", stringValue)
				|| equalsIgnoreCase("true", stringValue)
				|| equalsIgnoreCase("1", stringValue)
				|| equalsIgnoreCase("on", stringValue)) {
			return true;

		} else if (equalsIgnoreCase("no", stringValue)
				|| equalsIgnoreCase("false", stringValue)
				|| equalsIgnoreCase("0", stringValue)
				|| equalsIgnoreCase("off", stringValue)) {
			return false;

		} else {
			throw new IllegalArgumentException(MessageFormat.format(JGitText.get().notABoolean, stringValue));
		}
	}

	private StringUtils() {
		// Do not create instances
	}
}
