/*
 * Copyright (C) 2008, Florian Koeberle <florianskarten@web.de>
 * Copyright (C) 2008, Florian Köberle <florianskarten@web.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.fnmatch;

import java.util.List;

import org.eclipse.jgit.internal.JGitText;

abstract class AbstractHead implements Head {
	private List<Head> newHeads = null;

	private final boolean star;

	/**
	 * Whether the char matches
	 *
	 * @param c
	 *            a char.
	 * @return whether the char matches
	 */
	protected abstract boolean matches(char c);

	AbstractHead(boolean star) {
		this.star = star;
	}

	/**
	 * Set {@link org.eclipse.jgit.fnmatch.Head}s which will not be modified.
	 *
	 * @param newHeads
	 *            a list of {@link org.eclipse.jgit.fnmatch.Head}s which will
	 *            not be modified.
	 */
	public final void setNewHeads(List<Head> newHeads) {
		if (this.newHeads != null)
			throw new IllegalStateException(JGitText.get().propertyIsAlreadyNonNull);
		this.newHeads = newHeads;
	}

	/** {@inheritDoc} */
	@Override
	public List<Head> getNextHeads(char c) {
		if (matches(c)) {
			return newHeads;
		}
		return FileNameMatcher.EMPTY_HEAD_LIST;
	}

	boolean isStar() {
		return star;
	}
}
