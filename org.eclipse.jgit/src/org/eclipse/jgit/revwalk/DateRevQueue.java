/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.revwalk;

import java.io.IOException;
import java.util.Comparator;
import java.util.PriorityQueue;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;

/** A queue of commits sorted by commit time order. */
public class DateRevQueue extends AbstractRevQueue {
	private PriorityQueue<Entry> queue =
			new PriorityQueue<Entry>(32, new EntryComparator());
	private int counter;

	/** Create an empty date queue. */
	public DateRevQueue() {
		super();
	}

	DateRevQueue(final Generator s) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		for (;;) {
			final RevCommit c = s.next();
			if (c == null)
				break;
			add(c);
		}
	}

	public void add(final RevCommit c) {
		queue.add(new Entry(c, counter++));
	}

	public RevCommit next() {
		final Entry q = queue.poll();
		return q != null ? q.commit : null;
	}

	/**
	 * Peek at the next commit, without removing it.
	 *
	 * @return the next available commit; null if there are no commits left.
	 */
	public RevCommit peek() {
		final Entry q = queue.peek();
		return q != null ? q.commit : null;
	}

	public void clear() {
		queue.clear();
	}

	boolean everbodyHasFlag(final int f) {
		for (Entry q : queue) {
			if ((q.commit.flags & f) == 0)
				return false;
		}
		return true;
	}

	boolean anybodyHasFlag(final int f) {
		for (Entry q : queue) {
			if ((q.commit.flags & f) != 0)
				return true;
		}
		return false;
	}

	@Override
	int outputType() {
		return outputType | SORT_COMMIT_TIME_DESC;
	}

	public String toString() {
		final StringBuilder s = new StringBuilder();
		for (Entry q : queue)
			describe(s, q.commit);
		return s.toString();
	}

	static class Entry {
		RevCommit commit;
		int n;

		Entry(RevCommit c, int n) {
			commit = c;
			this.n = n;
		}
	}

	private static class EntryComparator implements Comparator<Entry> {
		public int compare(Entry q1, Entry q2) {
			final int d = q2.commit.commitTime - q1.commit.commitTime;
			return d != 0 ? d : q1.n - q2.n;
		}
	}
}
