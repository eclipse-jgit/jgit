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

package org.eclipse.jgit.util;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A singly-linked list with specific support for long-lived iterators that
 * reflect updates to the list in front of the iterator, so that updates to the
 * list after the iterator's position are visible to the iterator after the
 * iterator is created. This class is required because iterators found in
 * java.util.concurrent.* do not guarantee to reflect updates to the list after
 * construction.
 *
 * Nodes in the linked-list support a blocking {@link Node#next(long, TimeUnit)}
 * call until the next node is added. This feature is exposed when using the
 * {@link ConcurrentNodeIterator}, but not exposed via {@link NodeIterator}.
 *
 * @param <T>
 *            the type of object wrapped
 */
public class ConcurrentLinkedList<T> {
	/**
	 * Each ReadOnlyIterator must obtain the read lock before reading
	 * {@link Node#next}, and {@link Node#setNext(Node)} must obtain the write
	 * lock.
	 */
	private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

	/** @param <D> */
	public interface ConcurrentIterator<D> {
		/**
		 * @param time
		 * @param unit
		 * @return the next item, blocking until one exists
		 * @throws InterruptedException
		 * @throws TimeoutException
		 */
		D next(long time, TimeUnit unit)
				throws InterruptedException, TimeoutException;

		/**
		 * @return true if a call to next() will return without blocking, false
		 *         if there is not currently a next element and a call to next()
		 *         will block until one exists or times out according to
		 *         parameters.
		 */
		boolean hasNext();

		/**
		 * @return the next item immediately without advancing the list, or null
		 *         if no next item exists yet. This call will not block until a
		 *         next item exists, and should be used with {@link #hasNext()}.
		 */
		D peek();

		/**
		 * Store the position before the current item. When {@link #reset()} is
		 * called, the next call to {@link #next(long, TimeUnit)} will return
		 * the last item from {@link #next(long, TimeUnit)} before this function
		 * was called.
		 */
		void markBefore();

		/** Return to the stored position. */
		void reset();

		/** @return a non-concurrent copy of this iterator */
		Iterator<D> copy();
	}

	private class ConcurrentNodeIterator implements ConcurrentIterator<T> {
		private Node current;

		private Node marked;

		private Node lastRead;

		private ConcurrentNodeIterator(Node start) {
			current = start;
			marked = start;
		}

		public boolean hasNext() {
			Lock readLock = rwLock.readLock();
			readLock.lock();
			try {
				return current.hasNext();
			} finally {
				readLock.unlock();
			}
		}

		public T next(long time, TimeUnit unit)
				throws InterruptedException, TimeoutException {
			Lock readLock = rwLock.readLock();
			readLock.lock();
			try {
				Node next = current.next(time, unit);
				if (next == null)
					return null;
				lastRead = current;
				current = next;
				return current.data();
			} finally {
				readLock.unlock();
			}
		}

		public T peek() {
			Lock readLock = rwLock.readLock();
			readLock.lock();
			try {
				if (!current.hasNext())
					return null;
				Node ret = current.nextNonblocking();
				return ret.data();
			} finally {
				readLock.unlock();
			}
		}

		public void markBefore() {
			marked = lastRead;
		}

		public void reset() {
			current = marked;
		}

		public Iterator<T> copy() {
			return new NodeIterator(current, tail);
		}
	}

	private class NodeIterator implements Iterator<T> {
		Node current;

		Node prev;

		Node end;

		private NodeIterator(Node start, Node stop) {
			current = start;
			end = stop;
		}

		public boolean hasNext() {
			Lock readLock = rwLock.readLock();
			readLock.lock();
			try {
				return current.hasNext() && current != end;
			} finally {
				readLock.unlock();
			}
		}

		public T next() {
			Lock readLock = rwLock.readLock();
			readLock.lock();
			try {
				if (!current.hasNext()) {
					return null;
				}
				prev = current;
				current = current.nextNonblocking();
				return current.data();
			} finally {
				readLock.unlock();
			}
		}

		/**
		 * Remove the last item retrieved by {@link Iterator#next()}. A node
		 * with threads waiting on
		 * {@link ConcurrentLinkedList.Node#next(long, TimeUnit)} cannot be
		 * removed.
		 */
		public void remove() {
			if (prev == null || current.hasWaiters()) {
				return;
			}
			Lock writeLock = rwLock.writeLock();
			writeLock.lock();
			try {
				Node next = current.nextNonblocking();
				if (next == null) {
					tail = prev;
				}
				current = prev;
				current.setNext(next);
				prev = null;
			} finally {
				writeLock.unlock();
			}
		}
	}

	private class Node {
		private final T data;

		private volatile Node next;

		/**
		 * @param data
		 */
		Node(T data) {
			this.data = data;
		}

		/** @return the data stored in this node. */
		T data() {
			return data;
		}

		/**
		 * @param next
		 *            the next node in this list
		 */
		private void setNext(Node next) {
			this.next = next;
			if (tailCondition != null) {
				tailCondition.signalAll();
			}
		}

		/**
		 * @param time
		 * @param unit
		 * @return the next element
		 * @throws InterruptedException
		 * @throws TimeoutException
		 */
		Node next(long time, TimeUnit unit)
				throws InterruptedException, TimeoutException {
			if (next != null)
				return next;

			Lock readLock = rwLock.readLock();
			Lock writeLock = rwLock.writeLock();
			readLock.unlock();
			writeLock.lock();
			try {
				if (next != null)
					return next;
				while (next == null)
					if (!tailCondition.await(time, unit))
						throw new TimeoutException();
				return next;
			} finally {
				readLock.lock();
				writeLock.unlock();
			}
		}

		/** @return the next element, or null if none exists */
		Node nextNonblocking() {
			return next;
		}

		/** @return true if this list has a next node set. */
		boolean hasNext() {
			return (next != null);
		}

		/**
		 * @return true if this node has threads waiting on it. Threads can only
		 *         wait on the tail node, so prevent deletion of the tail node.
		 */
		boolean hasWaiters() {
			return (this == tail);
		}
	}

	/** The oldest/first item in the list. */
	private Node head;

	/** The newest/last item in the list. */
	private Node tail;

	private final Condition tailCondition;

	/**
	 *
	 */
	public ConcurrentLinkedList() {
		head = new Node(null);
		tail = head;
		tailCondition = rwLock.writeLock().newCondition();
	}

	/**
	 * @param item
	 *            to add to the end of the list.
	 */
	public void put(T item) {
		Lock writeLock = rwLock.writeLock();
		writeLock.lock();
		try {
			Node n = new Node(item);
			tail.setNext(n);
			tail = n;
		} finally {
			writeLock.unlock();
		}
	}

	/**
	 * @return an iterator that traverses this list starting at the tail, even
	 *         if new nodes are added.
	 */
	public ConcurrentIterator<T> getTailIterator() {
		return createIterator(tail);
	}

	/**
	 * @return an iterator that traverses this list starting at the head, even
	 *         if new nodes are added.
	 */
	public ConcurrentIterator<T> getHeadIterator() {
		return createIterator(head);
	}

	private ConcurrentIterator<T> createIterator(final Node start) {
		Lock createLock = rwLock.readLock();
		createLock.lock();
		try {
			return new ConcurrentNodeIterator(start);
		} finally {
			createLock.unlock();
		}
	}

	/**
	 * @return an iterator that traverses the list starting at the head,
	 *         stopping when it reaches the last node that existed when the
	 *         iterator was created.
	 */
	public Iterator<T> getWriteIterator() {
		Lock createLock = rwLock.readLock();
		createLock.lock();
		try {
			return new NodeIterator(head, tail);
		} finally {
			createLock.unlock();
		}
	}
}
