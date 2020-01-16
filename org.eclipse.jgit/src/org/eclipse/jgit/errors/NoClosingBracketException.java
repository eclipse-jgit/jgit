/*
 * Copyright (C) 2008, Florian Köberle <florianskarten@web.de>
 * Copyright (C) 2009, Vasyl' Vavrychuk <vvavrychuk@gmail.com>
 * Copyright (C) 2009, Yann Simon <yann.simon.fr@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/**
 * Thrown when a pattern contains a character group which is open to the right
 * side or a character class which is open to the right side.
 */
public class NoClosingBracketException extends InvalidPatternException {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor for NoClosingBracketException
	 *
	 * @param indexOfOpeningBracket
	 *            the position of the [ character which has no ] character.
	 * @param openingBracket
	 *            the unclosed bracket.
	 * @param closingBracket
	 *            the missing closing bracket.
	 * @param pattern
	 *            the invalid pattern.
	 */
	public NoClosingBracketException(final int indexOfOpeningBracket,
			final String openingBracket, final String closingBracket,
			final String pattern) {
		super(createMessage(indexOfOpeningBracket, openingBracket,
				closingBracket), pattern);
	}

	private static String createMessage(final int indexOfOpeningBracket,
			final String openingBracket, final String closingBracket) {
		return MessageFormat.format(JGitText.get().noClosingBracket,
				closingBracket, openingBracket,
				Integer.valueOf(indexOfOpeningBracket));
	}
}
