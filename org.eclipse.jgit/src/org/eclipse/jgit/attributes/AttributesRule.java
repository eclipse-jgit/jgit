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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.attributes.Attribute.State;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.fnmatch.FileNameMatcher;
import org.eclipse.jgit.ignore.IgnoreRule;

/**
 * A single ignore rule corresponding to one line in a .gitattributes file.
 * Parses the pattern
 *
 * Inspiration from: {@link IgnoreRule}
 */
public class AttributesRule {

	private static List<Attribute> parseAttributes(String attributesLine) {
		ArrayList<Attribute> result = new ArrayList<Attribute>();
		for (String attribute : attributesLine.split(" ")) { //$NON-NLS-1$
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

	private String pattern;

	private List<Attribute> attributes;

	private boolean nameOnly;

	private boolean dirOnly;

	private FileNameMatcher matcher;

	/**
	 * Create a new ignore rule with the given pattern. Assumes that the pattern
	 * is already trimmed.
	 *
	 * @param pattern
	 *            Base pattern for the attributes rule. This pattern will be
	 *            parsed to generate rule parameters.
	 * @param attributes
	 *            the rule attributes. This string will be parsed to read the
	 *            attributes.
	 */
	public AttributesRule(String pattern, String attributes) {
		this.pattern = pattern;
		this.attributes = parseAttributes(attributes);
		nameOnly = false;
		dirOnly = false;
		matcher = null;
		setup();
	}

	/**
	 * @return True if the pattern should match directories only
	 */
	public boolean dirOnly() {
		return dirOnly;
	}

	private boolean doesMatchDirectoryExpectations(boolean isDirectory,
			int segmentIdx, int segmentLength) {
		// The segment we are checking is a directory, expectations are met.
		if (segmentIdx < segmentLength - 1) {
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
	 * @return True if the pattern is just a file name and not a path
	 */
	public boolean getNameOnly() {
		return nameOnly;
	}

	/**
	 * @return The blob pattern to be used as a matcher
	 */
	public String getPattern() {
		return pattern;
	}

	/**
	 * Returns true if a match was made. <br>
	 *
	 *
	 * @param relativTarget
	 *            Name pattern of the file, relative to the base directory of
	 *            this rule
	 * @param isDirectory
	 *            Whether the target file is a directory or not
	 * @return True if a match was made.
	 */
	public boolean isMatch(String relativTarget, boolean isDirectory) {
		String target = relativTarget;
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
			if ((target).startsWith(pattern + "/")) //$NON-NLS-1$
				return true;

			if (nameOnly) {
				// Iterate through each sub-name
				final String[] segments = target.split("/"); //$NON-NLS-1$
				for (int idx = 0; idx < segments.length; idx++) {
					final String segmentName = segments[idx];
					if (segmentName.equals(pattern)
							&& doesMatchDirectoryExpectations(isDirectory, idx,
									segments.length))
						return true;
				}
			}

		} else {
			matcher.append(target);
			if (matcher.isMatch())
				return true;

			final String[] segments = target.split("/"); //$NON-NLS-1$
			if (nameOnly) {
				for (int idx = 0; idx < segments.length; idx++) {
					final String segmentName = segments[idx];
					// Iterate through each sub-directory
					matcher.reset();
					matcher.append(segmentName);
					if (matcher.isMatch()
							&& doesMatchDirectoryExpectations(isDirectory, idx,
									segments.length))
						return true;
				}
			} else {
				// TODO: This is the slowest operation
				// This matches e.g. "/src/ne?" to "/src/new/file.c"
				matcher.reset();
				for (int idx = 0; idx < segments.length; idx++) {
					final String segmentName = segments[idx];
					if (segmentName.length() > 0) {
						matcher.append("/" + segmentName); //$NON-NLS-1$
					}

					if (matcher.isMatch()
							&& doesMatchDirectoryExpectations(isDirectory, idx,
									segments.length))
						return true;
				}
			}
		}

		return false;
	}

	/**
	 * Remove leading/trailing characters as needed. Set up rule variables for
	 * later matching. Parse attributes.
	 */
	private void setup() {
		int endIndex = pattern.length();

		if (pattern.endsWith("/")) { //$NON-NLS-1$
			endIndex--;
			dirOnly = true;
		}

		pattern = pattern.substring(0, endIndex);
		boolean hasSlash = pattern.contains("/"); //$NON-NLS-1$

		if (!hasSlash)
			nameOnly = true;
		else if (!pattern.startsWith("/")) { //$NON-NLS-1$
			// Contains "/" but does not start with one
			// Adding / to the start should not interfere with matching
			pattern = "/" + pattern; //$NON-NLS-1$
		}

		if (pattern.contains("*") || pattern.contains("?") || pattern.contains("[")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			try {
				matcher = new FileNameMatcher(pattern, Character.valueOf('/'));
			} catch (InvalidPatternException e) {
				// Ignore pattern exceptions
			}
		}
	}
}