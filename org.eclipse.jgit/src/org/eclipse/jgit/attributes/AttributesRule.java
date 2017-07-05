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
import java.util.Collections;
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
 *
 * @since 3.7
 */
public class AttributesRule {

	/**
	 * regular expression for splitting attributes - space, tab and \r (the C
	 * implementation oddly enough allows \r between attributes)
	 * */
	private static final String ATTRIBUTES_SPLIT_REGEX = "[ \t\r]"; //$NON-NLS-1$

	private static List<Attribute> parseAttributes(String attributesLine) {
		// the C implementation oddly enough allows \r between attributes too.
		ArrayList<Attribute> result = new ArrayList<>();
		for (String attribute : attributesLine.split(ATTRIBUTES_SPLIT_REGEX)) {
			attribute = attribute.trim();
			if (attribute.length() == 0)
				continue;

			if (attribute.startsWith("-")) {//$NON-NLS-1$
				if (attribute.length() > 1)
					result.add(new Attribute(attribute.substring(1),
							State.UNSET));
				continue;
			}

			if (attribute.startsWith("!")) {//$NON-NLS-1$
				if (attribute.length() > 1)
					result.add(new Attribute(attribute.substring(1),
							State.UNSPECIFIED));
				continue;
			}

			final int equalsIndex = attribute.indexOf("="); //$NON-NLS-1$
			if (equalsIndex == -1)
				result.add(new Attribute(attribute, State.SET));
			else {
				String attributeKey = attribute.substring(0, equalsIndex);
				if (attributeKey.length() > 0) {
					String attributeValue = attribute
							.substring(equalsIndex + 1);
					result.add(new Attribute(attributeKey, attributeValue));
				}
			}
		}
		return result;
	}

	private final String pattern;
	private final List<Attribute> attributes;

	private final boolean nameOnly;

	private final boolean dirOnly;

	private final IMatcher matcher;

	/**
	 * Create a new attribute rule with the given pattern. Assumes that the
	 * pattern is already trimmed.
	 *
	 * @param pattern
	 *            Base pattern for the attributes rule. This pattern will be
	 *            parsed to generate rule parameters. It can not be
	 *            <code>null</code>.
	 * @param attributes
	 *            the rule attributes. This string will be parsed to read the
	 *            attributes.
	 */
	public AttributesRule(String pattern, String attributes) {
		this.attributes = parseAttributes(attributes);

		if (pattern.endsWith("/")) { //$NON-NLS-1$
			pattern = pattern.substring(0, pattern.length() - 1);
			dirOnly = true;
		} else {
			dirOnly = false;
		}

		int slashIndex = pattern.indexOf('/');

		if (slashIndex < 0) {
			nameOnly = true;
		} else if (slashIndex == 0) {
			nameOnly = false;
		} else {
			nameOnly = false;
			// Contains "/" but does not start with one
			// Adding / to the start should not interfere with matching
			pattern = "/" + pattern; //$NON-NLS-1$
		}

		IMatcher candidateMatcher = NO_MATCH;
		try {
			candidateMatcher = PathMatcher.createPathMatcher(pattern,
					Character.valueOf(FastIgnoreRule.PATH_SEPARATOR), dirOnly);
		} catch (InvalidPatternException e) {
			// ignore: invalid patterns are silently ignored
		}
		this.matcher = candidateMatcher;
		this.pattern = pattern;
	}

	/**
	 * @return True if the pattern should match directories only
	 * @since 4.3
	 */
	public boolean isDirOnly() {
		return dirOnly;
	}

	/**
	 * Returns the attributes.
	 *
	 * @return an unmodifiable list of attributes (never returns
	 *         <code>null</code>)
	 */
	public List<Attribute> getAttributes() {
		return Collections.unmodifiableList(attributes);
	}

	/**
	 * @return <code>true</code> if the pattern is just a file name and not a
	 *         path
	 */
	public boolean isNameOnly() {
		return nameOnly;
	}

	/**
	 * @return The blob pattern to be used as a matcher (never returns
	 *         <code>null</code>)
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
		if (relativeTarget == null)
			return false;
		if (relativeTarget.length() == 0)
			return false;
		boolean match = matcher.matches(relativeTarget, isDirectory);
		return match;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(pattern);
		for (Attribute a : attributes) {
			sb.append(" "); //$NON-NLS-1$
			sb.append(a);
		}
		return sb.toString();

	}
}
