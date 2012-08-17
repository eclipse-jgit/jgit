/*
 * Copyright (C) 2012, Google Inc.
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

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jgit.transport.PublisherPackSlice.Allocator;
import org.eclipse.jgit.transport.PublisherPackSlice.Deallocator;

/**
 * Each server should only have one pool instance. This pool wraps multiple
 * PublisherPackSlices in a list. The list is controlled to track the total pool
 * size. If adding a new Slice would overflow the pool, the last Slice is
 * archived to disk and all readers still attached are converted to disk readers
 * from memory readers.
 *
 * As new updates happen, they get split into PublisherPacks. Each
 * PublisherPack is made up of 1 or more PublisherPackSlices. Each Slice resides
 * either in memory and accounted for in this pool, or on disk. Slices also
 * maintain reference counts for the number of client sessions that still need
 * to read them. When the counter reaches 0, the pack can be removed from the
 * pool to reclaim space.
 *
 * Each client has a queue of pack numbers to expect. On session destruction,
 * clients run through the pool and decrement all packs they would have used.
 */
public class PublisherMemoryPool implements Allocator {
	// TODO: optimize remove() to be O(1)
	private final ConcurrentLinkedQueue<PublisherPackSlice> loadedSlices;

	private final long capacity;

	private final AtomicLong size;

	/**
	 * @param capacity
	 *            in bytes
	 */
	public PublisherMemoryPool(long capacity) {
		this.capacity = capacity;
		loadedSlices = new ConcurrentLinkedQueue<PublisherPackSlice>();
		size = new AtomicLong();
	}

	/**
	 * Track this slice as part of the pool's memory space. If needed, make
	 * space by storing the oldest slices.
	 *
	 * @param slice
	 * @return a deallocator with a method to remove this slice
	 * @throws PublisherException
	 */
	public Deallocator allocate(final PublisherPackSlice slice) throws PublisherException {
		if (loadedSlices.contains(slice))
			throw new IllegalStateException("Slice " + slice
					+ " already allocated");
		size.addAndGet(slice.getSize());
		if (size.get() > capacity) {
			synchronized (this) {
				for (PublisherPackSlice s : loadedSlices) {
					if (size.get() <= capacity)
						break;
					try {
						s.store();
					} catch (IOException e) {
						throw new PublisherException(
								"Error deallocating pack slice " + slice, e);
					}
				}
			}
		}
		loadedSlices.add(slice);
		return new Deallocator() {
			public void deallocate() throws PublisherException {
				if (!loadedSlices.remove(slice))
					throw new PublisherException("Slice " + slice
							+ " already deallocated");
				size.addAndGet(-slice.getSize());
			}
		};
	}
}
