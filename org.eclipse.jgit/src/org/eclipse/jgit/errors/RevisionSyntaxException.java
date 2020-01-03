/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2009, Vasyl' Vavrychuk <vvavrychuk@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;


/**
 * This signals a revision or object reference was not
 * properly formatted.
 */
public class RevisionSyntaxException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	private final String revstr;

	/**
	 * Construct a RevisionSyntaxException indicating a syntax problem with a
	 * revision (or object) string.
	 *
	 * @param revstr The problematic revision string
	 */
	public RevisionSyntaxException(String revstr) {
		this.revstr = revstr;
	}

	/**
	 * Construct a RevisionSyntaxException indicating a syntax problem with a
	 * revision (or object) string.
	 *
	 * @param message a specific reason
	 * @param revstr The problematic revision string
	 */
	public RevisionSyntaxException(String message, String revstr) {
		super(message);
		this.revstr = revstr;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return super.toString() + ":" + revstr; //$NON-NLS-1$
	}
}
