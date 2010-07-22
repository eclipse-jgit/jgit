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

package org.eclipse.jgit.lib;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Wrapper around the general {@link ProgressMonitor} to make it thread safe.
 */
public class ThreadSafeProgressMonitor implements ProgressMonitor {
	private final ProgressMonitor pm;

	private final ReentrantLock lock;

	/**
	 * Wrap a ProgressMonitor to be thread safe.
	 *
	 * @param pm
	 *            the underlying monitor to receive events.
	 */
	public ThreadSafeProgressMonitor(ProgressMonitor pm) {
		this.pm = pm;
		this.lock = new ReentrantLock();
	}

	public void start(int totalTasks) {
		lock.lock();
		try {
			pm.start(totalTasks);
		} finally {
			lock.unlock();
		}
	}

	public void beginTask(String title, int totalWork) {
		lock.lock();
		try {
			pm.beginTask(title, totalWork);
		} finally {
			lock.unlock();
		}
	}

	public void update(int completed) {
		lock.lock();
		try {
			pm.update(completed);
		} finally {
			lock.unlock();
		}
	}

	public boolean isCancelled() {
		lock.lock();
		try {
			return pm.isCancelled();
		} finally {
			lock.unlock();
		}
	}

	public void endTask() {
		lock.lock();
		try {
			pm.endTask();
		} finally {
			lock.unlock();
		}
	}
}
