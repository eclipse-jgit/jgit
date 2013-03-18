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

package org.eclipse.jgit.internal.storage.dfs;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_CORE_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_DFS_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_BLOCK_LIMIT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_BLOCK_SIZE;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_READ_AHEAD_LIMIT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_READ_AHEAD_THREADS;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.lib.Config;

/** Configuration parameters for {@link DfsBlockCache}. */
public class DfsBlockCacheConfig {
	/** 1024 (number of bytes in one kibibyte/kilobyte) */
	public static final int KB = 1024;

	/** 1024 {@link #KB} (number of bytes in one mebibyte/megabyte) */
	public static final int MB = 1024 * KB;

	private long blockLimit;

	private int blockSize;

	private int readAheadLimit;

	private ThreadPoolExecutor readAheadService;

	/** Create a default configuration. */
	public DfsBlockCacheConfig() {
		setBlockLimit(32 * MB);
		setBlockSize(64 * KB);
	}

	/**
	 * @return maximum number bytes of heap memory to dedicate to caching pack
	 *         file data. <b>Default is 32 MB.</b>
	 */
	public long getBlockLimit() {
		return blockLimit;
	}

	/**
	 * @param newLimit
	 *            maximum number bytes of heap memory to dedicate to caching
	 *            pack file data.
	 * @return {@code this}
	 */
	public DfsBlockCacheConfig setBlockLimit(final long newLimit) {
		blockLimit = newLimit;
		return this;
	}

	/**
	 * @return size in bytes of a single window mapped or read in from the pack
	 *         file. <b>Default is 64 KB.</b>
	 */
	public int getBlockSize() {
		return blockSize;
	}

	/**
	 * @param newSize
	 *            size in bytes of a single window read in from the pack file.
	 * @return {@code this}
	 */
	public DfsBlockCacheConfig setBlockSize(final int newSize) {
		blockSize = Math.max(512, newSize);
		return this;
	}

	/** @return number of bytes to read ahead sequentially by. */
	public int getReadAheadLimit() {
		return readAheadLimit;
	}

	/**
	 * @param newSize
	 *            new read-ahead limit, in bytes.
	 * @return {@code this}
	 */
	public DfsBlockCacheConfig setReadAheadLimit(final int newSize) {
		readAheadLimit = Math.max(0, newSize);
		return this;
	}

	/** @return service to perform read-ahead of sequential blocks. */
	public ThreadPoolExecutor getReadAheadService() {
		return readAheadService;
	}

	/**
	 * @param svc
	 *            service to perform read-ahead of sequential blocks with. If
	 *            not null the {@link RejectedExecutionHandler} must be managed
	 *            by the JGit DFS library and not the application.
	 * @return {@code this}.
	 */
	public DfsBlockCacheConfig setReadAheadService(ThreadPoolExecutor svc) {
		if (svc != null)
			svc.setRejectedExecutionHandler(ReadAheadRejectedExecutionHandler.INSTANCE);
		readAheadService = svc;
		return this;
	}

	/**
	 * Update properties by setting fields from the configuration.
	 * <p>
	 * If a property is not defined in the configuration, then it is left
	 * unmodified.
	 *
	 * @param rc
	 *            configuration to read properties from.
	 * @return {@code this}
	 */
	public DfsBlockCacheConfig fromConfig(final Config rc) {
		setBlockLimit(rc.getLong(
				CONFIG_CORE_SECTION,
				CONFIG_DFS_SECTION,
				CONFIG_KEY_BLOCK_LIMIT,
				getBlockLimit()));

		setBlockSize(rc.getInt(
				CONFIG_CORE_SECTION,
				CONFIG_DFS_SECTION,
				CONFIG_KEY_BLOCK_SIZE,
				getBlockSize()));

		setReadAheadLimit(rc.getInt(
				CONFIG_CORE_SECTION,
				CONFIG_DFS_SECTION,
				CONFIG_KEY_READ_AHEAD_LIMIT,
				getReadAheadLimit()));

		int readAheadThreads = rc.getInt(
				CONFIG_CORE_SECTION,
				CONFIG_DFS_SECTION,
				CONFIG_KEY_READ_AHEAD_THREADS,
				0);

		if (0 < getReadAheadLimit() && 0 < readAheadThreads) {
			setReadAheadService(new ThreadPoolExecutor(
					1, // Minimum number of threads kept alive.
					readAheadThreads, // Maximum threads active.
					60, TimeUnit.SECONDS, // Idle threads wait this long before ending.
					new ArrayBlockingQueue<Runnable>(1), // Do not queue deeply.
					new ThreadFactory() {
						private final String name = "JGit-DFS-ReadAhead"; //$NON-NLS-1$
						private final AtomicInteger cnt = new AtomicInteger();
						private final ThreadGroup group = new ThreadGroup(name);

						public Thread newThread(Runnable body) {
							int id = cnt.incrementAndGet();
							Thread thread = new Thread(group, body, name + "-" + id); //$NON-NLS-1$
							thread.setDaemon(true);
							thread.setContextClassLoader(getClass().getClassLoader());
							return thread;
						}
					}, ReadAheadRejectedExecutionHandler.INSTANCE));
		}
		return this;
	}
}
