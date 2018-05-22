/*
 * Copyright (C) 2008, Florian Koeberle <florianskarten@web.de>
 * Copyright (C) 2008, Florian Köberle <florianskarten@web.de>
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

package org.eclipse.jgit.fnmatch;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.internal.JGitText;

final class GroupHead extends AbstractHead {
	private final List<CharacterPattern> characterClasses;

	private static final Pattern REGEX_PATTERN = Pattern
			.compile("([^-][-][^-]|\\[[.:=].*?[.:=]\\])"); //$NON-NLS-1$

	private final boolean inverse;

	GroupHead(String pattern, String wholePattern)
			throws InvalidPatternException {
		super(false);
		this.characterClasses = new ArrayList<>();
		this.inverse = pattern.startsWith("!"); //$NON-NLS-1$
		if (inverse) {
			pattern = pattern.substring(1);
		}
		final Matcher matcher = REGEX_PATTERN.matcher(pattern);
		while (matcher.find()) {
			final String characterClass = matcher.group(0);
			if (characterClass.length() == 3 && characterClass.charAt(1) == '-') {
				final char start = characterClass.charAt(0);
				final char end = characterClass.charAt(2);
				characterClasses.add(new CharacterRange(start, end));
			} else if (characterClass.equals("[:alnum:]")) { //$NON-NLS-1$
				characterClasses.add(LetterPattern.INSTANCE);
				characterClasses.add(DigitPattern.INSTANCE);
			} else if (characterClass.equals("[:alpha:]")) { //$NON-NLS-1$
				characterClasses.add(LetterPattern.INSTANCE);
			} else if (characterClass.equals("[:blank:]")) { //$NON-NLS-1$
				characterClasses.add(new OneCharacterPattern(' '));
				characterClasses.add(new OneCharacterPattern('\t'));
			} else if (characterClass.equals("[:cntrl:]")) { //$NON-NLS-1$
				characterClasses.add(new CharacterRange('\u0000', '\u001F'));
				characterClasses.add(new OneCharacterPattern('\u007F'));
			} else if (characterClass.equals("[:digit:]")) { //$NON-NLS-1$
				characterClasses.add(DigitPattern.INSTANCE);
			} else if (characterClass.equals("[:graph:]")) { //$NON-NLS-1$
				characterClasses.add(new CharacterRange('\u0021', '\u007E'));
				characterClasses.add(LetterPattern.INSTANCE);
				characterClasses.add(DigitPattern.INSTANCE);
			} else if (characterClass.equals("[:lower:]")) { //$NON-NLS-1$
				characterClasses.add(LowerPattern.INSTANCE);
			} else if (characterClass.equals("[:print:]")) { //$NON-NLS-1$
				characterClasses.add(new CharacterRange('\u0020', '\u007E'));
				characterClasses.add(LetterPattern.INSTANCE);
				characterClasses.add(DigitPattern.INSTANCE);
			} else if (characterClass.equals("[:punct:]")) { //$NON-NLS-1$
				characterClasses.add(PunctPattern.INSTANCE);
			} else if (characterClass.equals("[:space:]")) { //$NON-NLS-1$
				characterClasses.add(WhitespacePattern.INSTANCE);
			} else if (characterClass.equals("[:upper:]")) { //$NON-NLS-1$
				characterClasses.add(UpperPattern.INSTANCE);
			} else if (characterClass.equals("[:xdigit:]")) { //$NON-NLS-1$
				characterClasses.add(new CharacterRange('0', '9'));
				characterClasses.add(new CharacterRange('a', 'f'));
				characterClasses.add(new CharacterRange('A', 'F'));
			} else if (characterClass.equals("[:word:]")) { //$NON-NLS-1$
				characterClasses.add(new OneCharacterPattern('_'));
				characterClasses.add(LetterPattern.INSTANCE);
				characterClasses.add(DigitPattern.INSTANCE);
			} else {
				final String message = MessageFormat.format(
						JGitText.get().characterClassIsNotSupported,
						characterClass);
				throw new InvalidPatternException(message, wholePattern);
			}

			pattern = matcher.replaceFirst(""); //$NON-NLS-1$
			matcher.reset(pattern);
		}
		// pattern contains now no ranges
		for (int i = 0; i < pattern.length(); i++) {
			final char c = pattern.charAt(i);
			characterClasses.add(new OneCharacterPattern(c));
		}
	}

	/** {@inheritDoc} */
	@Override
	protected final boolean matches(char c) {
		for (CharacterPattern pattern : characterClasses) {
			if (pattern.matches(c)) {
				return !inverse;
			}
		}
		return inverse;
	}

	private interface CharacterPattern {
		/**
		 * @param c
		 *            the character to test
		 * @return returns true if the character matches a pattern.
		 */
		boolean matches(char c);
	}

	private static final class CharacterRange implements CharacterPattern {
		private final char start;

		private final char end;

		CharacterRange(char start, char end) {
			this.start = start;
			this.end = end;
		}

		@Override
		public final boolean matches(char c) {
			return start <= c && c <= end;
		}
	}

	private static final class DigitPattern implements CharacterPattern {
		static final GroupHead.DigitPattern INSTANCE = new DigitPattern();

		@Override
		public final boolean matches(char c) {
			return Character.isDigit(c);
		}
	}

	private static final class LetterPattern implements CharacterPattern {
		static final GroupHead.LetterPattern INSTANCE = new LetterPattern();

		@Override
		public final boolean matches(char c) {
			return Character.isLetter(c);
		}
	}

	private static final class LowerPattern implements CharacterPattern {
		static final GroupHead.LowerPattern INSTANCE = new LowerPattern();

		@Override
		public final boolean matches(char c) {
			return Character.isLowerCase(c);
		}
	}

	private static final class UpperPattern implements CharacterPattern {
		static final GroupHead.UpperPattern INSTANCE = new UpperPattern();

		@Override
		public final boolean matches(char c) {
			return Character.isUpperCase(c);
		}
	}

	private static final class WhitespacePattern implements CharacterPattern {
		static final GroupHead.WhitespacePattern INSTANCE = new WhitespacePattern();

		@Override
		public final boolean matches(char c) {
			return Character.isWhitespace(c);
		}
	}

	private static final class OneCharacterPattern implements CharacterPattern {
		private char expectedCharacter;

		OneCharacterPattern(char c) {
			this.expectedCharacter = c;
		}

		@Override
		public final boolean matches(char c) {
			return this.expectedCharacter == c;
		}
	}

	private static final class PunctPattern implements CharacterPattern {
		static final GroupHead.PunctPattern INSTANCE = new PunctPattern();

		private static String punctCharacters = "-!\"#$%&'()*+,./:;<=>?@[\\]_`{|}~"; //$NON-NLS-1$

		@Override
		public boolean matches(char c) {
			return punctCharacters.indexOf(c) != -1;
		}
	}

}
