/*
 * Copyright (C) 2022, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diff;

import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;

public abstract class AbstractRenameDetectionTestCase
		extends RepositoryTestCase {

	protected static final String PATH_A = "src/A";

	protected static final String PATH_B = "src/B";

	protected static final String PATH_H = "src/H";

	protected static final String PATH_Q = "src/Q";

	protected TestRepository<Repository> testDb;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		testDb = new TestRepository<>(db);
	}

	protected ObjectId blob(String content) throws Exception {
		return testDb.blob(content).copy();
	}

	protected static void assertRename(DiffEntry o, DiffEntry n, int score,
			DiffEntry rename) {
		assertEquals(ChangeType.RENAME, rename.getChangeType());

		assertEquals(o.getOldPath(), rename.getOldPath());
		assertEquals(n.getNewPath(), rename.getNewPath());

		assertEquals(o.getOldMode(), rename.getOldMode());
		assertEquals(n.getNewMode(), rename.getNewMode());

		assertEquals(o.getOldId(), rename.getOldId());
		assertEquals(n.getNewId(), rename.getNewId());

		assertEquals(score, rename.getScore());
	}

	protected static void assertCopy(DiffEntry o, DiffEntry n, int score,
			DiffEntry copy) {
		assertEquals(ChangeType.COPY, copy.getChangeType());

		assertEquals(o.getOldPath(), copy.getOldPath());
		assertEquals(n.getNewPath(), copy.getNewPath());

		assertEquals(o.getOldMode(), copy.getOldMode());
		assertEquals(n.getNewMode(), copy.getNewMode());

		assertEquals(o.getOldId(), copy.getOldId());
		assertEquals(n.getNewId(), copy.getNewId());

		assertEquals(score, copy.getScore());
	}

	protected static void assertAdd(String newName, ObjectId newId,
			FileMode newMode, DiffEntry add) {
		assertEquals(DiffEntry.DEV_NULL, add.oldPath);
		assertEquals(DiffEntry.A_ZERO, add.oldId);
		assertEquals(FileMode.MISSING, add.oldMode);
		assertEquals(ChangeType.ADD, add.changeType);
		assertEquals(newName, add.newPath);
		assertEquals(AbbreviatedObjectId.fromObjectId(newId), add.newId);
		assertEquals(newMode, add.newMode);
	}

	protected static void assertDelete(String oldName, ObjectId oldId,
			FileMode oldMode, DiffEntry delete) {
		assertEquals(DiffEntry.DEV_NULL, delete.newPath);
		assertEquals(DiffEntry.A_ZERO, delete.newId);
		assertEquals(FileMode.MISSING, delete.newMode);
		assertEquals(ChangeType.DELETE, delete.changeType);
		assertEquals(oldName, delete.oldPath);
		assertEquals(AbbreviatedObjectId.fromObjectId(oldId), delete.oldId);
		assertEquals(oldMode, delete.oldMode);
	}
}
