/*
 * Copyright (C) 2014, 2025 Andrey Loskutov <loskutov@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.ignore.internal;

import static java.lang.Character.isLetter;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.ignore.FastIgnoreRule;
import org.eclipse.jgit.internal.JGitText;

/**
 * Various {@link java.lang.String} related utility methods, written mostly to
 * avoid generation of new String objects (e.g. via splitting Strings etc).
 */
public class Strings {

	static char getPathSeparator(Character pathSeparator) {
		return pathSeparator == null ? FastIgnoreRule.PATH_SEPARATOR
				: pathSeparator.charValue();
	}

	/**
	 * Strip trailing characters
	 *
	 * @param pattern
	 *            non null
	 * @param c
	 *            character to remove
	 * @return new string with all trailing characters removed
	 */
	public static String stripTrailing(String pattern, char c) {
		for (int i = pattern.length() - 1; i >= 0; i--) {
			char charAt = pattern.charAt(i);
			if (charAt != c) {
				if (i == pattern.length() - 1) {
					return pattern;
				}
				return pattern.substring(0, i + 1);
			}
		}
		return ""; //$NON-NLS-1$
	}

	/**
	 * Check if pattern is a directory pattern ending with a path separator
	 *
	 * @param pattern
	 *            non null
	 * @return {@code true} if the last character, which is not whitespace, is a
	 *         path separator
	 */
	public static boolean isDirectoryPattern(String pattern) {
		for (int i = pattern.length() - 1; i >= 0; i--) {
			char charAt = pattern.charAt(i);
			if (!Character.isWhitespace(charAt)) {
				return charAt == FastIgnoreRule.PATH_SEPARATOR;
			}
		}
		return false;
	}

	static int count(String s, char c, boolean ignoreFirstLast) {
		int start = 0;
		int count = 0;
		int length = s.length();
		while (start < length) {
			start = s.indexOf(c, start);
			if (start == -1) {
				break;
			}
			if (!ignoreFirstLast || (start != 0 && start != length - 1)) {
				count++;
			}
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
		List<String> segments = new ArrayList<>(count);
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
		if (pattern.indexOf('[') != -1) {
			return true;
		}
		if (pattern.indexOf('?') != -1) {
			return true;
		}
		// check if the backslash escapes one of the glob special characters
		// if not, backslash is not part of a regex and treated literally
		int backSlash = pattern.indexOf('\\');
		if (backSlash >= 0) {
			int nextIdx = backSlash + 1;
			if (pattern.length() == nextIdx) {
				return false;
			}
			char nextChar = pattern.charAt(nextIdx);
			// If the backslash _might_ escape a metacharacter, we have
			// something complex
			return nextChar == '?' || nextChar == '*' || nextChar == '[';
		}
		return false;
	}

	static PatternState checkWildCards(String pattern) {
		if (isComplexWildcard(pattern)) {
			return PatternState.COMPLEX;
		}
		int startIdx = pattern.indexOf('*');
		if (startIdx < 0) {
			return PatternState.NONE;
		}

		if (startIdx == pattern.length() - 1) {
			return PatternState.TRAILING_ASTERISK_ONLY;
		}
		if (pattern.lastIndexOf('*') == 0) {
			return PatternState.LEADING_ASTERISK_ONLY;
		}
		return PatternState.COMPLEX;
	}

	enum PatternState {
		LEADING_ASTERISK_ONLY, TRAILING_ASTERISK_ONLY, COMPLEX, NONE
	}

	static final List<String> POSIX_CHAR_CLASSES = Arrays.asList(
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

	static final List<String> JAVA_CHAR_CLASSES = Arrays
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
	static final Pattern UNSUPPORTED = Pattern
			.compile("\\[\\[[.=]\\w+[.=]\\]\\]"); //$NON-NLS-1$

	/**
	 * Conversion from glob to Java regex following two sources:
	 * <ul>
	 * <li>http://man7.org/linux/man-pages/man7/glob.7.html
	 * <li>org.eclipse.jgit.fnmatch.FileNameMatcher.java Seems that there are
	 * various ways to define what "glob" can be.
	 * </ul>
	 *
	 * @param pattern
	 *            non null pattern
	 *
	 * @return Java regex pattern corresponding to given glob pattern
	 * @throws InvalidPatternException
	 *             if pattern is invalid
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
			final char c = pattern.charAt(i);
			switch (c) {

			case '*':
				if (seenEscape || in_brackets > 0)
					sb.append(c);
				else
					sb.append('.').append(c);
				break;

			case '(': // fall-through
			case ')': // fall-through
			case '{': // fall-through
			case '}': // fall-through
			case '+': // fall-through
			case '$': // fall-through
			case '^': // fall-through
			case '|':
				if (seenEscape || in_brackets > 0)
					sb.append(c);
				else
					sb.append('\\').append(c);
				break;

			case '.':
				if (seenEscape || in_brackets > 0)
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
				sb.append(c);
				break;

			case '-':
				if (in_brackets > 0) {
					if (lookAhead(pattern, i) == ']')
						sb.append('\\').append(c);
					else
						sb.append(c);
				} else
					sb.append(c);
				break;

			case '\\':
				if (seenEscape) {
					sb.append(c);
					seenEscape = false;
					continue;
				}
				if (i + 1 == pattern.length()) {
					// Lone backslash at end of pattern is dropped.
					break;
				}
				char lookAhead = lookAhead(pattern, i);
				if (in_brackets > 0 && (lookAhead == ']' || lookAhead == '[')) {
					ignoreLastBracket = true;
				}
				if (lookAhead == '0' || (lookAhead >= 'a' && lookAhead <= 'z')
						|| (lookAhead >= 'A' && lookAhead <= 'Z')) {
					// Can be escaped in a glob pattern, but don't need to.
					// Escaping these is invalid in a Java regexp. Glob patterns
					// do not have the escaped constructs a Java regexp has
					// (like \d, \Q, \x, or also \0).
					break;
				}
				sb.append(c);
				break;

			case '[':
				if (in_brackets > 0) {
					if (!seenEscape) {
						sb.append('\\');
					}
					sb.append('[');
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
		try {
			return Pattern.compile(sb.toString(), Pattern.DOTALL);
		} catch (PatternSyntaxException e) {
			throw new InvalidPatternException(
					MessageFormat.format(JGitText.get().invalidIgnoreRule,
							pattern),
					pattern, e);
		}
	}

	/**
	 * @param buffer
	 *            buffer
	 * @return zero of the buffer is empty, otherwise the last character from
	 *         buffer
	 */
	private static char lookBehind(StringBuilder buffer) {
		return buffer.length() > 0 ? buffer.charAt(buffer.length() - 1) : 0;
	}

	/**
	 * Lookahead next character after given index in pattern
	 *
	 * @param pattern
	 *            the pattern
	 * @param i
	 *            current pointer in the pattern
	 * @return zero if the index is out of range, otherwise the next character
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

	static String unescape(String s) {
		if (s.indexOf('\\') < 0) {
			return s;
		}
		int len = s.length();
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++) {
			char ch = s.charAt(i);
			if (ch == '\\') {
				if (i + 1 < len) {
					char next = s.charAt(i + 1);
					if (next == '\\') {
						sb.append(ch);
						i++;
					}
				}
			} else {
				sb.append(ch);
			}
		}
		return sb.toString();
	}

}
