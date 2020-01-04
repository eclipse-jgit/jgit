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

final class RestrictedWildCardHead extends AbstractHead {
	private final char excludedCharacter;

	RestrictedWildCardHead(char excludedCharacter, boolean star) {
		super(star);
		this.excludedCharacter = excludedCharacter;
	}

	/** {@inheritDoc} */
	@Override
	protected final boolean matches(char c) {
		return c != excludedCharacter;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return isStar() ? "*" : "?"; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
