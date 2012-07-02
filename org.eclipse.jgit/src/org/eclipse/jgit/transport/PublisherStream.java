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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A singly-linked list with specific support for long-lived iterators that
 * reflect updates to the list in front of the iterator, so that updates to the
 * list after the iterator's position are visible to the iterator after the
 * iterator is created. This class is required because iterators found in
 * java.util.concurrent.* do not guarantee to reflect updates to the list after
 * construction.
 *
 * Nodes in the linked-list support a blocking {@link Node#next(long, TimeUnit)}
 * call until the next node is added.
 *
 * @param <T>
 */
public class PublisherStream<T extends PublisherStream.RefCounted> {
	/**
	 * A reference counted object, with reference decrementing handled by the
	 * PublisherStream class.
	 */
	public static interface RefCounted {
		/**
		 * Called once upon insertion into the stream with the current number
		 * of Windows that will reference it.
		 *
		 * @param number
		 */
		void setReferences(int number);

		/**
		 * Called once when a Window can no longer reference this node.
		 */
		void decrement();
	}

	private static class Node<D extends RefCounted> {
		private final CountDownLatch condition = new CountDownLatch(1);

		private final D data;

		private volatile Node<D> next;

		private Node(D p) {
			data = p;
		}

		private D get() {
			return data;
		}

		/**
		 * Wait for {@code time} for the {@link #next} pointer to be populated.
		 *
		 * @param time
		 * @param unit
		 * @return the next item, or null if there is no next item and the call
		 *         timed out.
		 * @throws InterruptedException
		 */
		private Node<D> next(long time, TimeUnit unit)
				throws InterruptedException {
			if (next != null)
				return next;
			condition.await(time, unit);
			return next;
		}

		private Node<D> next() {
			return next;
		}

		private void setNext(Node<D> n) {
			if (n == null)
				throw new NullPointerException();
			next = n;
			condition.countDown();
		}
	}

	/**
	 * A window/iterator into the stream of nodes.
	 *
	 * @param <D>
	 */
	public abstract static class Window<D extends RefCounted> {
		private Node<D> current;

		private Node<D> first;

		private final int markCapacity;

		private final List<Node<D>> marked;

		private Window(Node<D> start, int capacity) {
			current = first = start;
			markCapacity = capacity;
			marked = new ArrayList<Node<D>>();
		}

		/**
		 * @param time
		 * @param unit
		 * @return the next item, blocking until one exists. Return null if the
		 *         call timed out before the next item was added.
		 * @throws InterruptedException
		 */
		D next(long time, TimeUnit unit) throws InterruptedException {
			if (current == first) {
				D data = current.get();
				first = null;
				return data;
			}
			Node<D> next = current.next(time, unit);
			if (next == null)
				return null;
			if (!marked.contains(current)) {
				D data = current.get();
				if (data != null)
					data.decrement();
			}
			current = next;
			return current.get();
		}

		/**
		 * @return true if a call to next() will return without blocking, false
		 *         if there is not currently a next element and a call to next()
		 *         will block until one exists or times out according to
		 *         parameters.
		 */
		public boolean hasNext() {
			return (current.next() != null);
		}

		/**
		 * @return the next item immediately without advancing the list, or null
		 *         if no next item exists yet. This call will not block until a
		 *         next item exists, and should be used with {@link #hasNext()}.
		 */
		public D peek() {
			return (hasNext()) ? current.get() : null;
		}

		/**
		 * Store the current item so the window's position can be reset to the
		 * current position by calling {@link #rollback(RefCounted)}.
		 */
		public void mark() {
			marked.add(current);
			if (marked.size() > markCapacity) {
				Node<D> n = marked.remove(0);
				RefCounted rc = n.get();
				if (rc != null)
					rc.decrement();
			}
		}

		/**
		 * Reset the current pointer to after the item's position.
		 *
		 * @param item
		 * @return true if the stream was successfully rolled back so that the
		 *         next call to {@link #next(long, TimeUnit)} will return the
		 *         item.
		 */
		public boolean rollback(D item) {
			boolean matched = false;
			Node<D> n;
			for (Iterator<Node<D>> it = marked.iterator(); it.hasNext(); ) {
				n = it.next();
				if (matched)
					it.remove();
				else if (n.get() == item) {
					current = n;
					matched = true;
				}
			}
			return matched;
		}

		/**
		 * Prepend an item to only this iterator. The item's reference count
		 * will be set to 1.
		 *
		 * @param item
		 */
		public void prepend(D item) {
			item.setReferences(1);
			Node<D> newNode = new Node<D>(item);
			newNode.setNext(current);
			current = first = newNode;
		}

		/**
		 * Delete this iterator, and decrement all reference counts. This is
		 * only called with the stream locked.
		 */
		private void delete() {
			RefCounted rc = null;
			for (Node<D> n : marked) {
				rc = n.get();
				if (rc != null)
					rc.decrement();
			}
			// The current node may be the last item in the marked list; only
			// decrement once.
			if (rc != current.get()) {
				rc = current.get();
				if (rc != null)
					rc.decrement();
			}
			while (hasNext()) {
				current = current.next();
				rc = current.get();
				if (rc != null)
					rc.decrement();
			}
			current = null;
		}

		/** Release all resources used by this window. */
		abstract public void release();
	}

	private volatile Node<T> tail = new Node<T>(null);

	private volatile int windowCount;

	/**
	 * @param item
	 *            to add to the end of the list.
	 */
	public synchronized void add(T item) {
		Node<T> prev = tail;
		tail = new Node<T>(item);
		prev.setNext(tail);
		item.setReferences(windowCount);
	}

	/**
	 * @param markCapacity
	 *            the number of marks to keep when calling
	 *            {@link Window#mark()}.
	 * @return an iterator that traverses this list starting at the tail, even
	 *         if new nodes are added.
	 */
	public synchronized Window<T> newIterator(int markCapacity) {
		windowCount++;
		return new Window<T>(tail, markCapacity) {
			@Override
			public void release() {
				deleteIterator(this);
			}
		};
	}

	/**
	 * @param window
	 */
	public synchronized void deleteIterator(Window<T> window) {
		windowCount--;
		window.delete();
	}
}
