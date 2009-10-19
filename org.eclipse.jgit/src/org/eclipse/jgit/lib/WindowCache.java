/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.lib;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caches slices of a {@link PackFile} in memory for faster read access.
 * <p>
 * The WindowCache serves as a Java based "buffer cache", loading segments of a
 * PackFile into the JVM heap prior to use. As JGit often wants to do reads of
 * only tiny slices of a file, the WindowCache tries to smooth out these tiny
 * reads into larger block-sized IO operations.
 */
public class WindowCache extends OffsetCache<ByteWindow, WindowCache.WindowRef> {
	private static final int bits(int newSize) {
		if (newSize < 4096)
			throw new IllegalArgumentException("Invalid window size");
		if (Integer.bitCount(newSize) != 1)
			throw new IllegalArgumentException("Window size must be power of 2");
		return Integer.numberOfTrailingZeros(newSize);
	}

	private static volatile WindowCache cache;

	static {
		reconfigure(new WindowCacheConfig());
	}

	/**
	 * Modify the configuration of the window cache.
	 * <p>
	 * The new configuration is applied immediately. If the new limits are
	 * smaller than what what is currently cached, older entries will be purged
	 * as soon as possible to allow the cache to meet the new limit.
	 *
	 * @param packedGitLimit
	 *            maximum number of bytes to hold within this instance.
	 * @param packedGitWindowSize
	 *            number of bytes per window within the cache.
	 * @param packedGitMMAP
	 *            true to enable use of mmap when creating windows.
	 * @param deltaBaseCacheLimit
	 *            number of bytes to hold in the delta base cache.
	 * @deprecated Use {@link WindowCacheConfig} instead.
	 */
	public static void reconfigure(final int packedGitLimit,
			final int packedGitWindowSize, final boolean packedGitMMAP,
			final int deltaBaseCacheLimit) {
		final WindowCacheConfig c = new WindowCacheConfig();
		c.setPackedGitLimit(packedGitLimit);
		c.setPackedGitWindowSize(packedGitWindowSize);
		c.setPackedGitMMAP(packedGitMMAP);
		c.setDeltaBaseCacheLimit(deltaBaseCacheLimit);
		reconfigure(c);
	}

	/**
	 * Modify the configuration of the window cache.
	 * <p>
	 * The new configuration is applied immediately. If the new limits are
	 * smaller than what what is currently cached, older entries will be purged
	 * as soon as possible to allow the cache to meet the new limit.
	 *
	 * @param cfg
	 *            the new window cache configuration.
	 * @throws IllegalArgumentException
	 *             the cache configuration contains one or more invalid
	 *             settings, usually too low of a limit.
	 */
	public static void reconfigure(final WindowCacheConfig cfg) {
		final WindowCache nc = new WindowCache(cfg);
		final WindowCache oc = cache;
		if (oc != null)
			oc.removeAll();
		cache = nc;
		UnpackedObjectCache.reconfigure(cfg);
	}

	static WindowCache getInstance() {
		return cache;
	}

	static final ByteWindow get(final PackFile pack, final long offset)
			throws IOException {
		final WindowCache c = cache;
		final ByteWindow r = c.getOrLoad(pack, c.toStart(offset));
		if (c != cache) {
			// The cache was reconfigured while we were using the old one
			// to load this window. The window is still valid, but our
			// cache may think its still live. Ensure the window is removed
			// from the old cache so resources can be released.
			//
			c.removeAll();
		}
		return r;
	}

	static final void purge(final PackFile pack) {
		cache.removeAll(pack);
	}

	private final int maxFiles;

	private final long maxBytes;

	private final boolean mmap;

	private final int windowSizeShift;

	private final int windowSize;

	private final AtomicInteger openFiles;

	private final AtomicLong openBytes;

	private WindowCache(final WindowCacheConfig cfg) {
		super(tableSize(cfg), lockCount(cfg));
		maxFiles = cfg.getPackedGitOpenFiles();
		maxBytes = cfg.getPackedGitLimit();
		mmap = cfg.isPackedGitMMAP();
		windowSizeShift = bits(cfg.getPackedGitWindowSize());
		windowSize = 1 << windowSizeShift;

		openFiles = new AtomicInteger();
		openBytes = new AtomicLong();

		if (maxFiles < 1)
			throw new IllegalArgumentException("Open files must be >= 1");
		if (maxBytes < windowSize)
			throw new IllegalArgumentException("Window size must be < limit");
	}

	int getOpenFiles() {
		return openFiles.get();
	}

	long getOpenBytes() {
		return openBytes.get();
	}

	@Override
	protected int hash(final int packHash, final long off) {
		return packHash + (int) (off >>> windowSizeShift);
	}

	@Override
	protected ByteWindow load(final PackFile pack, final long offset)
			throws IOException {
		if (pack.beginWindowCache())
			openFiles.incrementAndGet();
		try {
			if (mmap)
				return pack.mmap(offset, windowSize);
			return pack.read(offset, windowSize);
		} catch (IOException e) {
			close(pack);
			throw e;
		} catch (RuntimeException e) {
			close(pack);
			throw e;
		} catch (Error e) {
			close(pack);
			throw e;
		}
	}

	@Override
	protected WindowRef createRef(final PackFile p, final long o,
			final ByteWindow v) {
		final WindowRef ref = new WindowRef(p, o, v, queue);
		openBytes.addAndGet(ref.size);
		return ref;
	}

	@Override
	protected void clear(final WindowRef ref) {
		openBytes.addAndGet(-ref.size);
		close(ref.pack);
	}

	private void close(final PackFile pack) {
		if (pack.endWindowCache())
			openFiles.decrementAndGet();
	}

	@Override
	protected boolean isFull() {
		return maxFiles < openFiles.get() || maxBytes < openBytes.get();
	}

	private long toStart(final long offset) {
		return (offset >>> windowSizeShift) << windowSizeShift;
	}

	private static int tableSize(final WindowCacheConfig cfg) {
		final int wsz = cfg.getPackedGitWindowSize();
		final long limit = cfg.getPackedGitLimit();
		if (wsz <= 0)
			throw new IllegalArgumentException("Invalid window size");
		if (limit < wsz)
			throw new IllegalArgumentException("Window size must be < limit");
		return (int) Math.min(5 * (limit / wsz) / 2, 2000000000);
	}

	private static int lockCount(final WindowCacheConfig cfg) {
		return Math.max(cfg.getPackedGitOpenFiles(), 32);
	}

	static class WindowRef extends OffsetCache.Ref<ByteWindow> {
		final int size;

		WindowRef(final PackFile pack, final long position, final ByteWindow v,
				final ReferenceQueue<ByteWindow> queue) {
			super(pack, position, v, queue);
			size = v.size();
		}
	}
}
