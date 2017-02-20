/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com>
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

package org.eclipse.jgit.notes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Iterator;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NoteMapMergerTest extends RepositoryTestCase {
	private TestRepository<Repository> tr;

	private ObjectReader reader;

	private ObjectInserter inserter;

	private NoteMap noRoot;

	private NoteMap empty;

	private NoteMap map_a;

	private NoteMap map_a_b;

	private RevBlob noteAId;

	private String noteAContent;

	private RevBlob noteABlob;

	private RevBlob noteBId;

	private String noteBContent;

	private RevBlob noteBBlob;

	private RevCommit sampleTree_a;

	private RevCommit sampleTree_a_b;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		tr = new TestRepository<>(db);
		reader = db.newObjectReader();
		inserter = db.newObjectInserter();

		noRoot = NoteMap.newMap(null, reader);
		empty = NoteMap.newEmptyMap();

		noteAId = tr.blob("a");
		noteAContent = "noteAContent";
		noteABlob = tr.blob(noteAContent);
		sampleTree_a = tr.commit()
				.add(noteAId.name(), noteABlob)
				.create();
		tr.parseBody(sampleTree_a);
		map_a = NoteMap.read(reader, sampleTree_a);

		noteBId = tr.blob("b");
		noteBContent = "noteBContent";
		noteBBlob = tr.blob(noteBContent);
		sampleTree_a_b = tr.commit()
				.add(noteAId.name(), noteABlob)
				.add(noteBId.name(), noteBBlob)
				.create();
		tr.parseBody(sampleTree_a_b);
		map_a_b = NoteMap.read(reader, sampleTree_a_b);
	}

	@Override
	@After
	public void tearDown() throws Exception {
		reader.close();
		inserter.close();
		super.tearDown();
	}

	@Test
	public void testNoChange() throws IOException {
		NoteMapMerger merger = new NoteMapMerger(db, null, null);
		NoteMap result;

		assertEquals(0, countNotes(merger.merge(noRoot, noRoot, noRoot)));
		assertEquals(0, countNotes(merger.merge(empty, empty, empty)));

		result = merger.merge(map_a, map_a, map_a);
		assertEquals(1, countNotes(result));
		assertEquals(noteABlob, result.get(noteAId));
	}

	@Test
	public void testOursEqualsTheirs() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, null, null);
		NoteMap result;

		assertEquals(0, countNotes(merger.merge(empty, noRoot, noRoot)));
		assertEquals(0, countNotes(merger.merge(map_a, noRoot, noRoot)));

		assertEquals(0, countNotes(merger.merge(noRoot, empty, empty)));
		assertEquals(0, countNotes(merger.merge(map_a, empty, empty)));

		result = merger.merge(noRoot, map_a, map_a);
		assertEquals(1, countNotes(result));
		assertEquals(noteABlob, result.get(noteAId));

		result = merger.merge(empty, map_a, map_a);
		assertEquals(1, countNotes(result));
		assertEquals(noteABlob, result.get(noteAId));

		result = merger.merge(map_a_b, map_a, map_a);
		assertEquals(1, countNotes(result));
		assertEquals(noteABlob, result.get(noteAId));

		result = merger.merge(map_a, map_a_b, map_a_b);
		assertEquals(2, countNotes(result));
		assertEquals(noteABlob, result.get(noteAId));
		assertEquals(noteBBlob, result.get(noteBId));
	}

	@Test
	public void testBaseEqualsOurs() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, null, null);
		NoteMap result;

		assertEquals(0, countNotes(merger.merge(noRoot, noRoot, empty)));
		result = merger.merge(noRoot, noRoot, map_a);
		assertEquals(1, countNotes(result));
		assertEquals(noteABlob, result.get(noteAId));

		assertEquals(0, countNotes(merger.merge(empty, empty, noRoot)));
		result = merger.merge(empty, empty, map_a);
		assertEquals(1, countNotes(result));
		assertEquals(noteABlob, result.get(noteAId));

		assertEquals(0, countNotes(merger.merge(map_a, map_a, noRoot)));
		assertEquals(0, countNotes(merger.merge(map_a, map_a, empty)));
		result = merger.merge(map_a, map_a, map_a_b);
		assertEquals(2, countNotes(result));
		assertEquals(noteABlob, result.get(noteAId));
		assertEquals(noteBBlob, result.get(noteBId));
	}

	@Test
	public void testBaseEqualsTheirs() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, null, null);
		NoteMap result;

		assertEquals(0, countNotes(merger.merge(noRoot, empty, noRoot)));
		result = merger.merge(noRoot, map_a, noRoot);
		assertEquals(1, countNotes(result));
		assertEquals(noteABlob, result.get(noteAId));

		assertEquals(0, countNotes(merger.merge(empty, noRoot, empty)));
		result = merger.merge(empty, map_a, empty);
		assertEquals(1, countNotes(result));
		assertEquals(noteABlob, result.get(noteAId));

		assertEquals(0, countNotes(merger.merge(map_a, noRoot, map_a)));
		assertEquals(0, countNotes(merger.merge(map_a, empty, map_a)));
		result = merger.merge(map_a, map_a_b, map_a);
		assertEquals(2, countNotes(result));
		assertEquals(noteABlob, result.get(noteAId));
		assertEquals(noteBBlob, result.get(noteBId));
	}

	@Test
	public void testAddDifferentNotes() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, null, null);
		NoteMap result;

		NoteMap map_a_c = NoteMap.read(reader, sampleTree_a);
		RevBlob noteCId = tr.blob("c");
		RevBlob noteCBlob = tr.blob("noteCContent");
		map_a_c.set(noteCId, noteCBlob);
		map_a_c.writeTree(inserter);

		result = merger.merge(map_a, map_a_b, map_a_c);
		assertEquals(3, countNotes(result));
		assertEquals(noteABlob, result.get(noteAId));
		assertEquals(noteBBlob, result.get(noteBId));
		assertEquals(noteCBlob, result.get(noteCId));
	}

	@Test
	public void testAddSameNoteDifferentContent() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, new DefaultNoteMerger(),
				null);
		NoteMap result;

		NoteMap map_a_b1 = NoteMap.read(reader, sampleTree_a);
		String noteBContent1 = noteBContent + "change";
		RevBlob noteBBlob1 = tr.blob(noteBContent1);
		map_a_b1.set(noteBId, noteBBlob1);
		map_a_b1.writeTree(inserter);

		result = merger.merge(map_a, map_a_b, map_a_b1);
		assertEquals(2, countNotes(result));
		assertEquals(noteABlob, result.get(noteAId));
		assertEquals(tr.blob(noteBContent + noteBContent1), result.get(noteBId));
	}

	@Test
	public void testEditSameNoteDifferentContent() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, new DefaultNoteMerger(),
				null);
		NoteMap result;

		NoteMap map_a1 = NoteMap.read(reader, sampleTree_a);
		String noteAContent1 = noteAContent + "change1";
		RevBlob noteABlob1 = tr.blob(noteAContent1);
		map_a1.set(noteAId, noteABlob1);
		map_a1.writeTree(inserter);

		NoteMap map_a2 = NoteMap.read(reader, sampleTree_a);
		String noteAContent2 = noteAContent + "change2";
		RevBlob noteABlob2 = tr.blob(noteAContent2);
		map_a2.set(noteAId, noteABlob2);
		map_a2.writeTree(inserter);

		result = merger.merge(map_a, map_a1, map_a2);
		assertEquals(1, countNotes(result));
		assertEquals(tr.blob(noteAContent1 + noteAContent2),
				result.get(noteAId));
	}

	@Test
	public void testEditDifferentNotes() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, null, null);
		NoteMap result;

		NoteMap map_a1_b = NoteMap.read(reader, sampleTree_a_b);
		String noteAContent1 = noteAContent + "change";
		RevBlob noteABlob1 = tr.blob(noteAContent1);
		map_a1_b.set(noteAId, noteABlob1);
		map_a1_b.writeTree(inserter);

		NoteMap map_a_b1 = NoteMap.read(reader, sampleTree_a_b);
		String noteBContent1 = noteBContent + "change";
		RevBlob noteBBlob1 = tr.blob(noteBContent1);
		map_a_b1.set(noteBId, noteBBlob1);
		map_a_b1.writeTree(inserter);

		result = merger.merge(map_a_b, map_a1_b, map_a_b1);
		assertEquals(2, countNotes(result));
		assertEquals(noteABlob1, result.get(noteAId));
		assertEquals(noteBBlob1, result.get(noteBId));
	}

	@Test
	public void testDeleteDifferentNotes() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, null, null);

		NoteMap map_b = NoteMap.read(reader, sampleTree_a_b);
		map_b.set(noteAId, null); // delete note a
		map_b.writeTree(inserter);

		assertEquals(0, countNotes(merger.merge(map_a_b, map_a, map_b)));
	}

	@Test
	public void testEditDeleteConflict() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, new DefaultNoteMerger(),
				null);
		NoteMap result;

		NoteMap map_a_b1 = NoteMap.read(reader, sampleTree_a_b);
		String noteBContent1 = noteBContent + "change";
		RevBlob noteBBlob1 = tr.blob(noteBContent1);
		map_a_b1.set(noteBId, noteBBlob1);
		map_a_b1.writeTree(inserter);

		result = merger.merge(map_a_b, map_a_b1, map_a);
		assertEquals(2, countNotes(result));
		assertEquals(noteABlob, result.get(noteAId));
		assertEquals(noteBBlob1, result.get(noteBId));
	}

	@Test
	public void testLargeTreesWithoutConflict() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, null, null);
		NoteMap map1 = createLargeNoteMap("note_1_", "content_1_", 300, 0);
		NoteMap map2 = createLargeNoteMap("note_2_", "content_2_", 300, 0);

		NoteMap result = merger.merge(empty, map1, map2);
		assertEquals(600, countNotes(result));
		// check a few random notes
		assertEquals(tr.blob("content_1_59"), result.get(tr.blob("note_1_59")));
		assertEquals(tr.blob("content_2_10"), result.get(tr.blob("note_2_10")));
		assertEquals(tr.blob("content_2_99"), result.get(tr.blob("note_2_99")));
	}

	@Test
	public void testLargeTreesWithConflict() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, new DefaultNoteMerger(),
				null);
		NoteMap largeTree1 = createLargeNoteMap("note_1_", "content_1_", 300, 0);
		NoteMap largeTree2 = createLargeNoteMap("note_1_", "content_2_", 300, 0);

		NoteMap result = merger.merge(empty, largeTree1, largeTree2);
		assertEquals(300, countNotes(result));
		// check a few random notes
		assertEquals(tr.blob("content_1_59content_2_59"),
				result.get(tr.blob("note_1_59")));
		assertEquals(tr.blob("content_1_10content_2_10"),
				result.get(tr.blob("note_1_10")));
		assertEquals(tr.blob("content_1_99content_2_99"),
				result.get(tr.blob("note_1_99")));
	}

	private NoteMap createLargeNoteMap(String noteNamePrefix,
			String noteContentPrefix, int notesCount, int firstIndex)
			throws Exception {
		NoteMap result = NoteMap.newEmptyMap();
		for (int i = 0; i < notesCount; i++) {
			result.set(tr.blob(noteNamePrefix + (firstIndex + i)),
					tr.blob(noteContentPrefix + (firstIndex + i)));
		}
		result.writeTree(inserter);
		return result;
	}

	@Test
	public void testFanoutAndLeafWithoutConflict() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, null, null);

		NoteMap largeTree = createLargeNoteMap("note_1_", "content_1_", 300, 0);
		NoteMap result = merger.merge(map_a, map_a_b, largeTree);
		assertEquals(301, countNotes(result));
	}

	@Test
	public void testFanoutAndLeafWitConflict() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, new DefaultNoteMerger(),
				null);

		NoteMap largeTree_b1 = createLargeNoteMap("note_1_", "content_1_", 300,
				0);
		String noteBContent1 = noteBContent + "change";
		largeTree_b1.set(noteBId, tr.blob(noteBContent1));
		largeTree_b1.writeTree(inserter);

		NoteMap result = merger.merge(map_a, map_a_b, largeTree_b1);
		assertEquals(301, countNotes(result));
		assertEquals(tr.blob(noteBContent + noteBContent1), result.get(noteBId));
	}

	@Test
	public void testCollapseFanoutAfterMerge() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, null, null);

		NoteMap largeTree = createLargeNoteMap("note_", "content_", 257, 0);
		assertTrue(largeTree.getRoot() instanceof FanoutBucket);
		NoteMap deleteFirstHundredNotes = createLargeNoteMap("note_", "content_", 157,
				100);
		NoteMap deleteLastHundredNotes = createLargeNoteMap("note_",
				"content_", 157, 0);
		NoteMap result = merger.merge(largeTree, deleteFirstHundredNotes,
				deleteLastHundredNotes);
		assertEquals(57, countNotes(result));
		assertTrue(result.getRoot() instanceof LeafBucket);
	}

	@Test
	public void testNonNotesWithoutNonNoteConflict() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, null,
				MergeStrategy.RESOLVE);
		RevCommit treeWithNonNotes =
			tr.commit()
				.add(noteAId.name(), noteABlob) // this is a note
				.add("a.txt", tr.blob("content of a.txt")) // this is a non-note
				.create();
		tr.parseBody(treeWithNonNotes);
		NoteMap base = NoteMap.read(reader, treeWithNonNotes);

		treeWithNonNotes =
			tr.commit()
				.add(noteAId.name(), noteABlob)
				.add("a.txt", tr.blob("content of a.txt"))
				.add("b.txt", tr.blob("content of b.txt"))
				.create();
		tr.parseBody(treeWithNonNotes);
		NoteMap ours = NoteMap.read(reader, treeWithNonNotes);

		treeWithNonNotes =
			tr.commit()
				.add(noteAId.name(), noteABlob)
				.add("a.txt", tr.blob("content of a.txt"))
				.add("c.txt", tr.blob("content of c.txt"))
				.create();
		tr.parseBody(treeWithNonNotes);
		NoteMap theirs = NoteMap.read(reader, treeWithNonNotes);

		NoteMap result = merger.merge(base, ours, theirs);
		assertEquals(3, countNonNotes(result));
	}

	@Test
	public void testNonNotesWithNonNoteConflict() throws Exception {
		NoteMapMerger merger = new NoteMapMerger(db, null,
				MergeStrategy.RESOLVE);
		RevCommit treeWithNonNotes =
			tr.commit()
				.add(noteAId.name(), noteABlob) // this is a note
				.add("a.txt", tr.blob("content of a.txt")) // this is a non-note
				.create();
		tr.parseBody(treeWithNonNotes);
		NoteMap base = NoteMap.read(reader, treeWithNonNotes);

		treeWithNonNotes =
			tr.commit()
				.add(noteAId.name(), noteABlob)
				.add("a.txt", tr.blob("change 1"))
				.create();
		tr.parseBody(treeWithNonNotes);
		NoteMap ours = NoteMap.read(reader, treeWithNonNotes);

		treeWithNonNotes =
			tr.commit()
				.add(noteAId.name(), noteABlob)
				.add("a.txt", tr.blob("change 2"))
				.create();
		tr.parseBody(treeWithNonNotes);
		NoteMap theirs = NoteMap.read(reader, treeWithNonNotes);

		try {
			merger.merge(base, ours, theirs);
			fail("NotesMergeConflictException was expected");
		} catch (NotesMergeConflictException e) {
			// expected
		}
	}

	private static int countNotes(NoteMap map) {
		int c = 0;
		Iterator<Note> it = map.iterator();
		while (it.hasNext()) {
			it.next();
			c++;
		}
		return c;
	}

	private static int countNonNotes(NoteMap map) {
		int c = 0;
		NonNoteEntry nonNotes = map.getRoot().nonNotes;
		while (nonNotes != null) {
			c++;
			nonNotes = nonNotes.next;
		}
		return c;
	}
}
