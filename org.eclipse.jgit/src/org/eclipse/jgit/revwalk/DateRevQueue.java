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
import java.util.Comparator;
import java.util.Iterator;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.util.BucketQueue;

/** A queue of commits sorted by commit time order. */
public class DateRevQueue extends AbstractRevQueue {
	BucketQueue<RevCommit> bucketQueue = new BucketQueue<RevCommit>(
			new RevCommitTimeComparator());

	/** Create an empty date queue. */
	public DateRevQueue() {
		super();
	}

	/**
	 * Construct from generator
	 *
	 * @param s
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 */
	DateRevQueue(final Generator s) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		for (;;) {
			final RevCommit c = s.next();
			if (c == null)
				break;
			add(c);
		}
	}

	@Override
	public void add(RevCommit c) {
		bucketQueue.add(c);
	}

	/**
	 * Peek at the next commit, without removing it.
	 *
	 * @return the next available commit; null if there are no commits left.
	 */
	public RevCommit peek() {
		return bucketQueue.peek();
	}

	/**
	 * Clears queue
	 */
	@Override
	public void clear() {
		bucketQueue.clear();
	}

	/**
	 * Pops and returns minimum RevCommit
	 *
	 * @return minimum RevCommit
	 */
	@Override
	public RevCommit next() {
		return bucketQueue.pop();
	}

	@Override
	boolean everbodyHasFlag(int f) {
		for (Iterator<RevCommit> it : bucketQueue.getIterators()) {
			while (it.hasNext()) {
				if ((it.next().flags & f) == 0)
					return false;
			}
		}
		return true;
	}

	@Override
	boolean anybodyHasFlag(int f) {
		for (Iterator<RevCommit> it : bucketQueue.getIterators()) {
			while (it.hasNext()) {
				if ((it.next().flags & f) != 0)
					return true;
			}
		}
		return false;
	}

	@Override
	int outputType() {
		return outputType | SORT_COMMIT_TIME_DESC;
	}

	/**
	 * @return total number of commits
	 */
	public int size() {
		return bucketQueue.size();
	}

	@Override
	public String toString() {
		return bucketQueue.toString();
	}

	/**
	 * Compares two RevCommits
	 */
	public class RevCommitTimeComparator implements Comparator<RevCommit> {
		public int compare(RevCommit o1, RevCommit o2) {
			if (o1.commitTime > o2.commitTime)
				return -1;
			if (o1.commitTime < o2.commitTime)
				return 1;
			return 0;
		}
	}
}
