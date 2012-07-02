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
import java.util.Iterator;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jgit.util.ConcurrentLinkedList;

/**
 * Each server should only have one buffer instance. This buffer wraps multiple
 * PublisherPackSlices in a linked list. The list is controlled to track the
 * total buffer size. If adding a new Slice would overflow the buffer, the last
 * Slice is archived to disk and all readers still attached are converted to
 * disk readers from memory readers.
 *
 *  As new updates happen, they get split into PublisherPacks. Each
 * PublisherPack is made up of 1 or more PublisherPackSlices. Each Slice resides
 * either in memory and accounted for in this buffer, or on disk. Slices also
 * maintain reference counts for the number of client sessions that still need
 * to read them. When the counter reaches 0, the pack can be removed from the
 * buffer to reclaim space.
 *
 *  Garbage collection is done with a helper thread that iterates the list and
 * removes all Slices no longer used ({@link PublisherPackSlice#canRecycle()}).
 *
 *  Each client has a queue of pack numbers to expect. On session destruction,
 * clients run through the buffer and decrement all packs they would have used.
 */
public class PublisherBuffer {
	private static final int DEFAULT_GC_INTERVAL = 2000; // 2s

	private final ConcurrentLinkedList<PublisherPackSlice> loadedSlices;

	private final ScheduledExecutorService gcService;

	private int gcInterval;

	private final long capacity;

	private final AtomicLong size;

	private boolean gcStarted;

	/**
	 * @param capacity
	 *            in bytes
	 */
	public PublisherBuffer(long capacity) {
		this.capacity = capacity;
		loadedSlices = new ConcurrentLinkedList<PublisherPackSlice>();
		gcInterval = DEFAULT_GC_INTERVAL;
		gcService = new ScheduledThreadPoolExecutor(1);
		size = new AtomicLong();
	}

	/**
	 * @param interval
	 *            the interval in ms to run the garbage collector
	 * @throws IllegalStateException
	 *             if the garbage collector has already been called with
	 *             {@link #startGC()}.
	 */
	public void setGcInterval(int interval) throws IllegalStateException {
		if (gcStarted)
			throw new IllegalStateException();
		gcInterval = interval;
	}

	/**
	 * Track this slice as part of the buffer's memory space. If needed, make
	 * space by storing the oldest slices.
	 *
	 * @param slice
	 */
	public void allocate(PublisherPackSlice slice) {
		size.addAndGet(slice.getSize());
		if (size.get() > capacity) {
			PublisherPackSlice s;
			for (Iterator<PublisherPackSlice> it = loadedSlices
					.getWriteIterator(); size.get() > capacity
					&& it.hasNext();) {
				s = it.next();
				if (s.isClosed())
					continue;
				try {
					s.store();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		loadedSlices.put(slice);
	}

	/**
	 * Release this slice's space from memory.
	 *
	 * @param slice
	 */
	public void deallocate(PublisherPackSlice slice) {
		size.addAndGet(-slice.getSize());
	}

	/** Garbage collect over all slices, including those in storage. */
	public void startGC() {
		if (gcStarted)
			throw new IllegalStateException();
		gcStarted = true;
		gcService.scheduleAtFixedRate(new Runnable() {
			public void run() {
				Iterator<PublisherPackSlice> it = loadedSlices
						.getWriteIterator();
				for (PublisherPackSlice slice = it.next(); it.hasNext();) {
					if (slice.isClosed())
						it.remove();
				}
			}
		}, 0, gcInterval, TimeUnit.MILLISECONDS);
	}
}
