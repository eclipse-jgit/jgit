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

import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
	private static class Data {
		private static final Data EMPTY = new Data(null, 0);

		private final PublisherPush push;

		private final AtomicInteger references;

		private Data(PublisherPush push, int count) {
			this.push = push;
			references = new AtomicInteger(count);
		}

		private void decrement() throws PublisherException {
			if (push != null) {
				int count = references.decrementAndGet();
				if (count == 0)
					push.close();
				else if (count < 0)
					throw new IllegalStateException("too many decrements");
			}
		}

		private boolean isPushId(String pushId) {
			return push != null && push.getPushId().equals(pushId);
		}

		@Override
		public String toString() {
			return "Data[" + push + ", " + references.get() + "]";
		}
	}

	private static abstract class Node {
		private final Data data;

		private Node(Data data) {
			if (data == null)
				throw new NullPointerException();
			this.data = data;
		}

		protected abstract Node next(long time, TimeUnit unit)
				throws InterruptedException;

		@Override
		public String toString() {
			return "Node[" + data + "]";
		}
	}

	private static class BlockingNode extends Node {
		private final CountDownLatch nextBarrier;

		private volatile Node next;

		private BlockingNode(Data data) {
			super(data);
			nextBarrier = new CountDownLatch(1);
		}

		@Override
		protected Node next(long time, TimeUnit unit)
				throws InterruptedException {
			nextBarrier.await(time, unit);
			return next;
		}

		private void setNext(Node n) {
			if (n == null)
				throw new NullPointerException();
			next = n;
			nextBarrier.countDown();
		}
	}

	private static class LinkingNode extends Node {
		private final Node next;

		private LinkingNode(Data data, Node next) {
			super(data);
			if (next == null)
				throw new NullPointerException();
			this.next = next;
		}

		@Override
		protected Node next(long time, TimeUnit unit) {
			return next;
		}
	}

	/**
	 * Forwards the next calls to the delegate. The data is provided by the
	 * creator. This ensures the data from the delegate is never decremented, so
	 * the reference count does not get out of sync.
	 */
	private static class ForwardingNode extends Node {
		private final Node delegate;

		private ForwardingNode(Data data, Node delegate) {
			super(data);
			this.delegate = delegate;
		}

		@Override
		protected Node next(long time, TimeUnit unit)
				throws InterruptedException {
			return delegate.next(time, unit);
		}
	}

	/**
	 * A window into the stream of {@link PublisherPush}es.
	 *
	 * <pre>
	 * Window window = stream.newWindow(2);
	 * window.prepend(new PublisherPush("pack-prepend"));
	 * PublisherPush prependedPush = window.next(1, TimeUnit.SECONDS);
	 * if (push == null) { timed out, no next node found }
	 * else { window.mark(); push...; }
	 * window.rollback("pack-prepend");
	 * PublisherPush packAfterPrepend = window.next(...);
	 * </pre>
	 */
	public abstract static class Window {
		private Node current;

		private final int markCapacity;

		private final ArrayDeque<Data> marked;

		private Window(final Node n, int capacity) {
			current = new ForwardingNode(Data.EMPTY, n);
			markCapacity = capacity;
			marked = new ArrayDeque<Data>(capacity);
		}

		/**
		 * @param time
		 *            the time to block until the next item is added.
		 * @param unit
		 *            units for the time argument.
		 * @return the next item, blocking until one exists. Return null if the
		 *         call timed out before the next item was added.
		 * @throws InterruptedException
		 * @throws PublisherException
		 */
		PublisherPush next(long time, TimeUnit unit)
				throws InterruptedException, PublisherException {
			do {
				// No need to decrement the time, since we will only block
				// on BlockingNodes, which also
				Node next = current.next(time, unit);
				if (next == null)
					return null; // timeout
				current.data.decrement();
				current = next;
			} while (current.data.push == null);
			return current.data.push;
		}

		/**
		 * Store the current item so the window's position can be reset to the
		 * current position by calling {@link #rollback(String)}. If the stream
		 * passes an item without marking it, that item's reference count will
		 * be decremented.
		 *
		 * @throws PublisherException
		 */
		public void mark() throws PublisherException {
			if (current.data.push != null) {
				if (marked.size() >= markCapacity) {
					Data data = marked.removeLast();
					data.decrement();
				}
				marked.addFirst(current.data);
				// Ensure the data is only available in the marked list.
				current = new ForwardingNode(Data.EMPTY, current);
			}
		}

		/**
		 * Reset the current pointer to after the item's position.
		 *
		 * @param pushId
		 *            rollback to the item after the item with this pushId. If
		 *            pushId is null, rollback as far as possible.
		 * @return true if the stream was successfully rolled back so that the
		 *         next call to {@link #next(long, TimeUnit)} will return the
		 *         next item.
		 * @throws PublisherException
		 */
		public boolean rollback(String pushId) throws PublisherException {
			if (current.data.isPushId(pushId))
				return true;

			Node last = current;
			for (Data data : marked) {
				if (data.isPushId(pushId)) {
					current = new LinkingNode(Data.EMPTY, last);
					while (!marked.isEmpty()
							&& !marked.getFirst().isPushId(pushId))
						marked.removeFirst();
					return true;
				}
				last = new LinkingNode(data, last);
			}
			return false;
		}

		/**
		 * Prepend an item to only this window. The item must have a new and
		 * unique pushId.
		 *
		 * @param item
		 * @throws PublisherException
		 */
		public void prepend(PublisherPush item) throws PublisherException {
			if (item == null)
				throw new NullPointerException();
			Data data = new Data(item, 1);
			Node next = new ForwardingNode(data, current);
			current.data.decrement();
			current = new LinkingNode(Data.EMPTY, next);
		}

		/**
		 * Consumes all nodes in the stream until the last node is reached.
		 *
		 * @param last
		 *            the last node to consume.
		 * @throws PublisherException
		 */
		protected void consume(Data last) throws PublisherException {
			boolean found = false;
			for (Data data : marked) {
				found |= data == last;
				data.decrement();
			}
			marked.clear();

			while (!found) {
				Node n = current;
				while (!(found = n.data == last)) {
					if (!(n instanceof ForwardingNode))
						break;
					n = ((ForwardingNode) n).delegate;
				}
				current.data.decrement();
				try {
					current = current.next(0, TimeUnit.NANOSECONDS);
				} catch (InterruptedException e) {
					throw new PublisherException("unexpected interrupt", e);
				}
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

	private BlockingNode tail = new BlockingNode(Data.EMPTY);

	private int windowCount;

	/**
	 * @param item
	 *            to add to the end of the list.
	 * @throws PublisherException
	 */
	public void add(PublisherPush item) throws PublisherException {
		if (item == null)
			throw new NullPointerException();
		boolean hasWindows;
		synchronized (this) {
			hasWindows = windowCount != 0;
			if (hasWindows) {
				BlockingNode old = tail;
				tail = new BlockingNode(new Data(item, windowCount));
				old.setNext(tail);
			}
		}
		if (!hasWindows)
			item.close();
	}

	private synchronized Node deleteWindow() {
		windowCount--;
		return tail;
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
			private boolean released;

			@Override
			public void release() throws PublisherException {
				if (released)
					throw new IllegalStateException();
				released = true;
				Node last = deleteWindow();
				consume(last.data);
			}
		};
	}
}
