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

package org.eclipse.jgit.storage.dht;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Helper to implement a synchronous method in terms of an asynchronous one.
 * <p>
 * Implementors can use this type to wait for an asynchronous computation to
 * complete on a background thread by passing the Sync instance as though it
 * were the AsyncCallback:
 *
 * <pre>
 * Sync&lt;T&gt; sync = Sync.create();
 * async(..., sync);
 * return sync.get(timeout, TimeUnit.MILLISECONDS);
 * </pre>
 *
 * @param <T>
 *            type of value object.
 */
public abstract class Sync<T> implements AsyncCallback<T> {
	private static final Sync<?> NONE = new Sync<Object>() {
		public void onSuccess(Object result) {
			// Discard
		}

		public void onFailure(DhtException error) {
			// Discard
		}

		@Override
		public Object get(long timeout, TimeUnit unit) throws DhtException,
				InterruptedException, TimeoutException {
			return null;
		}
	};

	/**
	 * Helper method to create a new sync object.
	 *
	 * @param <T>
	 *            type of value object.
	 * @return a new instance.
	 */
	public static <T> Sync<T> create() {
		return new Value<T>();
	}

	/**
	 * Singleton callback that ignores onSuccess, onFailure.
	 *
	 * @param <T>
	 *            type of value object.
	 * @return callback that discards all results.
	 */
	@SuppressWarnings("unchecked")
	public static <T> Sync<T> none() {
		return (Sync<T>) NONE;
	}

	/**
	 * Wait for the asynchronous operation to complete.
	 * <p>
	 * To prevent application deadlock, waiting can only be performed with the
	 * supplied timeout.
	 *
	 * @param timeout
	 *            amount of time to wait before failing.
	 * @return the returned value.
	 * @throws DhtException
	 *             the asynchronous operation failed.
	 * @throws InterruptedException
	 *             the current thread was interrupted before the operation
	 *             completed.
	 * @throws TimeoutException
	 *             the timeout elapsed before the operation completed.
	 */
	public T get(Timeout timeout) throws DhtException, InterruptedException,
			TimeoutException {
		return get(timeout.getTime(), timeout.getUnit());
	}

	/**
	 * Wait for the asynchronous operation to complete.
	 * <p>
	 * To prevent application deadlock, waiting can only be performed with the
	 * supplied timeout.
	 *
	 * @param timeout
	 *            amount of time to wait before failing.
	 * @param unit
	 *            units of {@code timeout}. For example
	 *            {@link TimeUnit#MILLISECONDS}.
	 * @return the returned value.
	 * @throws DhtException
	 *             the asynchronous operation failed.
	 * @throws InterruptedException
	 *             the current thread was interrupted before the operation
	 *             completed.
	 * @throws TimeoutException
	 *             the timeout elapsed before the operation completed.
	 */
	public abstract T get(long timeout, TimeUnit unit) throws DhtException,
			InterruptedException, TimeoutException;

	private static class Value<T> extends Sync<T> {

		private final CountDownLatch wait = new CountDownLatch(1);

		private T data;

		private DhtException error;

		/**
		 * Wait for the asynchronous operation to complete.
		 * <p>
		 * To prevent application deadlock, waiting can only be performed with
		 * the supplied timeout.
		 *
		 * @param timeout
		 *            amount of time to wait before failing.
		 * @param unit
		 *            units of {@code timeout}. For example
		 *            {@link TimeUnit#MILLISECONDS}.
		 * @return the returned value.
		 * @throws DhtException
		 *             the asynchronous operation failed.
		 * @throws InterruptedException
		 *             the current thread was interrupted before the operation
		 *             completed.
		 * @throws TimeoutException
		 *             the timeout elapsed before the operation completed.
		 */
		public T get(long timeout, TimeUnit unit) throws DhtException,
				InterruptedException, TimeoutException {
			if (wait.await(timeout, unit)) {
				if (error != null)
					throw error;
				return data;
			}
			throw new TimeoutException();
		}

		public void onSuccess(T obj) {
			data = obj;
			wait.countDown();
		}

		public void onFailure(DhtException err) {
			error = err;
			wait.countDown();
		}
	}
}
