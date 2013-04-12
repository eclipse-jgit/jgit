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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ThreadSafeProgressMonitor;
import org.eclipse.jgit.storage.pack.PackConfig;

final class DeltaTask implements Callable<Object> {
	static final class Block {
		private static final int MIN_TOP_PATH = 50 << 20;

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
				System.out.println("cannot steal");
			}
		}

		void partitionTasks() {
			computeTopPaths();

			int topIdx = 0;
			long weightPerThread = totalWeight / threads;
			for (int i = beginIndex; i < endIndex;) {
				DeltaTask task = new DeltaTask(this, tasks.size());

				// Assign the thread one top path.
				if (topIdx < topPathCnt) {
					TopPath p = topPaths[topIdx++];
					// w += p.weight;
					task.add(p.slice());
				}

				// Assign the task thread ~average weight.
				int s = i;
				long w = 0;
				for (; w < weightPerThread && i < endIndex;) {
					if (topPathAt(i)) {
						if (s < i)
							task.add(new Slice(s, i));
						s = i = endOfTopPath();
					} else
						w += list[i++].getWeight();
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
System.out.println(String.format("%d starts with %d slices, %d bytes", task.tid, task.slices.size(), w));
				if (!task.slices.isEmpty())
					tasks.add(task);
			}
			for (;topIdx < topPathCnt; topIdx++) {
				DeltaTask task = new DeltaTask(this, tasks.size());
				TopPath p = topPaths[topIdx++];
				System.out.println(String.format(
						"%d starts with %d slices, %d bytes", task.tid,
						task.slices.size(), p.weight));
				task.add(p.slice());
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

System.out.println(String.format("%d top paths:", topPathCnt));
for(int i = 0; i < topPathCnt; i++)
System.out.println(String.format("  %8x %5d %5d", topPaths[i].pathHash,
		topPaths[i].endIdx - topPaths[i].beginIdx,
		topPaths[i].weight));
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
			return beginIdx - o.beginIdx;
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
	private final int tid;
	private final LinkedList<Slice> slices;
	private DeltaWindow dw;

	DeltaTask(Block b, int tid) {
		this.block = b;
		this.tid = tid;
		this.slices = new LinkedList<Slice>();
	}

	void add(Slice s) {
		slices.add(s);
	}

	public Object call() throws Exception {
		ObjectReader or = block.templateReader.newReader();
		try {
			for (;;) {
				Slice s;
				synchronized (this) {
					if (slices.isEmpty())
						break;
					s = slices.removeFirst();
				}
				doSlice(or, s);
			}
			for (Slice s = block.stealWork(); s != null; s = block.stealWork()) {
				System.out.println(String.format("%d stole %d", tid, s.size()));
				doSlice(or, s);
			}
		} finally {
			or.release();
			block.pm.endWorker();
		}
		System.out.println(String.format("thread %d end", tid));
		return null;
	}

	private void doSlice(ObjectReader or, Slice s) throws IOException {
		DeltaWindow w = new DeltaWindow(block.config, block.dc,
				or, block.pm,
				block.list, s.beginIndex, s.endIndex);
		synchronized (this) {
			dw = w;
		}
		w.search();
		synchronized (this) {
			dw = null;
		}
	}

	synchronized Slice remaining() {
		if (!slices.isEmpty())
			return slices.getLast();
		DeltaWindow d = dw;
		return d != null ? d.remaining() : null;
	}

	synchronized boolean tryStealWork(Slice s) {
		if (!slices.isEmpty() && slices.getLast().beginIndex == s.beginIndex) {
			slices.removeLast();
			return true;
		}
		DeltaWindow d = dw;
		return d != null ? d.tryStealWork(s) : false;
	}
}
