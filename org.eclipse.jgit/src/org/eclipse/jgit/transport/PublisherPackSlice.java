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
import java.io.OutputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A slice of a PublisherPack stored either in memory or on
 * implementation-specific storage (disk, DFS, etc).
 */
public abstract class PublisherPackSlice {
	/** Policy controlling automatic loading of data into memory. */
	public interface LoadPolicy {
		/**
		 * Called once before every client starts reading the data stream.
		 *
		 * @param slice
		 * @return true if this slice should be loaded into memory
		 */
		public boolean shouldLoad(PublisherPackSlice slice);
	}

	/** Deallocator callback for a slice's memory. */
	public interface Deallocator {
		/** Called after deallocating a slice's memory. */
		public void deallocate();
	}

	/** Used to allocate memory pool space for a slice. */
	public interface Allocator {
		/**
		 * Called once upon successful load of this slice into memory.
		 *
		 * @param slice
		 * @return deallocator for this slice
		 */
		public Deallocator allocate(PublisherPackSlice slice);
	}

	/** Size of each write in bytes between relocking. */
	private static final int WRITE_SIZE = 4096;

	private final int size;

	/** Memory storage for this Slice. */
	private byte memoryBuffer[];

	private volatile boolean closed;

	private final LoadPolicy loadPolicy;

	private final Allocator memoryAllocator;

	private volatile Deallocator deallocator;

	final ReadWriteLock rwLock = new ReentrantReadWriteLock();

	/**
	 * @param policy
	 * @param callback
	 * @param buf
	 */
	public PublisherPackSlice(
			LoadPolicy policy, Allocator callback, byte[] buf) {
		loadPolicy = policy;
		memoryAllocator = callback;
		size = buf.length;
		memoryBuffer = buf;
	}

	/**
	 * Set an initial deallocator for when the slice is already loaded into
	 * memory.
	 *
	 * @param d
	 */
	void setDeallocator(Deallocator d) {
		deallocator = d;
	}

	/** @return the size of this slice in bytes */
	public long getSize() {
		return size;
	}

	/**
	 * Transfer into physical memory.
	 *
	 * @throws IOException
	 */
	public void load() throws IOException {
		Lock writeLock = rwLock.writeLock();
		writeLock.lock();
		try {
			if (isLoaded())
				return;
			memoryBuffer = doLoad();
		} finally {
			writeLock.unlock();
		}
		// Loaded may call store()
		deallocator = memoryAllocator.allocate(this);
	}

	/**
	 * Free physical memory and transfer to storage.
	 *
	 * @return true if this block was released, false if it wasn't loaded
	 * @throws IOException
	 */
	protected boolean store() throws IOException {
		Lock writeLock = rwLock.writeLock();
		writeLock.lock();
		try {
			if (!isLoaded())
				return false;
			doStore(memoryBuffer);
			memoryBuffer = null;
			if (isLoaded()) {
				deallocator.deallocate();
				deallocator = null;
			}
			return true;
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Load from storage into memory.
	 *
	 * @return the loaded buffer
	 * @throws IOException
	 */
	abstract protected byte[] doLoad() throws IOException;

	/**
	 * Store buffer.
	 *
	 * @param buffer
	 * @throws IOException
	 */
	abstract protected void doStore(byte[] buffer) throws IOException;

	/**
	 * Read from stored location into the OutputStream.
	 *
	 * @param out
	 * @param position
	 * @param length
	 * @throws IOException
	 */
	abstract protected void doStoredWrite(
			OutputStream out, int position, int length) throws IOException;

	/**
	 * Copy this Slice into the provided OutputStream. This Slice may be moved
	 * to and from storage during copies.
	 *
	 * @param out
	 * @throws IOException
	 */
	void writeToStream(OutputStream out) throws IOException {
		if (loadPolicy.shouldLoad(this))
			load();
		int pos = 0;
		while (pos < size) {
			Lock readLock = rwLock.readLock();
			readLock.lock();
			try {
				if (closed)
					throw new IOException("Slice already closed");
				int writeLen = Math.min(size - pos, WRITE_SIZE);
				if (isLoaded())
					out.write(memoryBuffer, pos, writeLen);
				else
					doStoredWrite(out, pos, writeLen);
				pos += writeLen;
			} finally {
				readLock.unlock();
			}
		}
	}

	/**
	 * Delete all resources this slice used.
	 */
	public void close() {
		Lock writeLock = rwLock.writeLock();
		writeLock.lock();
		try {
			memoryBuffer = null;
			closed = true;
			if (isLoaded()) {
				deallocator.deallocate();
				deallocator = null;
			}
		} finally {
			writeLock.unlock();
		}
	}

	/** @return true if this slice is closed */
	public boolean isClosed() {
		return closed;
	}

	/** @return true if this slice is in memory */
	public boolean isLoaded() {
		return (deallocator != null);
	}
}
