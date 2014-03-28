/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A stable priority queue implemented using buckets
 * <p>
 * Unlike {@link java.util.PriorityQueue} this priority queue is stable without
 * the need for an additional field (i.e. an index or counter).
 *
 * @author Keunhong Park <keunhong@codeaurora.org>
 * @param <T>
 *            type of list element.
 */
public class BucketQueue<T> {
	private List<List<T>> buckets = new LinkedList<List<T>>();

	private List<T> stage = new ArrayList<T>();

	private Comparator<T> comparator;

	private MinFinder<T> minFinder;

	/**
	 * @param comparator
	 *
	 */
	public BucketQueue(Comparator<T> comparator) {
		this.comparator = comparator;
		this.minFinder = new LinearMinFinder<T>(buckets, comparator, 10);
	}

	/**
	 * Clears queue
	 */
	public void clear() {
		buckets.clear();
		stage.clear();
		minFinder.clear();
	}

	/**
	 * @param c
	 */
	public void add(T c) {
		stage.add(c);
	}

	/**
	 * @return min
	 */
	public T pop() {
		consolidate();

		return minFinder.pop();
	}

	/**
	 * @return min
	 */
	public T peek() {
		consolidate();

		return minFinder.peek();
	}

	private void consolidate() {
		if (!stage.isEmpty()) {
			Collections.reverse(stage);
			Collections.sort(stage, Collections.reverseOrder(comparator));

			buckets.add(0, stage);
			stage = new ArrayList<T>();

			consolidateBuckets();
			minFinder.update(buckets);
		}
	}

	private void consolidateBuckets() {
		if (buckets.size() < 2)
			return;

		List<List<T>> mergeableBuckets = new LinkedList<List<T>>();

		Iterator<List<T>> it = buckets.iterator();

		int mergeSize = it.next().size();

		// Get list of mergeable buckets
		while (it.hasNext()) {
			List<T> bucket = it.next();
			if (mergeSize < bucket.size() / 2) {
				break;
			}

			mergeableBuckets.add(bucket);
			mergeSize += bucket.size();

			it.remove();
		}

		if (mergeableBuckets.size() > 0) {
			mergeableBuckets.add(0, buckets.remove(0));
			buckets.add(0, mergeBuckets(mergeableBuckets, mergeSize));
		}
	}

	/**
	 * Merge the given buckets in the order of the comparator. If there is a tie
	 * the value from the later bucket is taken.
	 *
	 * @param buckets
	 * @param mergeSize
	 * @return merged bucket
	 */
	private List<T> mergeBuckets(List<List<T>> buckets, int mergeSize) {
		@SuppressWarnings("unchecked")
		ArrayList<T> mergedBucket = new ArrayList(
				Arrays.asList((T[]) new Object[mergeSize]));

		MinFinder<T> mergeMinFinder = new LinearMinFinder<T>(buckets,
				comparator, mergeSize);

		for (int index = mergeSize - 1; buckets.size() > 0; index--) {
			mergedBucket.set(index, mergeMinFinder.pop());
		}

		return mergedBucket;
	}

	/**
	 * @return number of buckets
	 */
	int getNumBuckets() {
		return buckets.size();
	}

	/**
	 * @return total number of commits
	 */
	public int size() {
		int computedSize = stage.size();
		for (List<T> b : buckets) {
			computedSize += b.size();
		}

		return computedSize;
	}

	/**
	 * @return a list of iterators containing an iterator for each bucket and
	 *         the stage
	 */
	public List<Iterator<T>> getIterators() {
		List<Iterator<T>> iterators = new ArrayList<Iterator<T>>(
				buckets.size() + 1);
		iterators.add(stage.iterator());
		for (List<T> b : buckets) {
			iterators.add(b.iterator());
		}

		return iterators;
	}

	@Override
	public String toString() {
		String output = "<<<<<<<<<<\nStage: ";
		for (T rc : stage) {
			output += rc + " ";
		}
		output += "\n\n";
		for (List<T> b : buckets) {
			for (T rc : b) {
				output += rc + " ";
			}
			output += "\n";
		}
		output += ">>>>>>>>>>";

		return output;
	}
}
