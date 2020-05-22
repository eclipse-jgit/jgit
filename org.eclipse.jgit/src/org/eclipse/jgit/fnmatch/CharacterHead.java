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

final class CharacterHead extends AbstractHead {
	private final char expectedCharacter;

	/**
	 * Constructor for CharacterHead
	 *
	 * @param expectedCharacter
	 *            expected {@code char}
	 */
	CharacterHead(char expectedCharacter) {
		super(false);
		this.expectedCharacter = expectedCharacter;
	}

	/** {@inheritDoc} */
	@Override
	protected final boolean matches(char c) {
		return c == expectedCharacter;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.valueOf(expectedCharacter);
	}

}
