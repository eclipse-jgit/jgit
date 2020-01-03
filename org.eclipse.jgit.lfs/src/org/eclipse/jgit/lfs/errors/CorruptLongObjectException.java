/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lfs.errors;

import org.eclipse.jgit.lfs.lib.AnyLongObjectId;

/**
 * Thrown when an object id is given that doesn't match the hash of the object's
 * content
 *
 * @since 4.3
 */
public class CorruptLongObjectException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	private final AnyLongObjectId id;

	private final AnyLongObjectId contentHash;

	/**
	 * Corrupt long object detected.
	 *
	 * @param id
	 *            id of the long object
	 * @param contentHash
	 *            hash of the long object's content
	 * @param message a {@link java.lang.String} object.
	 */
	public CorruptLongObjectException(AnyLongObjectId id,
			AnyLongObjectId contentHash,
			String message) {
		super(message);
		this.id = id;
		this.contentHash = contentHash;
	}

	/**
	 * Get the <code>id</code> of the object.
	 *
	 * @return the id of the object, i.e. the expected hash of the object's
	 *         content
	 */
	public AnyLongObjectId getId() {
		return id;
	}

	/**
	 * Get the <code>contentHash</code>.
	 *
	 * @return the actual hash of the object's content which doesn't match the
	 *         object's id when this exception is thrown which signals that the
	 *         object has been corrupted
	 */
	public AnyLongObjectId getContentHash() {
		return contentHash;
	}
}
