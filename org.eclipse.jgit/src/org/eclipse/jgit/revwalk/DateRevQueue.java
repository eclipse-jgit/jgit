/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>,
 * Copyright (C) 2013, Gustaf Lundh <gustaf.lundh@sonymobile.com>
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
import java.util.LinkedList;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;

/** A queue of commits sorted by commit time order. */
public class DateRevQueue extends AbstractRevQueue {
	private static final int REBUILD_INDEX_COUNT = 1000;

	private Entry head;

	private Entry free;

	private int inQueue = 0;

	private int sinceLastIndex = 0;

	private LinkedList<Entry> index = new LinkedList<Entry>();

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
		sinceLastIndex++;
		if (++inQueue > REBUILD_INDEX_COUNT
				&& sinceLastIndex > REBUILD_INDEX_COUNT) {
			sinceLastIndex = 0;
			buildIndex();
		}

		Entry q = head;
		final long when = c.commitTime;

		if (!index.isEmpty()) {
			Entry prev = null;
			for (Entry i : index) {
				if (when > i.commit.commitTime) {
					if (prev != null) {
						q = prev;
					}
					break;
				}
				prev = i;
			}
		}

		final Entry n = newEntry(c);
		if (q == null || when > q.commit.commitTime) {
			n.next = q;
			head = n;
		} else {
			Entry p = q.next;
			while (p != null && p.commit.commitTime > when) {
				q = p;
				p = q.next;
			}
			n.next = q.next;
			q.next = n;
		}
	}

	public RevCommit next() {
		final Entry q = head;
		if (q == null)
			return null;

		if (!index.isEmpty() && q == index.getFirst())
			index.remove();
		inQueue--;

		head = q.next;
		freeEntry(q);
		return q.commit;
	}

	private void buildIndex() {
		int i = 0;
		index.clear();
		Entry q = head;
		while (q != null) {
			if (++i % 100 == 0) {
				index.add(q);
			}
			q = q.next;
		}
	}

	/**
	 * Peek at the next commit, without removing it.
	 *
	 * @return the next available commit; null if there are no commits left.
	 */
	public RevCommit peek() {
		return head != null ? head.commit : null;
	}

	public void clear() {
		head = null;
		free = null;
	}

	boolean everbodyHasFlag(final int f) {
		for (Entry q = head; q != null; q = q.next) {
			if ((q.commit.flags & f) == 0)
				return false;
		}
		return true;
	}

	boolean anybodyHasFlag(final int f) {
		for (Entry q = head; q != null; q = q.next) {
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
		for (Entry q = head; q != null; q = q.next)
			describe(s, q.commit);
		return s.toString();
	}

	private Entry newEntry(final RevCommit c) {
		Entry r = free;
		if (r == null)
			r = new Entry();
		else
			free = r.next;
		r.commit = c;
		return r;
	}

	private void freeEntry(final Entry e) {
		e.next = free;
		free = e;
	}

	static class Entry {
		Entry next;

		RevCommit commit;
	}
}
