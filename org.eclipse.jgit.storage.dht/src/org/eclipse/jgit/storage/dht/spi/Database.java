/*
 * Copyright (C) 2011, Google Inc.
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

package org.eclipse.jgit.storage.dht.spi;

/**
 * A distributed database implementation.
 * <p>
 * A DHT provider must implement this interface to return table references for
 * each of the named tables. The database and the tables it returns are held as
 * singletons, and thus must be thread-safe. If the underlying implementation
 * needs to use individual "connections" for each operation, it is responsible
 * for setting up a connection pool, borrowing and returning resources within
 * each of the table APIs.
 * <p>
 * Most APIs on the tables are asynchronous and must perform their computation
 * in the background using a different thread than the caller. Implementations
 * that have only an underlying synchronous API should configure and use an
 * {@link java.util.concurrent.ExecutorService} to perform computation in the
 * background on a thread pool.
 * <p>
 * Tables returned by these methods should be singletons, as the higher level
 * DHT implementation usually invokes these methods each time it needs to use a
 * given table. The suggested implementation approach is:
 *
 * <pre>
 * class MyDatabase implements Database {
 * 	private final RepositoryIndexTable rep = new MyRepositoryIndex();
 *
 * 	private final RefTable ref = new MyRefTable();
 *
 * 	public RepositoryIndexTable repositoryIndex() {
 * 		return rep;
 * 	}
 *
 * 	public RefTable ref() {
 * 		return ref;
 * 	}
 * }
 * </pre>
 */
public interface Database {
	/** @return a handle to the table listing known repositories. */
	public RepositoryIndexTable repositoryIndex();

	/** @return a handle to the table storing repository metadata. */
	public RepositoryTable repository();

	/** @return a handle to the table listing references in a repository. */
	public RefTable ref();

	/** @return a handle to the table listing known objects. */
	public ObjectIndexTable objectIndex();

	/** @return a handle to the table listing pack data chunks. */
	public ChunkTable chunk();

	/**
	 * Create a new WriteBuffer for the current thread.
	 * <p>
	 * Unlike other methods on this interface, the returned buffer <b>must</b>
	 * be a new object on every invocation. Buffers do not need to be
	 * thread-safe.
	 *
	 * @return a new buffer to handle pending writes.
	 */
	public WriteBuffer newWriteBuffer();
}
