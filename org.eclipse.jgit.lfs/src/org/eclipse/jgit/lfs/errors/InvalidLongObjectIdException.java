/*
 * Copyright (C) 2009, Jonas Fonseca <fonseca@diku.dk>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lfs.errors;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.text.MessageFormat;

import org.eclipse.jgit.lfs.internal.LfsText;

/**
 * Thrown when an invalid long object id is passed in as an argument.
 *
 * @since 4.3
 */
public class InvalidLongObjectIdException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	/**
	 * Create exception with bytes of the invalid object id.
	 *
	 * @param bytes containing the invalid id.
	 * @param offset in the byte array where the error occurred.
	 * @param length of the sequence of invalid bytes.
	 */
	public InvalidLongObjectIdException(byte[] bytes, int offset, int length) {
		super(MessageFormat.format(LfsText.get().invalidLongId,
				asAscii(bytes, offset, length)));
	}

	/**
	 * <p>Constructor for InvalidLongObjectIdException.</p>
	 *
	 * @param idString
	 *            String containing the invalid id
	 */
	public InvalidLongObjectIdException(String idString) {
		super(MessageFormat.format(LfsText.get().invalidLongId, idString));
	}

	private static String asAscii(byte[] bytes, int offset, int length) {
		try {
			return new String(bytes, offset, length, US_ASCII);
		} catch (StringIndexOutOfBoundsException e) {
			return ""; //$NON-NLS-1$
		}
	}
}
