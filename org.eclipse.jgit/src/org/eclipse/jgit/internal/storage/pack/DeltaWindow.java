/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.internal.storage.pack;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.util.TemporaryBuffer;

final class DeltaWindow {
	private static final int NEXT_RES = 0;

	private static final int NEXT_SRC = 1;

	private final PackConfig config;
	private final DeltaCache deltaCache;
	private final ObjectReader reader;
	private final ProgressMonitor monitor;

	private final DeltaWindowEntry[] window;

	/** Maximum number of bytes to admit to the window at once. */
	private final long maxMemory;

	/** Maximum depth we should create for any delta chain. */
	private final int maxDepth;

	private final ObjectToPack[] toSearch;

	private int cur;

	private int end;

	/** Amount of memory we have loaded right now. */
	private long loaded;

	// The object we are currently considering needs a lot of state:

	/** Position of {@link #res} within {@link #window} array. */
	private int resSlot;

	/**
	 * Maximum delta chain depth the current object can have.
	 * <p>
	 * This can be smaller than {@link #maxDepth}.
	 */
	private int resMaxDepth;

	/** Window entry of the object we are currently considering. */
	private DeltaWindowEntry res;

	/** If we have a delta for {@link #res}, this is the shortest found yet. */
	private TemporaryBuffer.Heap bestDelta;

	/** If we have {@link #bestDelta}, the window position it was created by. */
	private int bestSlot;

	/** Used to compress cached deltas. */
	private Deflater deflater;

	DeltaWindow(PackConfig pc, DeltaCache dc, ObjectReader or,
			ProgressMonitor pm,
			ObjectToPack[] in, int beginIndex, int endIndex) {
		config = pc;
		deltaCache = dc;
		reader = or;
		monitor = pm;
		toSearch = in;
		cur = beginIndex;
		end = endIndex;

		// C Git increases the window size supplied by the user by 1.
		// We don't know why it does this, but if the user asks for
		// window=10, it actually processes with window=11. Because
		// the window size has the largest direct impact on the final
		// pack file size, we match this odd behavior here to give us
		// a better chance of producing a similar sized pack as C Git.
		//
		// We would prefer to directly honor the user's request since
		// PackWriter has a minimum of 2 for the window size, but then
		// users might complain that JGit is creating a bigger pack file.
		//
		window = new DeltaWindowEntry[config.getDeltaSearchWindowSize() + 1];
		for (int i = 0; i < window.length; i++)
			window[i] = new DeltaWindowEntry();

		maxMemory = Math.max(0, config.getDeltaSearchMemoryLimit());
		maxDepth = config.getMaxDeltaDepth();
	}

	synchronized int remaining() {
		return end - cur;
	}

	synchronized DeltaTask.Slice stealWork() {
		int e = end;
		int n = (e - cur) >>> 1;
		if (0 == n)
			return null;

		int t = e - n;
		int h = toSearch[t].getPathHash();
		while (cur < t) {
			if (h == toSearch[t - 1].getPathHash())
				t--;
			else
				break;
		}
		end = t;
		return new DeltaTask.Slice(t, e);
	}

	void search() throws IOException {
		try {
			for (;;) {
				ObjectToPack next;
				synchronized (this) {
					if (end <= cur)
						break;
					next = toSearch[cur++];
				}
				res = window[resSlot];
				if (maxMemory != 0) {
					clear(res);
					int tail = next(resSlot);
					final long need = estimateSize(next);
					while (maxMemory < loaded + need && tail != resSlot) {
						clear(window[tail]);
						tail = next(tail);
					}
				}
				res.set(next);

				if (res.object.isEdge() || res.object.doNotAttemptDelta()) {
					// We don't actually want to make a delta for
					// them, just need to push them into the window
					// so they can be read by other objects.
					//
					keepInWindow();
				} else {
					// Search for a delta for the current window slot.
					//
					monitor.update(1);
					searchInWindow();
				}
			}
		} finally {
			if (deflater != null)
				deflater.end();
		}
	}

	private static long estimateSize(ObjectToPack ent) {
		return DeltaIndex.estimateIndexSize(ent.getWeight());
	}

	private static long estimateIndexSize(DeltaWindowEntry ent) {
		if (ent.buffer == null)
			return estimateSize(ent.object);

		int len = ent.buffer.length;
		return DeltaIndex.estimateIndexSize(len) - len;
	}

	private void clear(DeltaWindowEntry ent) {
		if (ent.index != null)
			loaded -= ent.index.getIndexSize();
		else if (ent.buffer != null)
			loaded -= ent.buffer.length;
		ent.set(null);
	}

	private void searchInWindow() throws IOException {
		// TODO(spearce) If the object is used as a base for other
		// objects in this pack we should limit the depth we create
		// for ourselves to be the remainder of our longest dependent
		// chain and the configured maximum depth. This can happen
		// when the dependents are being reused out a pack, but we
		// cannot be because we are near the edge of a thin pack.
		//
		resMaxDepth = maxDepth;

		// Loop through the window backwards, considering every entry.
		// This lets us look at the bigger objects that came before.
		//
		for (int srcSlot = prior(resSlot); srcSlot != resSlot; srcSlot = prior(srcSlot)) {
			DeltaWindowEntry src = window[srcSlot];
			if (src.empty())
				break;
			if (delta(src, srcSlot) == NEXT_RES) {
				bestDelta = null;
				return;
			}
		}

		// We couldn't find a suitable delta for this object, but it may
		// still be able to act as a base for another one.
		//
		if (bestDelta == null) {
			keepInWindow();
			return;
		}

		// Select this best matching delta as the base for the object.
		//
		ObjectToPack srcObj = window[bestSlot].object;
		ObjectToPack resObj = res.object;
		if (srcObj.isEdge()) {
			// The source (the delta base) is an edge object outside of the
			// pack. Its part of the common base set that the peer already
			// has on hand, so we don't want to send it. We have to store
			// an ObjectId and *NOT* an ObjectToPack for the base to ensure
			// the base isn't included in the outgoing pack file.
			//
			resObj.setDeltaBase(srcObj.copy());
		} else {
			// The base is part of the pack we are sending, so it should be
			// a direct pointer to the base.
			//
			resObj.setDeltaBase(srcObj);
		}
		resObj.setDeltaDepth(srcObj.getDeltaDepth() + 1);
		resObj.clearReuseAsIs();
		cacheDelta(srcObj, resObj);

		// Discard the cached best result, otherwise it leaks.
		//
		bestDelta = null;

		// If this should be the end of a chain, don't keep
		// it in the window. Just move on to the next object.
		//
		if (resObj.getDeltaDepth() == maxDepth)
			return;

		shuffleBaseUpInPriority();
		keepInWindow();
	}

	private int delta(final DeltaWindowEntry src, final int srcSlot)
			throws IOException {
		// Objects must use only the same type as their delta base.
		// If we are looking at something where that isn't true we
		// have exhausted everything of the correct type and should
		// move on to the next thing to examine.
		//
		if (src.type() != res.type()) {
			keepInWindow();
			return NEXT_RES;
		}

		// Only consider a source with a short enough delta chain.
		if (src.depth() > resMaxDepth)
			return NEXT_SRC;

		// Estimate a reasonable upper limit on delta size.
		int msz = deltaSizeLimit(res, resMaxDepth, src);
		if (msz <= 8)
			return NEXT_SRC;

		// If we have to insert a lot to make this work, find another.
		if (res.size() - src.size() > msz)
			return NEXT_SRC;

		// If the sizes are radically different, this is a bad pairing.
		if (res.size() < src.size() / 16)
			return NEXT_SRC;

		DeltaIndex srcIndex;
		try {
			srcIndex = index(src);
		} catch (LargeObjectException tooBig) {
			// If the source is too big to work on, skip it.
			dropFromWindow(srcSlot);
			return NEXT_SRC;
		} catch (IOException notAvailable) {
			if (src.object.isEdge()) {
				// This is an edge that is suddenly not available.
				dropFromWindow(srcSlot);
				return NEXT_SRC;
			} else {
				throw notAvailable;
			}
		}

		byte[] resBuf;
		try {
			resBuf = buffer(res);
		} catch (LargeObjectException tooBig) {
			// If its too big, move on to another item.
			return NEXT_RES;
		}

		// If we already have a delta for the current object, abort
		// encoding early if this new pairing produces a larger delta.
		if (bestDelta != null && bestDelta.length() < msz)
			msz = (int) bestDelta.length();

		TemporaryBuffer.Heap delta = new TemporaryBuffer.Heap(msz);
		try {
			if (!srcIndex.encode(delta, resBuf, msz))
				return NEXT_SRC;
		} catch (IOException deltaTooBig) {
			// This only happens when the heap overflows our limit.
			return NEXT_SRC;
		}

		if (isBetterDelta(src, delta)) {
			bestDelta = delta;
			bestSlot = srcSlot;
		}

		return NEXT_SRC;
	}

	private void cacheDelta(ObjectToPack srcObj, ObjectToPack resObj) {
		if (Integer.MAX_VALUE < bestDelta.length())
			return;

		int rawsz = (int) bestDelta.length();
		if (deltaCache.canCache(rawsz, srcObj, resObj)) {
			try {
				byte[] zbuf = new byte[deflateBound(rawsz)];

				ZipStream zs = new ZipStream(deflater(), zbuf);
				bestDelta.writeTo(zs, null);
				bestDelta = null;
				int len = zs.finish();

				resObj.setCachedDelta(deltaCache.cache(zbuf, len, rawsz));
				resObj.setCachedSize(rawsz);
			} catch (IOException err) {
				deltaCache.credit(rawsz);
			} catch (OutOfMemoryError err) {
				deltaCache.credit(rawsz);
			}
		}
	}

	private static int deflateBound(int insz) {
		return insz + ((insz + 7) >> 3) + ((insz + 63) >> 6) + 11;
	}

	private void shuffleBaseUpInPriority() {
		// Shuffle the entire window so that the best match we just used
		// is at our current index, and our current object is at the index
		// before it. Slide any entries in between to make space.
		//
		window[resSlot] = window[bestSlot];

		DeltaWindowEntry next = res;
		int slot = prior(resSlot);
		for (; slot != bestSlot; slot = prior(slot)) {
			DeltaWindowEntry e = window[slot];
			window[slot] = next;
			next = e;
		}
		window[slot] = next;
	}

	private void keepInWindow() {
		resSlot = next(resSlot);
	}

	private int next(int slot) {
		if (++slot == window.length)
			return 0;
		return slot;
	}

	private int prior(int slot) {
		if (slot == 0)
			return window.length - 1;
		return slot - 1;
	}

	private void dropFromWindow(@SuppressWarnings("unused") int srcSlot) {
		// We should drop the current source entry from the window,
		// it is somehow invalid for us to work with.
	}

	private boolean isBetterDelta(DeltaWindowEntry src,
			TemporaryBuffer.Heap resDelta) {
		if (bestDelta == null)
			return true;

		// If both delta sequences are the same length, use the one
		// that has a shorter delta chain since it would be faster
		// to access during reads.
		//
		if (resDelta.length() == bestDelta.length())
			return src.depth() < window[bestSlot].depth();

		return resDelta.length() < bestDelta.length();
	}

	private static int deltaSizeLimit(DeltaWindowEntry res, int maxDepth,
			DeltaWindowEntry src) {
		// Ideally the delta is at least 50% of the original size,
		// but we also want to account for delta header overhead in
		// the pack file (to point to the delta base) so subtract off
		// some of those header bytes from the limit.
		//
		final int limit = res.size() / 2 - 20;

		// Distribute the delta limit over the entire chain length.
		// This is weighted such that deeper items in the chain must
		// be even smaller than if they were earlier in the chain, as
		// they cost significantly more to unpack due to the increased
		// number of recursive unpack calls.
		//
		final int remainingDepth = maxDepth - src.depth();
		return (limit * remainingDepth) / maxDepth;
	}

	private DeltaIndex index(DeltaWindowEntry ent)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException, LargeObjectException {
		DeltaIndex idx = ent.index;
		if (idx == null) {
			checkLoadable(ent, estimateIndexSize(ent));

			try {
				idx = new DeltaIndex(buffer(ent));
			} catch (OutOfMemoryError noMemory) {
				LargeObjectException.OutOfMemory e;
				e = new LargeObjectException.OutOfMemory(noMemory);
				e.setObjectId(ent.object);
				throw e;
			}
			if (maxMemory != 0)
				loaded += idx.getIndexSize() - idx.getSourceSize();
			ent.index = idx;
		}
		return idx;
	}

	private byte[] buffer(DeltaWindowEntry ent) throws MissingObjectException,
			IncorrectObjectTypeException, IOException, LargeObjectException {
		byte[] buf = ent.buffer;
		if (buf == null) {
			checkLoadable(ent, ent.size());

			buf = PackWriter.buffer(config, reader, ent.object);
			if (maxMemory != 0)
				loaded += buf.length;
			ent.buffer = buf;
		}
		return buf;
	}

	private void checkLoadable(DeltaWindowEntry ent, long need) {
		if (maxMemory == 0)
			return;

		int tail = next(resSlot);
		while (maxMemory < loaded + need) {
			DeltaWindowEntry cur = window[tail];
			clear(cur);
			if (cur == ent)
				throw new LargeObjectException.ExceedsLimit(
						maxMemory, loaded + need);
			tail = next(tail);
		}
	}

	private Deflater deflater() {
		if (deflater == null)
			deflater = new Deflater(config.getCompressionLevel());
		else
			deflater.reset();
		return deflater;
	}

	static final class ZipStream extends OutputStream {
		private final Deflater deflater;

		private final byte[] zbuf;

		private int outPtr;

		ZipStream(Deflater deflater, byte[] zbuf) {
			this.deflater = deflater;
			this.zbuf = zbuf;
		}

		int finish() throws IOException {
			deflater.finish();
			for (;;) {
				if (outPtr == zbuf.length)
					throw new EOFException();

				int n = deflater.deflate(zbuf, outPtr, zbuf.length - outPtr);
				if (n == 0) {
					if (deflater.finished())
						return outPtr;
					throw new IOException();
				}
				outPtr += n;
			}
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			deflater.setInput(b, off, len);
			for (;;) {
				if (outPtr == zbuf.length)
					throw new EOFException();

				int n = deflater.deflate(zbuf, outPtr, zbuf.length - outPtr);
				if (n == 0) {
					if (deflater.needsInput())
						break;
					throw new IOException();
				}
				outPtr += n;
			}
		}

		@Override
		public void write(int b) throws IOException {
			throw new UnsupportedOperationException();
		}
	}
}
