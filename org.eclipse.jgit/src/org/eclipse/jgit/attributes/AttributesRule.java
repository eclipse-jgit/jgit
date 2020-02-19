/*
 * Copyright (C) 2010, 2017 Red Hat Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.attributes;

import static org.eclipse.jgit.ignore.IMatcher.NO_MATCH;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.attributes.Attribute.State;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.ignore.FastIgnoreRule;
import org.eclipse.jgit.ignore.IMatcher;
import org.eclipse.jgit.ignore.internal.PathMatcher;

/**
 * A single attributes rule corresponding to one line in a .gitattributes file.
 *
 * Inspiration from: {@link org.eclipse.jgit.ignore.FastIgnoreRule}
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
	 * Whether to match directories only
	 *
	 * @return {@code true} if the pattern should match directories only
	 * @since 4.3
	 */
	public boolean isDirOnly() {
		return dirOnly;
	}

	/**
	 * Return the attributes.
	 *
	 * @return an unmodifiable list of attributes (never returns
	 *         <code>null</code>)
	 */
	public List<Attribute> getAttributes() {
		return Collections.unmodifiableList(attributes);
	}

	/**
	 * Whether the pattern is only a file name and not a path
	 *
	 * @return <code>true</code> if the pattern is just a file name and not a
	 *         path
	 */
	public boolean isNameOnly() {
		return nameOnly;
	}

	/**
	 * Get the pattern
	 *
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
		boolean match = matcher.matches(relativeTarget, isDirectory, true);
		return match;
	}

	/** {@inheritDoc} */
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
