/*
 * Copyright (C) 2010, Benjamin Muskalla <bmuskalla@eclipsesource.com>
 * Copyright (C) 2008, Manuel Woelker <manuel.woelker@gmail.com>
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
package org.eclipse.jgit.blame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.jgit.util.IntList;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Main structure for performing the blame algorithm
 *
 * Name and structure are lifted from the cgit blame implementation (cf.
 * builtin-blame.c)
 *
 *
 */
public class Scoreboard {
	LinkedList<BlameEntry> blameEntries = new LinkedList<BlameEntry>();

	Origin finalOrigin;

	private final IDiff diff;

	private final OriginWalk originWalk;

	/**
	 * @param finalObject
	 * @param diff
	 */
	public Scoreboard(Origin finalObject,
			IDiff diff) {
		super();
		try {
			originWalk = new OriginWalk(finalObject, false);
		} catch (Exception e) {
			throw new RuntimeException("Unable to create origin walk", e);
		}
		this.finalOrigin = finalObject;
		this.diff = diff;
		BlameEntry blameEntry = new BlameEntry();
		byte[] bytes = finalObject.getBytes();
		IntList lines = RawParseUtils.lineMap(bytes, 0, bytes.length);
		blameEntry.originalRange = new Range(0, lines.size() - 1);
		blameEntry.suspect = finalObject;
		blameEntry.suspectStart = 0;
		blameEntries.add(blameEntry);
	}

	/**
	 * @return list
	 */
	public List<BlameEntry> assingBlame() {
		try {

			while (true) {
				Origin origin = originWalk.next();
				HashSet<Origin> todos = new HashSet<Origin>();
				/* find one suspect to break down */
				boolean done = true;
				for (BlameEntry blameEntry : blameEntries) {
					if (blameEntry.suspect.equals(origin)) {
						todos.add(blameEntry.suspect);
						done = false;
					}
					if (!blameEntry.guilty) {
						// break;
						done = false;

					}
				}
				if (done) {
					break; // all done
				}
				if (origin == null) {
//					throw new RuntimeException(
//							"Internal error: not all guilty, but no more origins ");
					System.err.println("Internal error: not all guilty, but no more origins ");
					return blameEntries;
				}
				if (todos.isEmpty()) {
					continue;
				}
				for (Origin todo : todos) {
					passBlame(todo);
				}
				// Plead guilty for remaining entries
				List<BlameEntry> guilty = new ArrayList<BlameEntry>();
				for (BlameEntry blameEntry : blameEntries) {
					if (origin.equals(blameEntry.suspect)) {
						blameEntry.guilty = true;
						guilty.add(blameEntry);
					}
				}
			}
			return blameEntries;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void passBlame(Origin suspect) {
		// Simplified quite a lot
		Origin[] scapegoats = findScapegoats(suspect);
		for (Origin scapegoat : scapegoats) {
			if (suspect.getObjectId().equals(scapegoat.getObjectId())) {
				// file has not changed, pass all blame
				passWholeBlameToParent(suspect, scapegoat);
				return;
			}
		}
		for (Origin scapegoat : scapegoats) {
			passBlameToParent(suspect, scapegoat);
		}
	}

	private void passWholeBlameToParent(Origin target, Origin parent) {
		for (ListIterator<BlameEntry> it = blameEntries.listIterator(); it
				.hasNext();) {
			BlameEntry blameEntry = it.next();
			if (!blameEntry.suspect.equals(target)) {
				continue; // not what we are looking for
			}
			blameEntry.suspect = parent;
		}
	}

	private void passBlameToParent(Origin target, Origin parent) {
		byte[] parentBytes = parent.getBytes();
		byte[] targetBytes = target.getBytes();
		IntList parentLines = RawParseUtils.lineMap(parentBytes, 0,
				parentBytes.length);
		IntList targetLines = RawParseUtils.lineMap(targetBytes, 0,
				targetBytes.length);
		IDifference[] differences = diff.diff(parentBytes, parentLines,
				targetBytes, targetLines);

		List<CommonChunk> commonChunks = CommonChunk.computeCommonChunks(Arrays
				.asList(differences), parentLines.size() - 1, targetLines
				.size() - 1);
		for (CommonChunk commonChunk : commonChunks) {
			blameChunk(target, parent, commonChunk);
		}
	}

	private void blameChunk(Origin target, Origin parent,
			CommonChunk commonChunk) {
		for (ListIterator<BlameEntry> it = blameEntries.listIterator(); it
				.hasNext();) {
			BlameEntry blameEntry = it.next();
			if (blameEntry.guilty || !(blameEntry.suspect.equals(target))) {
				continue; // not what we are looking for
			}
			if (commonChunk.getBstart() + commonChunk.getLength() <= blameEntry.suspectStart) {
				continue; // common chunk ends before this entry starts
			}
			if (commonChunk.getBstart() < blameEntry.suspectStart
					+ blameEntry.originalRange.length) {
				List<BlameEntry> newBlameEntries = blameOverlap(blameEntry,
						parent, commonChunk);
				it.remove();
				for (BlameEntry newBlameEntry : newBlameEntries) {
					it.add(newBlameEntry);
				}
			}

		}
	}

	private List<BlameEntry> blameOverlap(BlameEntry blameEntry, Origin parent,
			CommonChunk commonChunk) {
		List<BlameEntry> split = splitOverlap(blameEntry, parent, commonChunk);
		return split;
	}

	// It is known that lines between tlno to same came from parent, and e
	// has an overlap with that range. it also is known that parent's
	// line plno corresponds to e's line tlno.
	//
	// <---- e ----->
	// <------>
	// <------------>
	// <------------>
	// <------------------>
	//
	// Split e into potentially three parts; before this chunk, the chunk
	// to be blamed for the parent, and after that portion.
	// [from builtin-blame.c split_overlap( )]
	//
	//
	// tlno = commonChunk.bstart
	// plno = commonChunk.astart
	// same = commonChunk.bstart+commonChunk.length

	static List<BlameEntry> splitOverlap(BlameEntry blameEntry, Origin parent,
			CommonChunk commonChunk) {
		List<BlameEntry> result = new LinkedList<BlameEntry>();
		// prechunk that can not be blamed on this parent
		BlameEntry split = new BlameEntry();
		if (blameEntry.suspectStart < commonChunk.getBstart()) {
			BlameEntry pre = new BlameEntry();
			pre.suspect = blameEntry.suspect;
			pre.suspectStart = blameEntry.suspectStart;
			pre.originalRange = new Range(blameEntry.originalRange.start,
					commonChunk.getBstart() - blameEntry.suspectStart);
			result.add(pre);
			split.originalRange = new Range(blameEntry.originalRange.start
					+ (commonChunk.getBstart() - blameEntry.suspectStart), 0);
			split.suspectStart = commonChunk.getAstart();
		} else {
			split.originalRange = new Range(blameEntry.originalRange.start, 0);
			split.suspectStart = commonChunk.getAstart()
					+ (blameEntry.suspectStart - commonChunk.getBstart());
		}

		split.suspect = parent;
		result.add(split);

		int same = commonChunk.getBstart() + commonChunk.getLength();
		// postchunk that can not be blamed on this parent
		int chunkEnd;
		if (same < blameEntry.suspectStart + blameEntry.originalRange.length) {
			BlameEntry post = new BlameEntry();
			post.suspect = blameEntry.suspect;
			int shiftStart = same - blameEntry.suspectStart;
			int numLines = blameEntry.suspectStart
					+ blameEntry.originalRange.length - same;
			post.originalRange = new Range(blameEntry.originalRange.start
					+ shiftStart, numLines);
			post.suspectStart = blameEntry.suspectStart + shiftStart;
			result.add(post);
			chunkEnd = post.originalRange.start;
		} else {
			chunkEnd = blameEntry.originalRange.start
					+ blameEntry.originalRange.length;
		}
		split.originalRange.length = chunkEnd - split.originalRange.start;

		// System.out.println("split "+ blameEntry);
		int sum = 0;
		for (BlameEntry each : result) {
			// System.out.println("\t-> "+ each);
			sum += each.originalRange.length;
		}
		if (sum != blameEntry.originalRange.length) {
			throw new RuntimeException("Internal error splitting blameentries");
		}
		return result;
	}

	/**
	 * get scapegoat origins for this origin, i.e. origins for parent commits
	 *
	 * a scapegoat in this context is a origin (for a parent commit) to that
	 * this origin can pass blame on
	 *
	 *
	 * Note: The main work is done by the OriginWalk
	 *
	 * @param origin
	 *            the origin for which to retrieve the scapegoats
	 *
	 * @return collection of scapegoat parent origins
	 */
	Origin[] findScapegoats(Origin origin) {
		return originWalk.getAncestorOrigins();
	}
}