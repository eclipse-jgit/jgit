/*
 * Copyright (C) 2021, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.eclipse.jgit.util.NB;

/**
 * Chooses the specific implementation of the object-size index based on the
 * file version.
 */
public class PackObjectSizeIndexLoader {

	/**
	 * Read an object size index from the stream
	 *
	 * @param in
	 *            input stream at the beginning of the object size data
	 * @return an implementation of the object size index
	 * @throws IOException
	 *             error reading the streams
	 */
	public static PackObjectSizeIndex load(InputStream in) throws IOException {
		byte[] header = in.readNBytes(4);
		if (!Arrays.equals(header, PackObjectSizeIndexWriter.HEADER)) {
			throw new IOException("Stream is not an object index"); //$NON-NLS-1$
		}

		int version = NB.decodeInt32(in.readNBytes(4), 0);
		switch (version) {
		case 1:
			return new PackObjectSizeIndexV1(in);
		default:
			throw new IOException("Unknown object size version: " + version); //$NON-NLS-1$
		}
	}
}
