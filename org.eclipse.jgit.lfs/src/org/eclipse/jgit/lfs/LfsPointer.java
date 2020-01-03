/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Locale;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lfs.lib.LongObjectId;

/**
 * Represents an LFS pointer file
 *
 * @since 4.6
 */
public class LfsPointer implements Comparable<LfsPointer> {
	/**
	 * The version of the LfsPointer file format
	 */
	public static final String VERSION = "https://git-lfs.github.com/spec/v1"; //$NON-NLS-1$

	/**
	 * The version of the LfsPointer file format using legacy URL
	 * @since 4.7
	 */
	public static final String VERSION_LEGACY = "https://hawser.github.com/spec/v1"; //$NON-NLS-1$

	/**
	 * Don't inspect files that are larger than this threshold to avoid
	 * excessive reading. No pointer file should be larger than this.
	 * @since 4.11
	 */
	public static final int SIZE_THRESHOLD = 200;

	/**
	 * The name of the hash function as used in the pointer files. This will
	 * evaluate to "sha256"
	 */
	public static final String HASH_FUNCTION_NAME = Constants.LONG_HASH_FUNCTION
			.toLowerCase(Locale.ROOT).replace("-", ""); //$NON-NLS-1$ //$NON-NLS-2$

	private AnyLongObjectId oid;

	private long size;

	/**
	 * <p>Constructor for LfsPointer.</p>
	 *
	 * @param oid
	 *            the id of the content
	 * @param size
	 *            the size of the content
	 */
	public LfsPointer(AnyLongObjectId oid, long size) {
		this.oid = oid;
		this.size = size;
	}

	/**
	 * <p>Getter for the field <code>oid</code>.</p>
	 *
	 * @return the id of the content
	 */
	public AnyLongObjectId getOid() {
		return oid;
	}

	/**
	 * <p>Getter for the field <code>size</code>.</p>
	 *
	 * @return the size of the content
	 */
	public long getSize() {
		return size;
	}

	/**
	 * Encode this object into the LFS format defined by {@link #VERSION}
	 *
	 * @param out
	 *            the {@link java.io.OutputStream} into which the encoded data should be
	 *            written
	 */
	public void encode(OutputStream out) {
		try (PrintStream ps = new PrintStream(out, false,
				UTF_8.name())) {
			ps.print("version "); //$NON-NLS-1$
			ps.print(VERSION + "\n"); //$NON-NLS-1$
			ps.print("oid " + HASH_FUNCTION_NAME + ":"); //$NON-NLS-1$ //$NON-NLS-2$
			ps.print(oid.name() + "\n"); //$NON-NLS-1$
			ps.print("size "); //$NON-NLS-1$
			ps.print(size + "\n"); //$NON-NLS-1$
		} catch (UnsupportedEncodingException e) {
			// should not happen, we are using a standard charset
			throw new UnsupportedCharsetException(UTF_8.name());
		}
	}

	/**
	 * Try to parse the data provided by an InputStream to the format defined by
	 * {@link #VERSION}
	 *
	 * @param in
	 *            the {@link java.io.InputStream} from where to read the data
	 * @return an {@link org.eclipse.jgit.lfs.LfsPointer} or <code>null</code>
	 *         if the stream was not parseable as LfsPointer
	 * @throws java.io.IOException
	 */
	@Nullable
	public static LfsPointer parseLfsPointer(InputStream in)
			throws IOException {
		boolean versionLine = false;
		LongObjectId id = null;
		long sz = -1;

		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(in, UTF_8))) {
			for (String s = br.readLine(); s != null; s = br.readLine()) {
				if (s.startsWith("#") || s.length() == 0) { //$NON-NLS-1$
					continue;
				} else if (s.startsWith("version") && s.length() > 8 //$NON-NLS-1$
						&& (s.substring(8).trim().equals(VERSION) ||
								s.substring(8).trim().equals(VERSION_LEGACY))) {
					versionLine = true;
				} else if (s.startsWith("oid sha256:")) { //$NON-NLS-1$
					id = LongObjectId.fromString(s.substring(11).trim());
				} else if (s.startsWith("size") && s.length() > 5) { //$NON-NLS-1$
					sz = Long.parseLong(s.substring(5).trim());
				}
			}
			if (versionLine && id != null && sz > -1) {
				return new LfsPointer(id, sz);
			}
		}
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return "LfsPointer: oid=" + oid.name() + ", size=" //$NON-NLS-1$ //$NON-NLS-2$
				+ size;
	}

	/**
	 * @since 4.11
	 */
	@Override
	public int compareTo(LfsPointer o) {
		int x = getOid().compareTo(o.getOid());
		if (x != 0) {
			return x;
		}

		return (int) (getSize() - o.getSize());
	}
}

