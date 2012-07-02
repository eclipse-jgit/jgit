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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A slice of a PublisherPack stored either in memory or on disk, abstracted by
 * an InputStream.
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

	/** Callbacks for load and store events. */
	public interface LoadCallback {
		/**
		 * Called once upon successful load of this slice into memory.
		 *
		 * @param slice
		 */
		public void loaded(PublisherPackSlice slice);

		/**
		 * Called once upon successful store of this slice.
		 *
		 * @param slice
		 */
		public void stored(PublisherPackSlice slice);
	}

	/** Size of each write in bytes between relocking. */
	private static final int WRITE_SIZE = 4096;

	private final int size;

	/** Memory storage for this Slice. */
	private byte memoryBuffer[];

	private volatile boolean inMemory = true;

	private volatile boolean closed;

	private final AtomicInteger referenceCount = new AtomicInteger();

	private final LoadPolicy loadPolicy;

	private final LoadCallback loadCallback;

	final ReadWriteLock rwLock = new ReentrantReadWriteLock();

	/**
	 * @param policy
	 * @param callback
	 * @param consumers
	 * @param buf
	 */
	public PublisherPackSlice(LoadPolicy policy, LoadCallback callback,
			int consumers, byte[] buf) {
		loadPolicy = policy;
		loadCallback = callback;
		referenceCount.set(consumers);
		size = buf.length;
		memoryBuffer = buf;
	}

	/** @return the size of this slice in bytes */
	public long getSize() {
		return size;
	}

	/**
	 * Transfer into physical memory, and update any outstanding InputStreams to
	 * read from the loaded buffer.
	 *
	 * @throws IOException
	 */
	public void load() throws IOException {
		Lock writeLock = rwLock.writeLock();
		writeLock.lock();
		try {
			if (inMemory)
				return;
			memoryBuffer = doLoad();
			inMemory = true;
		} finally {
			writeLock.unlock();
		}
		// Loaded may call store()
		loadCallback.loaded(this);
	}

	/**
	 * Same as {@link #store()}, except only call
	 * {@link LoadCallback#stored(PublisherPackSlice)} if allocated is true.
	 *
	 * @param allocated
	 * @return true if this block was released, false if it wasn't loaded
	 * @throws IOException
	 */
	protected boolean store(boolean allocated) throws IOException {
		Lock writeLock = rwLock.writeLock();
		writeLock.lock();
		try {
			if (!inMemory)
				return false;
			doStore(memoryBuffer);
			inMemory = false;
			memoryBuffer = null;
			if (allocated)
				loadCallback.stored(this);
			return true;
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * Free physical memory and transfer to storage, and update any outstanding
	 * InputStreams to load from the storage location.
	 *
	 * @return true if this block was released, false if it wasn't loaded
	 * @throws IOException
	 */
	public boolean store() throws IOException {
		return store(true);
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
	public void writeToStream(OutputStream out) throws IOException {
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
				if (inMemory)
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
	 * Increase reference count for this slice by 1.
	 *
	 * @return true if the count was incremented before it reached 0
	 */
	public boolean incrementOpen() {
		return referenceCount.getAndIncrement() > 0;
	}

	/**
	 * Release one reference to this Slice. This should be called by every
	 * client after they no longer need this Slice.
	 */
	public void release() {
		if (referenceCount.decrementAndGet() == 0) {
			if (inMemory)
				loadCallback.stored(this);
			close();
		}
	}

	/** @return true if this Slice's memory can be recycled */
	public boolean canRecycle() {
		return referenceCount.get() == 0 && closed;
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
		return inMemory;
	}
}
