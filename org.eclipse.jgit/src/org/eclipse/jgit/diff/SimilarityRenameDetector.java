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

package org.eclipse.jgit.diff;

import static org.eclipse.jgit.diff.DiffEntry.Side.NEW;
import static org.eclipse.jgit.diff.DiffEntry.Side.OLD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.SimilarityIndex.TableFullException;
import org.eclipse.jgit.errors.CancelledException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;

class SimilarityRenameDetector {
	/**
	 * Number of bits we need to express an index into src or dst list.
	 * <p>
	 * This must be 28, giving us a limit of 2^28 entries in either list, which
	 * is an insane limit of 536,870,912 file names being considered in a single
	 * rename pass. The other 8 bits are used to store the score, while staying
	 * under 127 so the long doesn't go negative.
	 */
	private static final int BITS_PER_INDEX = 28;

	private static final int INDEX_MASK = (1 << BITS_PER_INDEX) - 1;

	private static final int SCORE_SHIFT = 2 * BITS_PER_INDEX;

	private ContentSource.Pair reader;

	/**
	 * All sources to consider for copies or renames.
	 * <p>
	 * A source is typically a {@link ChangeType#DELETE} change, but could be
	 * another type when trying to perform copy detection concurrently with
	 * rename detection.
	 */
	private List<DiffEntry> srcs;

	/**
	 * All destinations to consider looking for a rename.
	 * <p>
	 * A destination is typically an {@link ChangeType#ADD}, as the name has
	 * just come into existence, and we want to discover where its initial
	 * content came from.
	 */
	private List<DiffEntry> dsts;

	/**
	 * Matrix of all examined file pairs, and their scores.
	 * <p>
	 * The upper 8 bits of each long stores the score, but the score is bounded
	 * to be in the range (0, 128] so that the highest bit is never set, and all
	 * entries are therefore positive.
	 * <p>
	 * List indexes to an element of {@link #srcs} and {@link #dsts} are encoded
	 * as the lower two groups of 28 bits, respectively, but the encoding is
	 * inverted, so that 0 is expressed as {@code (1 << 28) - 1}. This sorts
	 * lower list indices later in the matrix, giving precedence to files whose
	 * names sort earlier in the tree.
	 */
	private long[] matrix;

	/** Score a pair must exceed to be considered a rename. */
	private int renameScore = 60;

	/** Set if any {@link SimilarityIndex.TableFullException} occurs. */
	private boolean tableOverflow;

	private List<DiffEntry> out;

	SimilarityRenameDetector(ContentSource.Pair reader, List<DiffEntry> srcs,
			List<DiffEntry> dsts) {
		this.reader = reader;
		this.srcs = srcs;
		this.dsts = dsts;
	}

	void setRenameScore(int score) {
		renameScore = score;
	}

	void compute(ProgressMonitor pm) throws IOException, CancelledException {
		if (pm == null)
			pm = NullProgressMonitor.INSTANCE;

		pm.beginTask(JGitText.get().renamesFindingByContent, //
				2 * srcs.size() * dsts.size());

		int mNext = buildMatrix(pm);
		out = new ArrayList<>(Math.min(mNext, dsts.size()));

		// Match rename pairs on a first come, first serve basis until
		// we have looked at everything that is above our minimum score.
		//
		for (--mNext; mNext >= 0; mNext--) {
			if (pm.isCancelled()) {
				// TODO(ms): use org.eclipse.jgit.api.errors.CanceledException
				// in next major version
				throw new CancelledException(JGitText.get().renameCancelled);
			}
			long ent = matrix[mNext];
			int sIdx = srcFile(ent);
			int dIdx = dstFile(ent);
			DiffEntry s = srcs.get(sIdx);
			DiffEntry d = dsts.get(dIdx);

			if (d == null) {
				pm.update(1);
				continue; // was already matched earlier
			}

			ChangeType type;
			if (s.changeType == ChangeType.DELETE) {
				// First use of this source file. Tag it as a rename so we
				// later know it is already been used as a rename, other
				// matches (if any) will claim themselves as copies instead.
				//
				s.changeType = ChangeType.RENAME;
				type = ChangeType.RENAME;
			} else {
				type = ChangeType.COPY;
			}

			out.add(DiffEntry.pair(type, s, d, score(ent)));
			dsts.set(dIdx, null); // Claim the destination was matched.
			pm.update(1);
		}

		srcs = compactSrcList(srcs);
		dsts = compactDstList(dsts);
		pm.endTask();
	}

	List<DiffEntry> getMatches() {
		return out;
	}

	List<DiffEntry> getLeftOverSources() {
		return srcs;
	}

	List<DiffEntry> getLeftOverDestinations() {
		return dsts;
	}

	boolean isTableOverflow() {
		return tableOverflow;
	}

	private static List<DiffEntry> compactSrcList(List<DiffEntry> in) {
		ArrayList<DiffEntry> r = new ArrayList<>(in.size());
		for (DiffEntry e : in) {
			if (e.changeType == ChangeType.DELETE)
				r.add(e);
		}
		return r;
	}

	private static List<DiffEntry> compactDstList(List<DiffEntry> in) {
		ArrayList<DiffEntry> r = new ArrayList<>(in.size());
		for (DiffEntry e : in) {
			if (e != null)
				r.add(e);
		}
		return r;
	}

	private int buildMatrix(ProgressMonitor pm)
			throws IOException, CancelledException {
		// Allocate for the worst-case scenario where every pair has a
		// score that we need to consider. We might not need that many.
		//
		matrix = new long[srcs.size() * dsts.size()];

		long[] srcSizes = new long[srcs.size()];
		long[] dstSizes = new long[dsts.size()];
		BitSet dstTooLarge = null;

		// Consider each pair of files, if the score is above the minimum
		// threshold we need record that scoring in the matrix so we can
		// later find the best matches.
		//
		int mNext = 0;
		SRC: for (int srcIdx = 0; srcIdx < srcs.size(); srcIdx++) {
			DiffEntry srcEnt = srcs.get(srcIdx);
			if (!isFile(srcEnt.oldMode)) {
				pm.update(dsts.size());
				continue;
			}

			SimilarityIndex s = null;

			for (int dstIdx = 0; dstIdx < dsts.size(); dstIdx++) {
				if (pm.isCancelled()) {
					// TODO(ms): use
					// org.eclipse.jgit.api.errors.CanceledException in next
					// major version
					throw new CancelledException(
							JGitText.get().renameCancelled);
				}

				DiffEntry dstEnt = dsts.get(dstIdx);

				if (!isFile(dstEnt.newMode)) {
					pm.update(1);
					continue;
				}

				if (!RenameDetector.sameType(srcEnt.oldMode, dstEnt.newMode)) {
					pm.update(1);
					continue;
				}

				if (dstTooLarge != null && dstTooLarge.get(dstIdx)) {
					pm.update(1);
					continue;
				}

				long srcSize = srcSizes[srcIdx];
				if (srcSize == 0) {
					srcSize = size(OLD, srcEnt) + 1;
					srcSizes[srcIdx] = srcSize;
				}

				long dstSize = dstSizes[dstIdx];
				if (dstSize == 0) {
					dstSize = size(NEW, dstEnt) + 1;
					dstSizes[dstIdx] = dstSize;
				}

				long max = Math.max(srcSize, dstSize);
				long min = Math.min(srcSize, dstSize);
				if (min * 100 / max < renameScore) {
					// Cannot possibly match, as the file sizes are so different
					pm.update(1);
					continue;
				}

				if (s == null) {
					try {
						s = hash(OLD, srcEnt);
					} catch (TableFullException tableFull) {
						tableOverflow = true;
						continue SRC;
					}
				}

				SimilarityIndex d;
				try {
					d = hash(NEW, dstEnt);
				} catch (TableFullException tableFull) {
					if (dstTooLarge == null)
						dstTooLarge = new BitSet(dsts.size());
					dstTooLarge.set(dstIdx);
					tableOverflow = true;
					pm.update(1);
					continue;
				}

				int contentScore = s.score(d, 10000);

				// nameScore returns a value between 0 and 100, but we want it
				// to be in the same range as the content score. This allows it
				// to be dropped into the pretty formula for the final score.
				int nameScore = nameScore(srcEnt.oldPath, dstEnt.newPath) * 100;

				int score = (contentScore * 99 + nameScore * 1) / 10000;

				if (score < renameScore) {
					pm.update(1);
					continue;
				}

				matrix[mNext++] = encode(score, srcIdx, dstIdx);
				pm.update(1);
			}
		}

		// Sort everything in the range we populated, which might be the
		// entire matrix, or just a smaller slice if we had some bad low
		// scoring pairs.
		//
		Arrays.sort(matrix, 0, mNext);
		return mNext;
	}

	static int nameScore(String a, String b) {
	    int aDirLen = a.lastIndexOf("/") + 1; //$NON-NLS-1$
	    int bDirLen = b.lastIndexOf("/") + 1; //$NON-NLS-1$

	    int dirMin = Math.min(aDirLen, bDirLen);
	    int dirMax = Math.max(aDirLen, bDirLen);

	    final int dirScoreLtr;
	    final int dirScoreRtl;

		if (dirMax == 0) {
			dirScoreLtr = 100;
			dirScoreRtl = 100;
		} else {
			int dirSim = 0;
			for (; dirSim < dirMin; dirSim++) {
				if (a.charAt(dirSim) != b.charAt(dirSim))
					break;
			}
			dirScoreLtr = (dirSim * 100) / dirMax;

			if (dirScoreLtr == 100) {
				dirScoreRtl = 100;
			} else {
				for (dirSim = 0; dirSim < dirMin; dirSim++) {
					if (a.charAt(aDirLen - 1 - dirSim) != b.charAt(bDirLen - 1
							- dirSim))
						break;
				}
				dirScoreRtl = (dirSim * 100) / dirMax;
			}
		}

		int fileMin = Math.min(a.length() - aDirLen, b.length() - bDirLen);
		int fileMax = Math.max(a.length() - aDirLen, b.length() - bDirLen);

		int fileSim = 0;
		for (; fileSim < fileMin; fileSim++) {
			if (a.charAt(a.length() - 1 - fileSim) != b.charAt(b.length() - 1
					- fileSim))
				break;
		}
		int fileScore = (fileSim * 100) / fileMax;

		return (((dirScoreLtr + dirScoreRtl) * 25) + (fileScore * 50)) / 100;
	}

	private SimilarityIndex hash(DiffEntry.Side side, DiffEntry ent)
			throws IOException, TableFullException {
		SimilarityIndex r = new SimilarityIndex();
		r.hash(reader.open(side, ent));
		r.sort();
		return r;
	}

	private long size(DiffEntry.Side side, DiffEntry ent) throws IOException {
		return reader.size(side, ent);
	}

	private static int score(long value) {
		return (int) (value >>> SCORE_SHIFT);
	}

	static int srcFile(long value) {
		return decodeFile(((int) (value >>> BITS_PER_INDEX)) & INDEX_MASK);
	}

	static int dstFile(long value) {
		return decodeFile(((int) value) & INDEX_MASK);
	}

	static long encode(int score, int srcIdx, int dstIdx) {
		return (((long) score) << SCORE_SHIFT) //
				| (encodeFile(srcIdx) << BITS_PER_INDEX) //
				| encodeFile(dstIdx);
	}

	private static long encodeFile(int idx) {
		// We invert the index so that the first file in the list sorts
		// later in the table. This permits us to break ties favoring
		// earlier names over later ones.
		//
		return INDEX_MASK - idx;
	}

	private static int decodeFile(int v) {
		return INDEX_MASK - v;
	}

	private static boolean isFile(FileMode mode) {
		return (mode.getBits() & FileMode.TYPE_MASK) == FileMode.TYPE_FILE;
	}
}
