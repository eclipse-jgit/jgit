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

package org.eclipse.jgit.storage.dht.spi.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Optional executor support for implementors to build on top of. */
public class ExecutorTools {
	/**
	 * Get the default executor service for this JVM.
	 * <p>
	 * The default executor service is created the first time it is requested,
	 * and is shared with all future requests. It uses a fixed sized thread pool
	 * that is allocated 2 threads per CPU. Each thread is configured to be a
	 * daemon thread, permitting the JVM to do a clean shutdown when the
	 * application thread stop, even if work is still pending in the service.
	 *
	 * @return the default executor service.
	 */
	public static ExecutorService getDefaultExecutorService() {
		return DefaultExecutors.service;
	}

	private static class DefaultExecutors {
		static final ExecutorService service;
		static {
			int ncpu = Runtime.getRuntime().availableProcessors();
			ThreadFactory threadFactory = new ThreadFactory() {
				private final AtomicInteger cnt = new AtomicInteger();

				private final ThreadGroup group = new ThreadGroup("JGit-DHT");

				public Thread newThread(Runnable body) {
					int id = cnt.incrementAndGet();
					String name = "JGit-DHT-Worker-" + id;
					ClassLoader myCL = getClass().getClassLoader();

					Thread thread = new Thread(group, body, name);
					thread.setDaemon(true);
					thread.setContextClassLoader(myCL);
					return thread;
				}
			};
			service = Executors.newFixedThreadPool(2 * ncpu, threadFactory);
		}
	}

	private ExecutorTools() {
		// Static helper class, do not make instances.
	}
}
