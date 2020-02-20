/*
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.util.zip.Inflater;

/**
 * Creates zlib based inflaters as necessary for object decompression.
 */
public class InflaterCache {
	private static final int SZ = 4;

	private static final Inflater[] inflaterCache;

	private static int openInflaterCount;

	static {
		inflaterCache = new Inflater[SZ];
	}

	/**
	 * Obtain an Inflater for decompression.
	 * <p>
	 * Inflaters obtained through this cache should be returned (if possible) by
	 * {@link #release(Inflater)} to avoid garbage collection and reallocation.
	 *
	 * @return an available inflater. Never null.
	 */
	public static Inflater get() {
		final Inflater r = getImpl();
		return r != null ? r : new Inflater(false);
	}

	private static synchronized Inflater getImpl() {
		if (openInflaterCount > 0) {
			final Inflater r = inflaterCache[--openInflaterCount];
			inflaterCache[openInflaterCount] = null;
			return r;
		}
		return null;
	}

	/**
	 * Release an inflater previously obtained from this cache.
	 *
	 * @param i
	 *            the inflater to return. May be null, in which case this method
	 *            does nothing.
	 */
	public static void release(Inflater i) {
		if (i != null) {
			i.reset();
			if (releaseImpl(i))
				i.end();
		}
	}

	private static synchronized boolean releaseImpl(Inflater i) {
		if (openInflaterCount < SZ) {
			inflaterCache[openInflaterCount++] = i;
			return false;
		}
		return true;
	}

	private InflaterCache() {
		throw new UnsupportedOperationException();
	}
}
