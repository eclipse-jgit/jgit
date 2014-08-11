/*******************************************************************************
 * Copyright (c) 2014 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/
package org.eclipse.jgit.ignore2;

import static org.eclipse.jgit.fnmatch2.Strings.stripTrailing;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.fnmatch2.*;
import org.eclipse.jgit.ignore.IgnoreRule;

/**
 * "Fast" git ignore rule implementation (compared with {@link IgnoreRule}).
 * <p>
 * This class is immutable and thread safe.
 *
 * @since 3.5
 */
public class FastIgnoreRule {

	private static final char PATH_SEPARATOR = '/';
	private static final NoResultMatcher NO_MATCH = new NoResultMatcher();

	private final IMatcher matcher;
	private final boolean inverse;
	private final boolean dirOnly;

	/**
	 *
	 * @param pattern
	 *            ignore pattern as described in <a href=
	 *            "https://www.kernel.org/pub/software/scm/git/docs/gitignore.html"
	 *            >git manual</a>. If pattern is invalid or is not a pattern
	 *            (comment), this rule doesn't match anything.
	 */
	public FastIgnoreRule(String pattern) {
		if(pattern == null){
			throw new IllegalArgumentException("Pattern must be not null!");
		}
		pattern = pattern.trim();
		if (pattern.length() == 0) {
			dirOnly = false;
			inverse = false;
			this.matcher = NO_MATCH;
			return;
		}
		inverse = pattern.charAt(0) == '!';
		if(inverse){
			pattern = pattern.substring(1);
			if (pattern.length() == 0) {
				dirOnly = false;
				this.matcher = NO_MATCH;
				return;
			}
		}
		if(pattern.charAt(0) == '#'){
			this.matcher = NO_MATCH;
			dirOnly = false;
		} else {
			dirOnly = pattern.charAt(pattern.length() - 1) == PATH_SEPARATOR;
			if(dirOnly) {
				pattern = stripTrailing(pattern, PATH_SEPARATOR);
				if (pattern.length() == 0) {
					this.matcher = NO_MATCH;
					return;
				}
			}
			IMatcher m;
			try {
				m = PathMatcher.createPathMatcher(pattern,  Character.valueOf(PATH_SEPARATOR), dirOnly);
			} catch (InvalidPatternException e) {
				m = NO_MATCH;
			}
			this.matcher = m;
		}
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
	 *         target is ignored. Call {@link IgnoreRule#getResult()
	 *         getResult()} for the result.
	 */
	public boolean isMatch(String path, boolean directory) {
		if(path == null){
			return false;
		}
		path = path.trim();
		if (path.length() == 0) {
			return false;
		}
		boolean match = matcher.matches(path, directory);
		return match;
	}

	/**
	 * @return
	 * 			  True if the pattern is just a file name and not a path
	 */
	public boolean getNameOnly() {
		return !(matcher instanceof PathMatcher);
	}

	/**
	 *
	 * @return
	 * 			  True if the pattern should match directories only
	 */
	public boolean dirOnly() {
		return dirOnly;
	}

	/**
	 * Indicates whether the rule is non-negation or negation.
	 * @return
	 * 			  True if the pattern had a "!" in front of it
	 */
	public boolean getNegation() {
		return inverse;
	}

	/**
	 * Indicates whether the rule is non-negation or negation.
	 *
	 * @return
	 * 			  True if the target is to be ignored, false otherwise.
	 */
	public boolean getResult() {
		return !inverse;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (inverse) {
			sb.append('!');
		}
		sb.append(matcher);
		if (dirOnly) {
			sb.append(PATH_SEPARATOR);
		}
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
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof FastIgnoreRule)) {
			return false;
		}

		FastIgnoreRule other = (FastIgnoreRule) obj;
		if(inverse != other.inverse) {
			return false;
		}
		if(dirOnly != other.dirOnly) {
			return false;
		}
		return matcher.equals(other.matcher);
	}

	static final class NoResultMatcher implements IMatcher {

		public boolean matches(String path, boolean assumeDirectory) {
			return false;
		}

		public boolean matches(String segment, int startIncl, int endExcl,
				boolean assumeDirectory) {
			return false;
		}
	}
}
