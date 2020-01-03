/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ThreadSafeProgressMonitor;
import org.eclipse.jgit.storage.pack.PackConfig;

final class DeltaTask implements Callable<Object> {
	static final long MAX_METER = 9 << 20;

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

		private long totalWeight;
		long bytesPerUnit;

		Block(int threads, PackConfig config, ObjectReader reader,
				DeltaCache dc, ThreadSafeProgressMonitor pm,
				ObjectToPack[] list, int begin, int end) {
			this.tasks = new ArrayList<>(threads);
			this.threads = threads;
			this.config = config;
			this.templateReader = reader;
			this.dc = dc;
			this.pm = pm;
			this.list = list;
			this.beginIndex = begin;
			this.endIndex = end;
		}

		int cost() {
			int d = (int) (totalWeight / bytesPerUnit);
			if (totalWeight % bytesPerUnit != 0)
				d++;
			return d;
		}

		synchronized DeltaWindow stealWork(DeltaTask forThread) {
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
				if (maxTask == null) {
					return null;
				}
				if (maxTask.tryStealWork(maxSlice)) {
					return forThread.initWindow(maxSlice);
				}
			}
		}

		void partitionTasks() {
			ArrayList<WeightedPath> topPaths = computeTopPaths();
			Iterator<WeightedPath> topPathItr = topPaths.iterator();
			int nextTop = 0;
			long weightPerThread = Math.max(totalWeight / threads, 1);
			for (int i = beginIndex; i < endIndex;) {
				DeltaTask task = new DeltaTask(this);
				long w = 0;

				// Assign the thread one top path.
				if (topPathItr.hasNext()) {
					WeightedPath p = topPathItr.next();
					w += p.weight;
					task.add(p.slice);
				}

				// Assign the task thread ~average weight.
				int s = i;
				for (; w < weightPerThread && i < endIndex;) {
					if (nextTop < topPaths.size()
							&& i == topPaths.get(nextTop).slice.beginIndex) {
						if (s < i) {
							task.add(new Slice(s, i));
						}
						s = i = topPaths.get(nextTop++).slice.endIndex;
					} else {
						w += getAdjustedWeight(list[i++]);
					}
				}

				// Round up the slice to the end of a path.
				if (s < i) {
					int h = list[i - 1].getPathHash();
					while (i < endIndex) {
						if (h == list[i].getPathHash()) {
							i++;
						} else {
							break;
						}
					}
					task.add(new Slice(s, i));
				}
				if (!task.slices.isEmpty()) {
					tasks.add(task);
				}
			}
			while (topPathItr.hasNext()) {
				WeightedPath p = topPathItr.next();
				DeltaTask task = new DeltaTask(this);
				task.add(p.slice);
				tasks.add(task);
			}

			topPaths = null;
		}

		private ArrayList<WeightedPath> computeTopPaths() {
			ArrayList<WeightedPath> topPaths = new ArrayList<>(
					threads);
			int cp = beginIndex;
			int ch = list[cp].getPathHash();
			long cw = getAdjustedWeight(list[cp]);
			totalWeight = cw;

			for (int i = cp + 1; i < endIndex; i++) {
				ObjectToPack o = list[i];
				if (ch != o.getPathHash()) {
					if (MIN_TOP_PATH < cw) {
						if (topPaths.size() < threads) {
							Slice s = new Slice(cp, i);
							topPaths.add(new WeightedPath(cw, s));
							if (topPaths.size() == threads) {
								Collections.sort(topPaths);
							}
						} else if (topPaths.get(0).weight < cw) {
							Slice s = new Slice(cp, i);
							WeightedPath p = new WeightedPath(cw, s);
							topPaths.set(0, p);
							if (p.compareTo(topPaths.get(1)) > 0) {
								Collections.sort(topPaths);
							}
						}
					}
					cp = i;
					ch = o.getPathHash();
					cw = 0;
				}
				int weight = getAdjustedWeight(o);
				cw += weight;
				totalWeight += weight;
			}

			// Sort by starting index to identify gaps later.
			Collections.sort(topPaths, (WeightedPath a,
					WeightedPath b) -> a.slice.beginIndex - b.slice.beginIndex);

			bytesPerUnit = 1;
			while (MAX_METER <= (totalWeight / bytesPerUnit)) {
				bytesPerUnit <<= 10;
			}
			return topPaths;
		}
	}

	static int getAdjustedWeight(ObjectToPack o) {
		// Edge objects and those with reused deltas do not need to be
		// compressed. For compression calculations, ignore their weights.
		if (o.isEdge() || o.doNotAttemptDelta()) {
			return 0;
		}
		return o.getWeight();
	}

	static final class WeightedPath implements Comparable<WeightedPath> {
		final long weight;
		final Slice slice;

		WeightedPath(long weight, Slice s) {
			this.weight = weight;
			this.slice = s;
		}

		@Override
		public int compareTo(WeightedPath o) {
			int cmp = Long.signum(weight - o.weight);
			if (cmp != 0) {
				return cmp;
			}
			return slice.beginIndex - o.slice.beginIndex;
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
	final LinkedList<Slice> slices;

	private ObjectReader or;
	private DeltaWindow dw;

	DeltaTask(Block b) {
		this.block = b;
		this.slices = new LinkedList<>();
	}

	void add(Slice s) {
		if (!slices.isEmpty()) {
			Slice last = slices.getLast();
			if (last.endIndex == s.beginIndex) {
				slices.removeLast();
				slices.add(new Slice(last.beginIndex, s.endIndex));
				return;
			}
		}
		slices.add(s);
	}

	/** {@inheritDoc} */
	@Override
	public Object call() throws Exception {
		or = block.templateReader.newReader();
		try {
			DeltaWindow w;
			for (;;) {
				synchronized (this) {
					if (slices.isEmpty()) {
						break;
					}
					w = initWindow(slices.removeFirst());
				}
				runWindow(w);
			}
			while ((w = block.stealWork(this)) != null) {
				runWindow(w);
			}
		} finally {
			block.pm.endWorker();
			or.close();
			or = null;
		}
		return null;
	}

	DeltaWindow initWindow(Slice s) {
		DeltaWindow w = new DeltaWindow(block.config, block.dc,
				or, block.pm, block.bytesPerUnit,
				block.list, s.beginIndex, s.endIndex);
		synchronized (this) {
			dw = w;
		}
		return w;
	}

	private void runWindow(DeltaWindow w) throws IOException {
		try {
			w.search();
		} finally {
			synchronized (this) {
				dw = null;
			}
		}
	}

	synchronized Slice remaining() {
		if (!slices.isEmpty()) {
			return slices.getLast();
		}
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
