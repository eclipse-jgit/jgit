/*
 * Copyright (C) 2014, 2025 Andrey Loskutov <loskutov@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.ignore;

import static org.eclipse.jgit.ignore.IMatcher.NO_MATCH;
import static org.eclipse.jgit.ignore.internal.Strings.isDirectoryPattern;
import static org.eclipse.jgit.ignore.internal.Strings.stripTrailing;

import java.text.MessageFormat;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.ignore.internal.PathMatcher;
import org.eclipse.jgit.internal.JGitText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Fast" (compared with IgnoreRule) git ignore rule implementation supporting
 * also double star {@code **} pattern.
 * <p>
 * This class is immutable and thread safe.
 *
 * @since 3.6
 */
public class FastIgnoreRule {
	private static final Logger LOG = LoggerFactory
			.getLogger(FastIgnoreRule.class);

	/**
	 * Character used as default path separator for ignore entries
	 */
	public static final char PATH_SEPARATOR = '/';

	private IMatcher matcher;

	private boolean inverse;

	private boolean dirOnly;

	/**
	 * Constructor for FastIgnoreRule
	 *
	 * @param pattern
	 *            ignore pattern as described in <a href=
	 *            "https://www.kernel.org/pub/software/scm/git/docs/gitignore.html"
	 *            >git manual</a>. If pattern is invalid or is not a pattern
	 *            (comment), this rule doesn't match anything.
	 */
	public FastIgnoreRule(String pattern) {
		this();
		try {
			parse(pattern);
		} catch (InvalidPatternException e) {
			LOG.error(MessageFormat.format(JGitText.get().badIgnorePattern,
					e.getPattern()), e);
		}
	}

	FastIgnoreRule() {
		matcher = IMatcher.NO_MATCH;
	}

	void parse(String pattern) throws InvalidPatternException {
		if (pattern == null) {
			throw new IllegalArgumentException("Pattern must not be null!"); //$NON-NLS-1$
		}
		if (pattern.isEmpty() || pattern.charAt(0) == '#') {
			dirOnly = false;
			inverse = false;
			this.matcher = NO_MATCH;
			return;
		}
		inverse = pattern.charAt(0) == '!';
		if (inverse) {
			pattern = pattern.substring(1);
			if (pattern.isEmpty()
					|| pattern.length() == 1 && pattern.charAt(0) == '\\') {
				dirOnly = false;
				this.matcher = NO_MATCH;
				return;
			}
		}
		dirOnly = isDirectoryPattern(pattern);
		if (dirOnly) {
			pattern = pattern.stripTrailing();
			pattern = stripTrailing(pattern, PATH_SEPARATOR);
			if (pattern.isEmpty()
					|| pattern.length() == 1 && pattern.charAt(0) == '\\') {
				this.matcher = NO_MATCH;
				return;
			}
		}
		this.matcher = PathMatcher.createPathMatcher(pattern,
				Character.valueOf(PATH_SEPARATOR), dirOnly);
	}

	/**
	 * Returns true if a match was made. <br>
	 * This function does NOT return the actual ignore status of the target!
	 * Please consult {@link #getResult()} for the negation status. The actual
	 * ignore status may be true or false depending on whether this rule is an
	 * ignore rule or a negation rule.
	 *
	 * @param path
	 *            Name pattern of the file, relative to the base directory of
	 *            this rule
	 * @param directory
	 *            Whether the target file is a directory or not
	 * @return True if a match was made. This does not necessarily mean that the
	 *         target is ignored. Call {@link #getResult() getResult()} for the
	 *         result.
	 */
	public boolean isMatch(String path, boolean directory) {
		return isMatch(path, directory, false);
	}

	/**
	 * Returns true if a match was made. <br>
	 * This function does NOT return the actual ignore status of the target!
	 * Please consult {@link #getResult()} for the negation status. The actual
	 * ignore status may be true or false depending on whether this rule is an
	 * ignore rule or a negation rule.
	 *
	 * @param path
	 *            Name pattern of the file, relative to the base directory of
	 *            this rule
	 * @param directory
	 *            Whether the target file is a directory or not
	 * @param pathMatch
	 *            {@code true} if the match is for the full path: see
	 *            {@link IMatcher#matches(String, int, int)}
	 * @return True if a match was made. This does not necessarily mean that the
	 *         target is ignored. Call {@link #getResult() getResult()} for the
	 *         result.
	 * @since 4.11
	 */
	public boolean isMatch(String path, boolean directory, boolean pathMatch) {
		if (path == null)
			return false;
		if (path.length() == 0)
			return false;
		boolean match = matcher.matches(path, directory, pathMatch);
		return match;
	}

	/**
	 * Whether the pattern is just a file name and not a path
	 *
	 * @return {@code true} if the pattern is just a file name and not a path
	 */
	public boolean getNameOnly() {
		return !(matcher instanceof PathMatcher);
	}

	/**
	 * Whether the pattern should match directories only
	 *
	 * @return {@code true} if the pattern should match directories only
	 */
	public boolean dirOnly() {
		return dirOnly;
	}

	/**
	 * Indicates whether the rule is non-negation or negation.
	 *
	 * @return True if the pattern had a "!" in front of it
	 */
	public boolean getNegation() {
		return inverse;
	}

	/**
	 * Indicates whether the rule is non-negation or negation.
	 *
	 * @return True if the target is to be ignored, false otherwise.
	 */
	public boolean getResult() {
		return !inverse;
	}

	/**
	 * Whether the rule never matches
	 *
	 * @return {@code true} if the rule never matches (comment line or broken
	 *         pattern)
	 * @since 4.1
	 */
	public boolean isEmpty() {
		return matcher == NO_MATCH;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (inverse)
			sb.append('!');
		sb.append(matcher);
		if (dirOnly)
			sb.append(PATH_SEPARATOR);
		return sb.toString();

	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (inverse ? 1231 : 1237);
		result = prime * result + (dirOnly ? 1231 : 1237);
		result = prime * result + ((matcher == null) ? 0 : matcher.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof FastIgnoreRule))
			return false;

		FastIgnoreRule other = (FastIgnoreRule) obj;
		if (inverse != other.inverse)
			return false;
		if (dirOnly != other.dirOnly)
			return false;
		return matcher.equals(other.matcher);
	}
}
