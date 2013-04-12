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

package org.eclipse.jgit.internal.storage.pack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ThreadSafeProgressMonitor;
import org.eclipse.jgit.storage.pack.PackConfig;

final class DeltaTask implements Callable<Object> {
	static final class Block {
		private static final int MIN_TOP_PATH = 5 << 10;

		final List<DeltaTask> tasks;
		final int threads;
		final PackConfig config;
		final ObjectReader templateReader;
		final DeltaCache dc;
		final ThreadSafeProgressMonitor pm;
		final ObjectToPack[] list;
		final int beginIndex;
		final int endIndex;

		private TopPath[] topPaths;
		private int topPathCnt;
		private int nextTopPath;
		private long totalWeight;

		Block(int threads, PackConfig config, ObjectReader reader,
				DeltaCache dc, ThreadSafeProgressMonitor pm,
				ObjectToPack[] list, int begin, int end) {
			this.tasks = new ArrayList<DeltaTask>(threads);
			this.threads = threads;
			this.config = config;
			this.templateReader = reader;
			this.dc = dc;
			this.pm = pm;
			this.list = list;
			this.beginIndex = begin;
			this.endIndex = end;
			this.topPaths = new TopPath[threads];
		}

		synchronized Slice stealWork() {
			for (;;) {
				DeltaTask maxTask = null;
				Slice maxSlice = null;
				int maxWork = 0;

				for (DeltaTask task : tasks) {
					Slice s = task.remaining();
					if (s != null && maxWork < s.size()) {
						maxTask = task;
						maxSlice = s;
						maxWork = s.size();
					}
				}
				if (maxTask == null)
					return null;
				if (maxTask.tryStealWork(maxSlice))
					return maxSlice;
			}
		}

		void partitionTasks() {
			computeTopPaths();

			int topIdx = 0;
			long weightPerThread = totalWeight / threads;
			for (int i = beginIndex; i < endIndex;) {
				DeltaTask task = new DeltaTask(this);

				// Assign the thread one top path.
				if (topIdx < topPathCnt)
					task.add(topPaths[topIdx++].slice());

				// Assign the thread ~average weight.
				int s = i;
				long w = 0;
				for (; w < weightPerThread && i < endIndex; i++) {
					if (s < i && topPathAt(i)) {
						task.add(new Slice(s, i));
						s = endOfTopPath();
					}
					w += list[i].getWeight();
				}

				// Round up the slice to the end of a path.
				if (s < i) {
					int h = list[i - 1].getPathHash();
					while (i < endIndex) {
						if (h == list[i].getPathHash())
							i++;
						else
							break;
					}
					task.add(new Slice(s, i));
				}
				if (!task.slices.isEmpty())
					tasks.add(task);
			}

			topPaths = null;
		}

		private void computeTopPaths() {
			int cp = beginIndex;
			int ch = list[cp].getPathHash();
			long cw = list[cp].getWeight();
			totalWeight = list[cp].getWeight();

			for (int i = cp + 1; i < endIndex; i++) {
				ObjectToPack o = list[i];
				if (ch != o.getPathHash()) {
					if (MIN_TOP_PATH < cw) {
						if (topPathCnt < topPaths.length) {
							TopPath p = new TopPath(cw, ch, cp, i);
							topPaths[topPathCnt] = p;
							if (++topPathCnt == topPaths.length)
								Arrays.sort(topPaths);
						} else if (topPaths[0].weight < cw) {
							topPaths[0] = new TopPath(cw, ch, cp, i);
							if (topPaths[0].compareTo(topPaths[1]) > 0)
								Arrays.sort(topPaths);
						}
					}
					cp = i;
					ch = o.getPathHash();
					cw = 0;
				}
				cw += o.getWeight();
				totalWeight += o.getWeight();
			}

			// Sort by starting index to identify gaps later.
			Arrays.sort(topPaths, 0, topPathCnt, new Comparator<TopPath>() {
				public int compare(TopPath a, TopPath b) {
					return a.beginIdx - b.beginIdx;
				}
			});

			for (int i = 0; i < topPathCnt; i++)
				totalWeight -= topPaths[i].weight;
		}

		private boolean topPathAt(int i) {
			return nextTopPath < topPathCnt
					&& i == topPaths[nextTopPath].beginIdx;
		}

		private int endOfTopPath() {
			return topPaths[nextTopPath++].endIdx;
		}
	}

	static final class TopPath implements Comparable<TopPath> {
		final long weight;
		final int pathHash;
		final int beginIdx;
		final int endIdx;

		TopPath(long weight, int pathHash, int beginIdx, int endIdx) {
			this.weight = weight;
			this.pathHash = pathHash;
			this.beginIdx = beginIdx;
			this.endIdx = endIdx;
		}

		public int compareTo(TopPath o) {
			int cmp = Long.signum(weight - o.weight);
			if (cmp != 0)
				return cmp;
			cmp = (pathHash >>> 1) - (o.pathHash >>> 1);
			if (cmp != 0)
				return cmp;
			return (pathHash & 1) - (pathHash & 1);
		}

		Slice slice() {
			return new Slice(beginIdx, endIdx);
		}
	}

	static final class Slice {
		final int beginIndex;
		final int endIndex;

		Slice(int b, int e) {
			beginIndex = b;
			endIndex = e;
		}

		final int size() {
			return endIndex - beginIndex;
		}
	}

	private final Block block;
	private final List<Slice> slices;
	private volatile DeltaWindow dw;

	DeltaTask(Block b) {
		this.block = b;
		this.slices = new ArrayList<Slice>(4);
	}

	void add(Slice s) {
		slices.add(s);
	}

	public Object call() throws Exception {
		ObjectReader or = block.templateReader.newReader();
		try {
			for (Slice s : slices)
				doSlice(or, s);
			for (Slice s = block.stealWork(); s != null; s = block.stealWork())
				doSlice(or, s);
		} finally {
			or.release();
			block.pm.endWorker();
		}
		return null;
	}

	private void doSlice(ObjectReader or, Slice s) throws IOException {
		dw = new DeltaWindow(block.config, block.dc, or, block.pm,
				block.list, s.beginIndex, s.endIndex);
		dw.search();
		dw = null;
	}

	Slice remaining() {
		DeltaWindow d = dw;
		return d != null ? d.remaining() : null;
	}

	boolean tryStealWork(Slice s) {
		DeltaWindow d = dw;
		return d != null ? d.tryStealWork(s) : false;
	}
}
