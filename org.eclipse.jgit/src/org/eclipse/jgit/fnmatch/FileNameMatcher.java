/*
 * Copyright (C) 2008, Florian Köberle <florianskarten@web.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.fnmatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.errors.NoClosingBracketException;

/**
 * This class can be used to match filenames against fnmatch like patterns. It
 * is not thread save.
 * <p>
 * Supported are the wildcard characters * and ? and groups with:
 * <ul>
 * <li>characters e.g. [abc]</li>
 * <li>ranges e.g. [a-z]</li>
 * <li>the following character classes
 * <ul>
 * <li>[:alnum:]</li>
 * <li>[:alpha:]</li>
 * <li>[:blank:]</li>
 * <li>[:cntrl:]</li>
 * <li>[:digit:]</li>
 * <li>[:graph:]</li>
 * <li>[:lower:]</li>
 * <li>[:print:]</li>
 * <li>[:punct:]</li>
 * <li>[:space:]</li>
 * <li>[:upper:]</li>
 * <li>[:word:]</li>
 * <li>[:xdigit:]</li>
 * </ul>
 * e. g. [[:xdigit:]]</li>
 * </ul>
 * Any character can be escaped by prepending it with a \
 */
public class FileNameMatcher {
	static final List<Head> EMPTY_HEAD_LIST = Collections.emptyList();

	private static final Pattern characterClassStartPattern = Pattern
			.compile("\\[[.:=]"); //$NON-NLS-1$

	private List<Head> headsStartValue;

	private List<Head> heads;

	/**
	 * {{@link #extendStringToMatchByOneCharacter(char)} needs a list for the
	 * new heads, allocating a new array would be bad for the performance, as
	 * the method gets called very often.
	 *
	 */
	private List<Head> listForLocalUseage;

	/**
	 *
	 * @param headsStartValue
	 *            must be a list which will never be modified.
	 */
	private FileNameMatcher(List<Head> headsStartValue) {
		this(headsStartValue, headsStartValue);
	}

	/**
	 *
	 * @param headsStartValue
	 *            must be a list which will never be modified.
	 * @param heads
	 *            a list which will be cloned and then used as current head
	 *            list.
	 */
	private FileNameMatcher(final List<Head> headsStartValue,
			final List<Head> heads) {
		this.headsStartValue = headsStartValue;
		this.heads = new ArrayList<>(heads.size());
		this.heads.addAll(heads);
		this.listForLocalUseage = new ArrayList<>(heads.size());
	}

	/**
	 * Constructor for FileNameMatcher
	 *
	 * @param patternString
	 *            must contain a pattern which fnmatch would accept.
	 * @param invalidWildgetCharacter
	 *            if this parameter isn't null then this character will not
	 *            match at wildcards(* and ? are wildcards).
	 * @throws org.eclipse.jgit.errors.InvalidPatternException
	 *             if the patternString contains a invalid fnmatch pattern.
	 */
	public FileNameMatcher(final String patternString,
			final Character invalidWildgetCharacter)
			throws InvalidPatternException {
		this(createHeadsStartValues(patternString, invalidWildgetCharacter));
	}

	/**
	 * A Copy Constructor which creates a new
	 * {@link org.eclipse.jgit.fnmatch.FileNameMatcher} with the same state and
	 * reset point like <code>other</code>.
	 *
	 * @param other
	 *            another {@link org.eclipse.jgit.fnmatch.FileNameMatcher}
	 *            instance.
	 */
	public FileNameMatcher(FileNameMatcher other) {
		this(other.headsStartValue, other.heads);
	}

	private static List<Head> createHeadsStartValues(
			final String patternString, final Character invalidWildgetCharacter)
			throws InvalidPatternException {

		final List<AbstractHead> allHeads = parseHeads(patternString,
				invalidWildgetCharacter);

		List<Head> nextHeadsSuggestion = new ArrayList<>(2);
		nextHeadsSuggestion.add(LastHead.INSTANCE);
		for (int i = allHeads.size() - 1; i >= 0; i--) {
			final AbstractHead head = allHeads.get(i);

			// explanation:
			// a and * of the pattern "a*b"
			// need *b as newHeads
			// that's why * extends the list for it self and it's left neighbor.
			if (head.isStar()) {
				nextHeadsSuggestion.add(head);
				head.setNewHeads(nextHeadsSuggestion);
			} else {
				head.setNewHeads(nextHeadsSuggestion);
				nextHeadsSuggestion = new ArrayList<>(2);
				nextHeadsSuggestion.add(head);
			}
		}
		return nextHeadsSuggestion;
	}

	private static int findGroupEnd(final int indexOfStartBracket,
			final String pattern) throws InvalidPatternException {
		int firstValidCharClassIndex = indexOfStartBracket + 1;
		int firstValidEndBracketIndex = indexOfStartBracket + 2;

		if (indexOfStartBracket + 1 >= pattern.length())
			throw new NoClosingBracketException(indexOfStartBracket, "[", "]", //$NON-NLS-1$ //$NON-NLS-2$
					pattern);

		if (pattern.charAt(firstValidCharClassIndex) == '!') {
			firstValidCharClassIndex++;
			firstValidEndBracketIndex++;
		}

		final Matcher charClassStartMatcher = characterClassStartPattern
				.matcher(pattern);

		int groupEnd = -1;
		while (groupEnd == -1) {

			final int possibleGroupEnd = indexOfUnescaped(pattern, ']',
					firstValidEndBracketIndex);
			if (possibleGroupEnd == -1)
				throw new NoClosingBracketException(indexOfStartBracket, "[", //$NON-NLS-1$
						"]", pattern); //$NON-NLS-1$

			final boolean foundCharClass = charClassStartMatcher
					.find(firstValidCharClassIndex);

			if (foundCharClass
					&& charClassStartMatcher.start() < possibleGroupEnd) {

				final String classStart = charClassStartMatcher.group(0);
				final String classEnd = classStart.charAt(1) + "]"; //$NON-NLS-1$

				final int classStartIndex = charClassStartMatcher.start();
				final int classEndIndex = pattern.indexOf(classEnd,
						classStartIndex + 2);

				if (classEndIndex == -1)
					throw new NoClosingBracketException(classStartIndex,
							classStart, classEnd, pattern);

				firstValidCharClassIndex = classEndIndex + 2;
				firstValidEndBracketIndex = firstValidCharClassIndex;
			} else {
				groupEnd = possibleGroupEnd;
			}
		}
		return groupEnd;
	}

	private static List<AbstractHead> parseHeads(final String pattern,
			final Character invalidWildgetCharacter)
			throws InvalidPatternException {

		int currentIndex = 0;
		List<AbstractHead> heads = new ArrayList<>();
		while (currentIndex < pattern.length()) {
			final int groupStart = indexOfUnescaped(pattern, '[', currentIndex);
			if (groupStart == -1) {
				final String patternPart = pattern.substring(currentIndex);
				heads.addAll(createSimpleHeads(patternPart,
						invalidWildgetCharacter));
				currentIndex = pattern.length();
			} else {
				final String patternPart = pattern.substring(currentIndex,
						groupStart);
				heads.addAll(createSimpleHeads(patternPart,
						invalidWildgetCharacter));

				final int groupEnd = findGroupEnd(groupStart, pattern);
				final String groupPart = pattern.substring(groupStart + 1,
						groupEnd);
				heads.add(new GroupHead(groupPart, pattern));
				currentIndex = groupEnd + 1;
			}
		}
		return heads;
	}

	private static List<AbstractHead> createSimpleHeads(
			final String patternPart, final Character invalidWildgetCharacter) {
		final List<AbstractHead> heads = new ArrayList<>(
				patternPart.length());

		boolean escaped = false;
		for (int i = 0; i < patternPart.length(); i++) {
			final char c = patternPart.charAt(i);
			if (escaped) {
				final CharacterHead head = new CharacterHead(c);
				heads.add(head);
				escaped = false;
			} else {
				switch (c) {
				case '*': {
					final AbstractHead head = createWildCardHead(
							invalidWildgetCharacter, true);
					heads.add(head);
					break;
				}
				case '?': {
					final AbstractHead head = createWildCardHead(
							invalidWildgetCharacter, false);
					heads.add(head);
					break;
				}
				case '\\':
					escaped = true;
					break;
				default:
					final CharacterHead head = new CharacterHead(c);
					heads.add(head);
				}
			}
		}
		return heads;
	}

	private static AbstractHead createWildCardHead(
			final Character invalidWildgetCharacter, final boolean star) {
		if (invalidWildgetCharacter != null) {
			return new RestrictedWildCardHead(invalidWildgetCharacter
					.charValue(), star);
		}
		return new WildCardHead(star);
	}

	/**
	 * @param c new character to append
	 * @return true to continue, false if the matcher can stop appending
	 */
	private boolean extendStringToMatchByOneCharacter(char c) {
		final List<Head> newHeads = listForLocalUseage;
		newHeads.clear();
		List<Head> lastAddedHeads = null;
		for (int i = 0; i < heads.size(); i++) {
			final Head head = heads.get(i);
			final List<Head> headsToAdd = head.getNextHeads(c);
			// Why the next performance optimization isn't wrong:
			// Some times two heads return the very same list.
			// We save future effort if we don't add these heads again.
			// This is the case with the heads "a" and "*" of "a*b" which
			// both can return the list ["*","b"]
			if (headsToAdd != lastAddedHeads) {
				if (!headsToAdd.isEmpty())
					newHeads.addAll(headsToAdd);
				lastAddedHeads = headsToAdd;
			}
		}
		listForLocalUseage = heads;
		heads = newHeads;
		return !newHeads.isEmpty();
	}

	private static int indexOfUnescaped(final String searchString,
			final char ch, final int fromIndex) {
		for (int i = fromIndex; i < searchString.length(); i++) {
			char current = searchString.charAt(i);
			if (current == ch)
				return i;
			if (current == '\\')
				i++; // Skip the next char as it is escaped }
		}
		return -1;
	}

	/**
	 * Append to the string which is matched against the patterns of this class
	 *
	 * @param stringToMatch
	 *            extends the string which is matched against the patterns of
	 *            this class.
	 */
	public void append(String stringToMatch) {
		for (int i = 0; i < stringToMatch.length(); i++) {
			final char c = stringToMatch.charAt(i);
			if (!extendStringToMatchByOneCharacter(c))
				break;
		}
	}

	/**
	 * Resets this matcher to it's state right after construction.
	 */
	public void reset() {
		heads.clear();
		heads.addAll(headsStartValue);
	}

	/**
	 * Create a {@link org.eclipse.jgit.fnmatch.FileNameMatcher} instance which
	 * uses the same pattern like this matcher, but has the current state of
	 * this matcher as reset and start point
	 *
	 * @return a {@link org.eclipse.jgit.fnmatch.FileNameMatcher} instance which
	 *         uses the same pattern like this matcher, but has the current
	 *         state of this matcher as reset and start point.
	 */
	public FileNameMatcher createMatcherForSuffix() {
		final List<Head> copyOfHeads = new ArrayList<>(heads.size());
		copyOfHeads.addAll(heads);
		return new FileNameMatcher(copyOfHeads);
	}

	/**
	 * Whether the matcher matches
	 *
	 * @return whether the matcher matches
	 */
	public boolean isMatch() {
		if (heads.isEmpty())
			return false;

		final ListIterator<Head> headIterator = heads
				.listIterator(heads.size());
		while (headIterator.hasPrevious()) {
			final Head head = headIterator.previous();
			if (head == LastHead.INSTANCE) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether a match can be appended
	 *
	 * @return a boolean.
	 */
	public boolean canAppendMatch() {
		for (Head head : heads) {
			if (head != LastHead.INSTANCE) {
				return true;
			}
		}
		return false;
	}
}
