/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.notes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DefaultNoteMergerTest extends RepositoryTestCase {

	private TestRepository<Repository> tr;

	private ObjectReader reader;

	private ObjectInserter inserter;

	private DefaultNoteMerger merger;

	private Note baseNote;

	private RevBlob noteOn;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		tr = new TestRepository<>(db);
		reader = db.newObjectReader();
		inserter = db.newObjectInserter();
		merger = new DefaultNoteMerger();
		noteOn = tr.blob("a");
		baseNote = newNote("data");
	}

	@Override
	@After
	public void tearDown() throws Exception {
		reader.close();
		inserter.close();
		super.tearDown();
	}

	@Test
	public void testDeleteDelete() throws Exception {
		assertNull(merger.merge(baseNote, null, null, null, null));
	}

	@Test
	public void testEditDelete() throws Exception {
		Note edit = newNote("edit");
		assertSame(merger.merge(baseNote, edit, null, null, null), edit);
		assertSame(merger.merge(baseNote, null, edit, null, null), edit);
	}

	@Test
	public void testIdenticalEdit() throws Exception {
		Note edit = newNote("edit");
		assertSame(merger.merge(baseNote, edit, edit, null, null), edit);
	}

	@Test
	public void testEditEdit() throws Exception {
		Note edit1 = newNote("edit1");
		Note edit2 = newNote("edit2");

		Note result = merger.merge(baseNote, edit1, edit2, reader, inserter);
		assertEquals(result, noteOn); // same note
		assertEquals(result.getData(), tr.blob("edit1edit2"));

		result = merger.merge(baseNote, edit2, edit1, reader, inserter);
		assertEquals(result, noteOn); // same note
		assertEquals(result.getData(), tr.blob("edit2edit1"));
	}

	@Test
	public void testIdenticalAdd() throws Exception {
		Note add = newNote("add");
		assertSame(merger.merge(null, add, add, null, null), add);
	}

	@Test
	public void testAddAdd() throws Exception {
		Note add1 = newNote("add1");
		Note add2 = newNote("add2");

		Note result = merger.merge(null, add1, add2, reader, inserter);
		assertEquals(result, noteOn); // same note
		assertEquals(result.getData(), tr.blob("add1add2"));

		result = merger.merge(null, add2, add1, reader, inserter);
		assertEquals(result, noteOn); // same note
		assertEquals(result.getData(), tr.blob("add2add1"));
	}

	private Note newNote(String data) throws Exception {
		return new Note(noteOn, tr.blob(data));
	}
}
