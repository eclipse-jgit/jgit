/*
 * Copyright (C) 2013, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

/**
 * A pack file extension.
 */
public enum PackExt {
	/** A pack file extension. */
	PACK("pack"), //$NON-NLS-1$

	/** A pack index file extension. */
	INDEX("idx"), //$NON-NLS-1$

	/** A keep pack file extension. */
	KEEP("keep"), //$NON-NLS-1$

	/** A pack bitmap index file extension. */
	BITMAP_INDEX("bitmap"), //$NON-NLS-1$

	/** A reftable file. */
	REFTABLE("ref"),

	/** An object size index. */
	OBJECT_SIZE_INDEX("objsize"); //$NON-NLS-1$

	private final String ext;

	private PackExt(String ext) {
		this.ext = ext;
	}

	/**
	 * Get the file extension.
	 *
	 * @return the file extension.
	 */
	public String getExtension() {
		return ext;
	}

	/**
	 * Get the position of the extension in the enum declaration.
	 *
	 * @return the position of the extension in the enum declaration.
	 */
	public int getPosition() {
		return ordinal();
	}

	/**
	 * Get the bit mask of the extension e.g {@code 1 << getPosition()}.
	 *
	 * @return the bit mask of the extension e.g {@code 1 << getPosition()}.
	 */
	public int getBit() {
		return 1 << getPosition();
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("PackExt[%s, bit=0x%s]", getExtension(), //$NON-NLS-1$
				Integer.toHexString(getBit()));
	}
}
