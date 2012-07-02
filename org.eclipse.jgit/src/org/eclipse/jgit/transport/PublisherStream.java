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
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.transport.Publisher.PublisherException;

/**
 * A singly-linked list with specific support for long-lived windows/iterators
 * that reflect updates to the list in front of the window, so that updates to
 * the list after the window's position are visible to the window after the
 * window is created. This class is required because windows found in
 * java.util.concurrent.* do not guarantee to reflect updates to the list after
 * construction.
 *
 * Nodes in the linked-list support a blocking {@link Node#next(long, TimeUnit)}
 * call until the next node is added.
 */
public class PublisherStream {
	private static class Node {
		private final CountDownLatch nextSet = new CountDownLatch(1);

		private final PublisherPush data;

		private volatile Node next;

		private final AtomicInteger refCount = new AtomicInteger();

		private Node(PublisherPush p, int count) {
			data = p;
			refCount.set(count);
		}

		private PublisherPush get() {
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
		private Node next(long time, TimeUnit unit)
				throws InterruptedException {
			if (next != null)
				return next;
			nextSet.await(time, unit);
			return next;
		}

		private Node next() {
			return next;
		}

		private void setNext(Node n) {
			if (n == null)
				throw new NullPointerException();
			next = n;
			nextSet.countDown();
		}

		private void decrement() throws PublisherException {
			if (refCount.decrementAndGet() == 0) {
				if (data != null)
					data.close();
			}
		}

		@Override
		public String toString() {
			return "Node[" + data + ", " + refCount.get() + "]";
		}
	}

	/**
	 * A window into the stream of nodes.
	 */
	public abstract static class Window {
		private Node current;

		// Don't return data from the start node
		private Node start;

		// Return data from first on the first call to next()
		private Node first;

		private final int markCapacity;

		private final List<Node> marked;

		private Window(Node n, int capacity) {
			current = start = first = n;
			markCapacity = capacity;
			marked = new ArrayList<Node>(capacity);
		}

		/**
		 * @param time
		 * @param unit
		 * @return the next item, blocking until one exists. Return null if the
		 *         call timed out before the next item was added.
		 * @throws InterruptedException
		 * @throws PublisherException
		 */
		PublisherPush next(long time, TimeUnit unit)
				throws InterruptedException, PublisherException {
			Node next;
			if (current == first && current != start) {
				first = null;
				return current.get();
			}
			next = current.next(time, unit);
			if (next == null)
				return null;
			if (!marked.contains(current))
				current.decrement();
			current = next;
			if (current == start) {
				next = current.next(time, unit);
				if (next != null)
					current = next;
				else
					return null;
			}
			return current.get();
		}

		/**
		 * Store the current item so the window's position can be reset to the
		 * current position by calling {@link #rollback(String)}.
		 *
		 * @throws PublisherException
		 */
		public void mark() throws PublisherException {
			marked.add(current);
			if (marked.size() > markCapacity) {
				Node n = marked.remove(0);
				n.decrement();
			}
		}

		/**
		 * Reset the current pointer to after the item's position.
		 *
		 * @param pushId
		 * @return true if the stream was successfully rolled back so that the
		 *         next call to {@link #next(long, TimeUnit)} will return the
		 *         item.
		 */
		public boolean rollback(String pushId) {
			boolean matched = false;
			Node n;
			for (Iterator<Node> it = marked.iterator(); it.hasNext(); ) {
				n = it.next();
				if (matched)
					it.remove();
				else if (n.get().getPushId() == pushId) {
					current = n;
					matched = true;
				}
			}
			return matched;
		}

		/**
		 * Prepend an item to only this window. This should only be used before
		 * any calls to {@link #next(long, TimeUnit)}, or else
		 * {@link #rollback(String)} will not work properly.
		 *
		 * @param item
		 */
		public void prepend(PublisherPush item) {
			Node newNode = new Node(item, 1);
			newNode.setNext(current);
			current = first = newNode;
		}

		/**
		 * Delete this window, and decrement all reference counts. This is
		 * only called with the stream locked.
		 *
		 * @param last
		 * @throws PublisherException
		 */
		protected void delete(Node last) throws PublisherException {
			Node prev = null;
			for (Node n : marked) {
				prev = n;
				n.decrement();
			}
			// The current node may be the last item in the marked list; only
			// decrement once.
			if (prev == current)
				current = current.next();

			while (current != null) {
				current.decrement();
				if (current == last)
					break;
				current = current.next();
			}
			current = null;
		}

		/**
		 * Release all resources used by this window.
		 *
		 * @throws PublisherException
		 */
		abstract public void release() throws PublisherException;
	}

	private Node tail = new Node(null, 0);

	private int windowCount;

	/**
	 * @param item
	 *            to add to the end of the list.
	 * @throws PublisherException
	 */
	public synchronized void add(PublisherPush item) throws PublisherException {
		Node prev = tail;
		tail = new Node(item, windowCount);
		if (windowCount == 0)
			item.close();
		prev.setNext(tail);
	}

	/**
	 * @param markCapacity
	 *            the number of marks to keep when calling
	 *            {@link Window#mark()}.
	 * @return a window that traverses this list starting after the tail,
	 *         even if new nodes are added.
	 */
	public synchronized Window newWindow(int markCapacity) {
		windowCount++;
		return new Window(tail, markCapacity) {
			@Override
			public void release() throws PublisherException {
				Node last;
				synchronized (PublisherStream.this) {
					windowCount--;
					last = tail;
				}
				delete(last);
			}
		};
	}
}
