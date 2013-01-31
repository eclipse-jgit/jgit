/*
 * Copyright (C) 2010, Red Hat Inc.
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
package org.eclipse.jgit.attributes;

import static org.eclipse.jgit.ignore.internal.IMatcher.NO_MATCH;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.attributes.Attribute.State;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.ignore.FastIgnoreRule;
import org.eclipse.jgit.ignore.internal.IMatcher;
import org.eclipse.jgit.ignore.internal.PathMatcher;

/**
 * A single attributes rule corresponding to one line in a .gitattributes file.
 *
 * Inspiration from: {@link FastIgnoreRule}
 */
public class AttributesRule {

	/**
	 * regular expression for splitting attributes - space, tab and \r (the C
	 * implementation oddly enough allows \r between attributes)
	 * */
	private static final String ATTRIBUTES_SPLIT_REGEX = "[ \t\r]"; //$NON-NLS-1$

	private static List<Attribute> parseAttributes(String attributesLine) {
		// the C implementation oddly enough allows \r between attributes too.
		ArrayList<Attribute> result = new ArrayList<Attribute>();
		for (String attribute : attributesLine.split(ATTRIBUTES_SPLIT_REGEX)) {
			attribute = attribute.trim();
			if (attribute.length() == 0)
				continue;

			if (attribute.startsWith("-")) {//$NON-NLS-1$
				result.add(new Attribute(attribute.substring(1), State.UNSET));
				continue;
			}

			final int equalsIndex = attribute.indexOf("="); //$NON-NLS-1$
			if (equalsIndex == -1)
				result.add(new Attribute(attribute, State.SET));
			else
				result.add(new Attribute(attribute.substring(0, equalsIndex),
						attribute.substring(equalsIndex + 1)));
		}
		return result;
	}


	private final String pattern;
	private final List<Attribute> attributes;

	private boolean nameOnly;
	private boolean dirOnly;

	private IMatcher matcher;

	/**
	 * Create a new attribute rule with the given pattern. Assumes that the
	 * pattern is already trimmed.
	 *
	 * @param pattern
	 *            Base pattern for the attributes rule. This pattern will be
	 *            parsed to generate rule parameters.
	 * @param attributes
	 *            the rule attributes. This string will be parsed to read the
	 *            attributes.
	 */
	public AttributesRule(String pattern, String attributes) {
		this.attributes = parseAttributes(attributes);
		nameOnly = false;
		dirOnly = false;
		// Negative patterns are forbidden for git attributes
		if (pattern.charAt(0) == '!') {
			// We may need to throw an exception instead
			this.pattern = pattern;
			this.matcher = NO_MATCH;
			return;
		}

		int endIndex = pattern.length();

		if (pattern.endsWith("/")) { //$NON-NLS-1$
			endIndex--;
			dirOnly = true;
		}

		String pattern2 = pattern.substring(0, endIndex);
		boolean hasSlash = pattern2.contains("/"); //$NON-NLS-1$

		if (!hasSlash)
			nameOnly = true;
		else if (!pattern2.startsWith("/")) { //$NON-NLS-1$
			// Contains "/" but does not start with one
			// Adding / to the start should not interfere with matching
			pattern2 = "/" + pattern2; //$NON-NLS-1$
		}

		if (pattern2.contains("*") //$NON-NLS-1$
				|| pattern2.contains("?") //$NON-NLS-1$
				|| pattern2.contains("[")) { //$NON-NLS-1$
			try {
				matcher = PathMatcher.createPathMatcher(pattern2,
						Character.valueOf(FastIgnoreRule.PATH_SEPARATOR),
						dirOnly);
			} catch (InvalidPatternException e) {
				// Ignore pattern exceptions
			}
		}

		this.pattern = pattern2;
	}

	/**
	 * @return True if the pattern should match directories only
	 */
	public boolean dirOnly() {
		return dirOnly;
	}

	private boolean doesMatchDirectoryExpectations(boolean isDirectory,
			boolean lastSegment) {
		// The segment we are checking is a directory, expectations are met.
		if (!lastSegment) {
			return true;
		}

		// We are checking the last part of the segment for which isDirectory
		// has to be considered.
		return !dirOnly || isDirectory;
	}

	/**
	 * Returns the attributes.
	 *
	 * @return the attributes
	 */
	public List<Attribute> getAttributes() {
		return attributes;
	}

	/**
	 * @return <code>true</code> if the pattern is just a file name and not a
	 *         path
	 */
	public boolean isNameOnly() {
		return nameOnly;
	}

	/**
	 * @return The blob pattern to be used as a matcher
	 */
	public String getPattern() {
		return pattern;
	}

	/**
	 * Returns <code>true</code> if a match was made.
	 *
	 * @param relativeTarget
	 *            Name pattern of the file, relative to the base directory of
	 *            this rule
	 * @param isDirectory
	 *            Whether the target file is a directory or not
	 * @return True if a match was made.
	 */
	public boolean isMatch(String relativeTarget, boolean isDirectory) {
		String target = relativeTarget;
		if (!target.startsWith("/")) //$NON-NLS-1$
			target = "/" + target; //$NON-NLS-1$

		if (matcher == null) {
			if (target.equals(pattern)) {
				// Exact match
				if (dirOnly && !isDirectory)
					// Directory expectations not met
					return false;
				else
					// Directory expectations met
					return true;
			}

			/*
			 * Add slashes for startsWith check. This avoids matching e.g.
			 * "/src/new" to /src/newfile" but allows "/src/new" to match
			 * "/src/new/newfile", as is the git standard
			 */
			if (target.startsWith(pattern + "/")) //$NON-NLS-1$
				return true;

			if (nameOnly) {
				// Iterate through each sub-name
				int segmentStart = 0;
				int segmentEnd = target.indexOf('/');
				while (true) {
					if (segmentEnd > segmentStart
							&& target.regionMatches(segmentStart, pattern, 0,
									segmentEnd - segmentStart)
							&& doesMatchDirectoryExpectations(isDirectory, false))
						return true;
					else if (segmentEnd == -1
							&& target.regionMatches(segmentStart, pattern, 0,
									target.length() - segmentStart)
							&& doesMatchDirectoryExpectations(isDirectory, true))
						return true;

					if (segmentEnd == -1 || segmentStart >= target.length())
						// this was the last segment
						break;

					// next
					segmentStart = segmentEnd + 1;
					segmentEnd = target.indexOf('/', segmentStart);
				}
			}

		} else {
			return matcher.matches(target, isDirectory);
		}

		return false;
	}
}