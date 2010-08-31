/*
 * Copyright (C) 2010, Benjamin Muskalla <bmuskalla@eclipsesource.com>
 * Copyright (C) 2009, Manuel Woelker <manuel.woelker+github@gmail.com>
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

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

public class SplitOverlapTest extends TestCase {

	public void testPerfectMatch() throws Exception {
		// <ttttttttt> < entry (t = target origin, i.e. current suspect)
		// <xxxxxxxxx> < common chunk
		// <ppppppppp> < result (p = parent origin, i.e. potential suspect)
		Origin parent = new Origin(null, null, "target");
		Origin target = new Origin(null, null, "target");

		BlameEntry blameEntry = new BlameEntry();
		blameEntry.originalRange = new Range(17, 4);
		blameEntry.suspectStart = 50;
		blameEntry.suspect = target;
		List<BlameEntry> actual = Scoreboard.splitOverlap(blameEntry, parent,
				new CommonChunk(70, 50, 4));
		BlameEntry expected1 = new BlameEntry();
		expected1.originalRange = new Range(17, 4);
		expected1.suspectStart = 70;
		expected1.suspect = parent;
		List<BlameEntry> expected = Arrays.asList(expected1);
		assertEquals(expected, actual);
	}

	public void testSplitMiddle() throws Exception {
		// <ttttttttt> < entry
		// ---<xxx>--- < common chunk
		// <t><ppp><t> < result
		Origin parent = new Origin(null, null, "target");
		Origin target = new Origin(null, null, "target");

		BlameEntry blameEntry = new BlameEntry();
		blameEntry.originalRange = new Range(17, 12);
		blameEntry.suspectStart = 50;
		blameEntry.suspect = target;
		List<BlameEntry> actual = Scoreboard.splitOverlap(blameEntry, parent,
				new CommonChunk(73, 53, 4));
		BlameEntry expected1 = new BlameEntry();
		expected1.originalRange = new Range(17, 3);
		expected1.suspectStart = 50;
		expected1.suspect = target;
		BlameEntry expected2 = new BlameEntry();
		expected2.originalRange = new Range(20, 4);
		expected2.suspectStart = 73;
		expected2.suspect = parent;
		BlameEntry expected3 = new BlameEntry();
		expected3.originalRange = new Range(24, 5);
		expected3.suspectStart = 57;
		expected3.suspect = target;
		List<BlameEntry> expected = Arrays.asList(expected1, expected2,
				expected3);
		assertEquals(expected, actual);
	}

	public void testSplitCommonChunkBigger() throws Exception {
		// ---<ttt>--- < entry
		// <xxxxxxxxx> < common chunk
		// ---<ppp>--- < result
		Origin parent = new Origin(null, null, "target");
		Origin target = new Origin(null, null, "target");

		BlameEntry blameEntry = new BlameEntry();
		blameEntry.originalRange = new Range(17, 12);
		blameEntry.suspectStart = 50;
		blameEntry.suspect = target;
		List<BlameEntry> actual = Scoreboard.splitOverlap(blameEntry, parent,
				new CommonChunk(73, 40, 30));
		BlameEntry expected1 = new BlameEntry();
		expected1.originalRange = new Range(17, 12);
		expected1.suspectStart = 83;
		expected1.suspect = parent;
		List<BlameEntry> expected = Arrays.asList(expected1);
		assertEquals(expected, actual);
	}

	public void testSplitCommonChunkStartsBefore() throws Exception {
		// ---<tttttt> < entry
		// <xxxxxx>--- < common chunk
		// ---<ppp><t> < result
		Origin parent = new Origin(null, null, "target");
		Origin target = new Origin(null, null, "target");

		BlameEntry blameEntry = new BlameEntry();
		blameEntry.originalRange = new Range(17, 12);
		blameEntry.suspectStart = 50;
		blameEntry.suspect = target;
		List<BlameEntry> actual = Scoreboard.splitOverlap(blameEntry, parent,
				new CommonChunk(73, 40, 20));
		BlameEntry expected1 = new BlameEntry();
		expected1.originalRange = new Range(17, 10);
		expected1.suspectStart = 83;
		expected1.suspect = parent;
		BlameEntry expected2 = new BlameEntry();
		expected2.originalRange = new Range(27, 2);
		expected2.suspectStart = 60;
		expected2.suspect = target;
		List<BlameEntry> expected = Arrays.asList(expected1, expected2);
		assertEquals(expected, actual);
	}

	public void testSplitCommonChunkEndsAfter() throws Exception {
		// <tttttt>--- < entry
		// ---<xxxxxx> < common chunk
		// <t><ppp>--- < result
		Origin parent = new Origin(null, null, "target");
		Origin target = new Origin(null, null, "target");

		BlameEntry blameEntry = new BlameEntry();
		blameEntry.originalRange = new Range(17, 12);
		blameEntry.suspectStart = 50;
		blameEntry.suspect = target;
		List<BlameEntry> actual = Scoreboard.splitOverlap(blameEntry, parent,
				new CommonChunk(73, 55, 20));
		BlameEntry expected1 = new BlameEntry();
		expected1.originalRange = new Range(17, 5);
		expected1.suspectStart = 50;
		expected1.suspect = target;
		BlameEntry expected2 = new BlameEntry();
		expected2.originalRange = new Range(22, 7);
		expected2.suspectStart = 73;
		expected2.suspect = parent;
		List<BlameEntry> expected = Arrays.asList(expected1, expected2);
		assertEquals(expected, actual);
	}

}
