/*
 * Copyright (C) 2016, 2021 Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.Objects;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lfs.lib.LongObjectId;
import org.eclipse.jgit.util.IO;

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

	/**
	 * {@link #SIZE_THRESHOLD} is too low; with lfs extensions a LFS pointer can
	 * be larger. But 8kB should be more than enough.
	 */
	static final int FULL_SIZE_THRESHOLD = 8 * 1024;

	private final AnyLongObjectId oid;

	private final long size;

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
		}
	}

	/**
	 * Try to parse the data provided by an InputStream to the format defined by
	 * {@link #VERSION}. If the given stream supports mark and reset as
	 * indicated by {@link InputStream#markSupported()}, its input position will
	 * be reset if the stream content is not actually a LFS pointer (i.e., when
	 * {@code null} is returned). If the stream content is an invalid LFS
	 * pointer or the given stream does not support mark/reset, the input
	 * position may not be reset.
	 *
	 * @param in
	 *            the {@link java.io.InputStream} from where to read the data
	 * @return an {@link org.eclipse.jgit.lfs.LfsPointer} or {@code null} if the
	 *         stream was not parseable as LfsPointer
	 * @throws java.io.IOException
	 *             if an IO error occurred
	 */
	@Nullable
	public static LfsPointer parseLfsPointer(InputStream in)
			throws IOException {
		if (in.markSupported()) {
			return parse(in);
		}
		// Fallback; note that while parse() resets its input stream, that won't
		// reset "in".
		return parse(new BufferedInputStream(in));
	}

	@Nullable
	private static LfsPointer parse(InputStream in)
			throws IOException {
		if (!in.markSupported()) {
			// No translation; internal error
			throw new IllegalArgumentException(
					"LFS pointer parsing needs InputStream.markSupported() == true"); //$NON-NLS-1$
		}
		// Try reading only a short block first.
		in.mark(SIZE_THRESHOLD);
		byte[] preamble = new byte[SIZE_THRESHOLD];
		int length = IO.readFully(in, preamble, 0);
		if (length < preamble.length || in.read() < 0) {
			// We have the whole file. Try to parse a pointer from it.
			try (BufferedReader r = new BufferedReader(new InputStreamReader(
					new ByteArrayInputStream(preamble, 0, length), UTF_8))) {
				LfsPointer ptr = parse(r);
				if (ptr == null) {
					in.reset();
				}
				return ptr;
			}
		}
		// Longer than SIZE_THRESHOLD: expect "version" to be the first line.
		boolean hasVersion = checkVersion(preamble);
		in.reset();
		if (!hasVersion) {
			return null;
		}
		in.mark(FULL_SIZE_THRESHOLD);
		byte[] fullPointer = new byte[FULL_SIZE_THRESHOLD];
		length = IO.readFully(in, fullPointer, 0);
		if (length == fullPointer.length && in.read() >= 0) {
			in.reset();
			return null; // Too long.
		}
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				new ByteArrayInputStream(fullPointer, 0, length), UTF_8))) {
			LfsPointer ptr = parse(r);
			if (ptr == null) {
				in.reset();
			}
			return ptr;
		}
	}

	private static LfsPointer parse(BufferedReader r) throws IOException {
		boolean versionLine = false;
		LongObjectId id = null;
		long sz = -1;
		// This parsing is a bit too general if we go by the spec at
		// https://github.com/git-lfs/git-lfs/blob/master/docs/spec.md
		// Comment lines are not mentioned in the spec, the "version" line
		// MUST be the first, and keys are ordered alphabetically.
		for (String s = r.readLine(); s != null; s = r.readLine()) {
			if (s.startsWith("#") || s.length() == 0) { //$NON-NLS-1$
				continue;
			} else if (s.startsWith("version")) { //$NON-NLS-1$
				if (versionLine || !checkVersionLine(s)) {
					return null; // Not a LFS pointer
				}
				versionLine = true;
			} else {
				try {
					if (s.startsWith("oid sha256:")) { //$NON-NLS-1$
						if (id != null) {
							return null; // Not a LFS pointer
						}
						id = LongObjectId.fromString(s.substring(11).trim());
					} else if (s.startsWith("size")) { //$NON-NLS-1$
						if (sz > 0 || s.length() < 5 || s.charAt(4) != ' ') {
							return null; // Not a LFS pointer
						}
						sz = Long.parseLong(s.substring(5).trim());
					}
				} catch (RuntimeException e) {
					// We could not parse the line. If we have a version
					// already, this is a corrupt LFS pointer. Otherwise it
					// is just not an LFS pointer.
					if (versionLine) {
						throw e;
					}
					return null;
				}
			}
			if (versionLine && id != null && sz > -1) {
				return new LfsPointer(id, sz);
			}
		}
		return null;
	}

	private static boolean checkVersion(byte[] data) {
		// According to the spec at
		// https://github.com/git-lfs/git-lfs/blob/master/docs/spec.md
		// it MUST always be the first line.
		try (BufferedReader r = new BufferedReader(
				new InputStreamReader(new ByteArrayInputStream(data), UTF_8))) {
			String s = r.readLine();
			if (s != null && s.startsWith("version")) { //$NON-NLS-1$
				return checkVersionLine(s);
			}
		} catch (IOException e) {
			// Doesn't occur, we're reading from a byte array!
		}
		return false;
	}

	private static boolean checkVersionLine(String s) {
		if (s.length() < 8 || s.charAt(7) != ' ') {
			return false; // Not a valid LFS pointer version line
		}
		String rest = s.substring(8).trim();
		return VERSION.equals(rest) || VERSION_LEGACY.equals(rest);
	}

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

		return Long.compare(getSize(), o.getSize());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getOid()) * 31 + Long.hashCode(getSize());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof LfsPointer)) {
			return false;
		}
		LfsPointer other = (LfsPointer) obj;
		return Objects.equals(getOid(), other.getOid())
				&& getSize() == other.getSize();
	}
}
