/*
 * Copyright (C) 2014, Andrey Loskutov <loskutov@gmx.de>
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
package org.eclipse.jgit.ignore.internal;

import static java.lang.Character.isLetter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.ignore.FastIgnoreRule;

/**
 * Various {@link String} related utility methods, written mostly to avoid
 * generation of new String objects (e.g. via splitting Strings etc).
 *
 * @since 3.6
 */
public class Strings {

	static char getPathSeparator(Character pathSeparator) {
		return pathSeparator == null ? FastIgnoreRule.PATH_SEPARATOR
				: pathSeparator.charValue();
	}

	/**
	 * @param pattern
	 *            non null
	 * @param c
	 *            character to remove
	 * @return new string with all trailing characters removed
	 */
	public static String stripTrailing(String pattern, char c) {
		while (pattern.length() > 0
				&& pattern.charAt(pattern.length() - 1) == c)
			pattern = pattern.substring(0, pattern.length() - 1);
		return pattern;
	}

	static int count(String s, char c, boolean ignoreFirstLast) {
		int start = 0;
		int count = 0;
		while (true) {
			start = s.indexOf(c, start);
			if (start == -1)
				break;
			if (!ignoreFirstLast || (start != 0 && start != s.length()))
				count++;
			start++;
		}
		return count;
	}

	/**
	 * Splits given string to substrings by given separator
	 *
	 * @param pattern
	 *            non null
	 * @param slash
	 *            separator char
	 * @return list of substrings
	 */
	public static List<String> split(String pattern, char slash) {
		int count = count(pattern, slash, true);
		if (count < 1)
			throw new IllegalStateException(
					"Pattern must have at least two segments: " + pattern); //$NON-NLS-1$
		List<String> segments = new ArrayList<String>(count);
		int right = 0;
		while (true) {
			int left = right;
			right = pattern.indexOf(slash, right);
			if (right == -1) {
				if (left < pattern.length())
					segments.add(pattern.substring(left));
				break;
			}
			if (right - left > 0)
				if (left == 1)
					// leading slash should remain by the first pattern
					segments.add(pattern.substring(left - 1, right));
				else if (right == pattern.length() - 1)
					// trailing slash should remain too
					segments.add(pattern.substring(left, right + 1));
				else
					segments.add(pattern.substring(left, right));
			right++;
		}
		return segments;
	}

	static boolean isWildCard(String pattern) {
		return pattern.indexOf('*') != -1 || isComplexWildcard(pattern);
	}

	private static boolean isComplexWildcard(String pattern) {
		int idx1 = pattern.indexOf('[');
		if (idx1 != -1) {
			int idx2 = pattern.indexOf(']');
			if (idx2 > idx1)
				return true;
		}
		// required to match escaped backslashes '\\\\'
		if (pattern.indexOf('?') != -1 || pattern.indexOf('\\') != -1)
			return true;
		return false;
	}

	static PatternState checkWildCards(String pattern) {
		if (isComplexWildcard(pattern))
			return PatternState.COMPLEX;
		int startIdx = pattern.indexOf('*');
		if (startIdx < 0)
			return PatternState.NONE;

		if (startIdx == pattern.length() - 1)
			return PatternState.TRAILING_ASTERISK_ONLY;
		if (pattern.lastIndexOf('*') == 0)
			return PatternState.LEADING_ASTERISK_ONLY;

		return PatternState.COMPLEX;
	}

	static enum PatternState {
		LEADING_ASTERISK_ONLY, TRAILING_ASTERISK_ONLY, COMPLEX, NONE
	}

	final static List<String> POSIX_CHAR_CLASSES = Arrays.asList(
			"alnum", "alpha", "blank", "cntrl", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			// [:alnum:] [:alpha:] [:blank:] [:cntrl:]
			"digit", "graph", "lower", "print", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			// [:digit:] [:graph:] [:lower:] [:print:]
			"punct", "space", "upper", "xdigit", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			// [:punct:] [:space:] [:upper:] [:xdigit:]
			"word" //$NON-NLS-1$
	// [:word:] XXX I don't see it in
	// http://man7.org/linux/man-pages/man7/glob.7.html
	// but this was in org.eclipse.jgit.fnmatch.GroupHead.java ???
			);

	private static final String DL = "\\p{javaDigit}\\p{javaLetter}"; //$NON-NLS-1$

	final static List<String> JAVA_CHAR_CLASSES = Arrays
			.asList("\\p{Alnum}", "\\p{javaLetter}", "\\p{Blank}", "\\p{Cntrl}", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					// [:alnum:] [:alpha:] [:blank:] [:cntrl:]
					"\\p{javaDigit}", "[\\p{Graph}" + DL + "]", "\\p{Ll}", "[\\p{Print}" + DL + "]", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
					// [:digit:] [:graph:] [:lower:] [:print:]
					"\\p{Punct}", "\\p{Space}", "\\p{Lu}", "\\p{XDigit}", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					// [:punct:] [:space:] [:upper:] [:xdigit:]
					"[" + DL + "_]" //$NON-NLS-1$ //$NON-NLS-2$
							// [:word:]
			);

	// Collating symbols [[.a.]] or equivalence class expressions [[=a=]] are
	// not supported by CLI git (at least not by 1.9.1)
	final static Pattern UNSUPPORTED = Pattern
			.compile("\\[\\[[.=]\\w+[.=]\\]\\]"); //$NON-NLS-1$

	/**
	 * Conversion from glob to Java regex following two sources: <li>
	 * http://man7.org/linux/man-pages/man7/glob.7.html <li>
	 * org.eclipse.jgit.fnmatch.FileNameMatcher.java Seems that there are
	 * various ways to define what "glob" can be.
	 *
	 * @param pattern
	 *            non null pattern
	 *
	 * @return Java regex pattern corresponding to given glob pattern
	 * @throws InvalidPatternException
	 */
	static Pattern convertGlob(String pattern) throws InvalidPatternException {
		if (UNSUPPORTED.matcher(pattern).find())
			throw new InvalidPatternException(
					"Collating symbols [[.a.]] or equivalence class expressions [[=a=]] are not supported", //$NON-NLS-1$
					pattern);

		StringBuilder sb = new StringBuilder(pattern.length());

		int in_brackets = 0;
		boolean seenEscape = false;
		boolean ignoreLastBracket = false;
		boolean in_char_class = false;
		// 6 is the length of the longest posix char class "xdigit"
		char[] charClass = new char[6];

		for (int i = 0; i < pattern.length(); i++) {
			char c = pattern.charAt(i);
			switch (c) {

			case '*':
				if (seenEscape || in_brackets > 0)
					sb.append(c);
				else
					sb.append('.').append(c);
				break;

			case '.':
				if (seenEscape)
					sb.append(c);
				else
					sb.append('\\').append('.');
				break;

			case '?':
				if (seenEscape || in_brackets > 0)
					sb.append(c);
				else
					sb.append('.');
				break;

			case ':':
				if (in_brackets > 0)
					if (lookBehind(sb) == '['
							&& isLetter(lookAhead(pattern, i)))
						in_char_class = true;
				sb.append(':');
				break;

			case '-':
				if (in_brackets > 0) {
					if (lookAhead(pattern, i) == ']')
						sb.append('\\').append(c);
					else
						sb.append(c);
				} else
					sb.append('-');
				break;

			case '\\':
				if (in_brackets > 0) {
					char lookAhead = lookAhead(pattern, i);
					if (lookAhead == ']' || lookAhead == '[')
						ignoreLastBracket = true;
				}
				sb.append(c);
				break;

			case '[':
				if (in_brackets > 0) {
					sb.append('\\').append('[');
					ignoreLastBracket = true;
				} else {
					if (!seenEscape) {
						in_brackets++;
						ignoreLastBracket = false;
					}
					sb.append('[');
				}
				break;

			case ']':
				if (seenEscape) {
					sb.append(']');
					ignoreLastBracket = true;
					break;
				}
				if (in_brackets <= 0) {
					sb.append('\\').append(']');
					ignoreLastBracket = true;
					break;
				}
				char lookBehind = lookBehind(sb);
				if ((lookBehind == '[' && !ignoreLastBracket)
						|| lookBehind == '^') {
					sb.append('\\');
					sb.append(']');
					ignoreLastBracket = true;
				} else {
					ignoreLastBracket = false;
					if (!in_char_class) {
						in_brackets--;
						sb.append(']');
					} else {
						in_char_class = false;
						String charCl = checkPosixCharClass(charClass);
						// delete last \[:: chars and set the pattern
						if (charCl != null) {
							sb.setLength(sb.length() - 4);
							sb.append(charCl);
						}
						reset(charClass);
					}
				}
				break;

			case '!':
				if (in_brackets > 0) {
					if (lookBehind(sb) == '[')
						sb.append('^');
					else
						sb.append(c);
				} else
					sb.append(c);
				break;

			default:
				if (in_char_class)
					setNext(charClass, c);
				else
					sb.append(c);
				break;
			} // end switch

			seenEscape = c == '\\';

		} // end for

		if (in_brackets > 0)
			throw new InvalidPatternException("Not closed bracket?", pattern); //$NON-NLS-1$
		return Pattern.compile(sb.toString());
	}

	/**
	 * @param buffer
	 * @return zero of the buffer is empty, otherwise the last character from
	 *         buffer
	 */
	private static char lookBehind(StringBuilder buffer) {
		return buffer.length() > 0 ? buffer.charAt(buffer.length() - 1) : 0;
	}

	/**
	 * @param pattern
	 * @param i
	 *            current pointer in the pattern
	 * @return zero of the index is out of range, otherwise the next character
	 *         from given position
	 */
	private static char lookAhead(String pattern, int i) {
		int idx = i + 1;
		return idx >= pattern.length() ? 0 : pattern.charAt(idx);
	}

	private static void setNext(char[] buffer, char c) {
		for (int i = 0; i < buffer.length; i++)
			if (buffer[i] == 0) {
				buffer[i] = c;
				break;
			}
	}

	private static void reset(char[] buffer) {
		for (int i = 0; i < buffer.length; i++)
			buffer[i] = 0;
	}

	private static String checkPosixCharClass(char[] buffer) {
		for (int i = 0; i < POSIX_CHAR_CLASSES.size(); i++) {
			String clazz = POSIX_CHAR_CLASSES.get(i);
			boolean match = true;
			for (int j = 0; j < clazz.length(); j++)
				if (buffer[j] != clazz.charAt(j)) {
					match = false;
					break;
				}
			if (match)
				return JAVA_CHAR_CLASSES.get(i);
		}
		return null;
	}

}
