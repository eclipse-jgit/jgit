/*
 * Copyright (c) 2026 Vector Informatik GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

/**
 * Tests for the {@link SequencerState} helper that persists cherry-pick /
 * revert sequences in a format compatible with native Git's
 * {@code $GIT_DIR/sequencer} directory.
 */
public class SequencerStateTest extends RepositoryTestCase {

	@Test
	public void testNotInProgressInitially() throws Exception {
		assertFalse(SequencerState.isInProgress(db));
	}

	@Test
	public void testBeginWriteReadEnd() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("a", "1\n");
			git.add().addFilepattern("a").call();
			RevCommit c1 = git.commit().setMessage("one").call();
			writeTrashFile("a", "1\n2\n");
			git.add().addFilepattern("a").call();
			RevCommit c2 = git.commit().setMessage("two").call();

			ObjectId head = db.resolve(org.eclipse.jgit.lib.Constants.HEAD);

			SequencerState.Options opts = new SequencerState.Options();
			opts.noCommit = true;
			opts.mainline = Integer.valueOf(1);
			opts.strategy = "ours"; //$NON-NLS-1$

			List<RevCommit> picks = Arrays.asList(c1, c2);

			SequencerState.begin(db, head, opts);
			assertEquals(head, SequencerState.readHead(db));
			File abortSafetyFile = new File(db.getDirectory(),
					Constants.SEQUENCER_HEAD_FILE);
			assertEquals(head.name(),
					JGitTestUtil.read(abortSafetyFile).trim());

			// Assert options were written correctly
			opts = SequencerState.readOpts(db);
			assertTrue(opts.noCommit);
			assertEquals(Integer.valueOf(1), opts.mainline);
			assertEquals("ours", opts.strategy); //$NON-NLS-1$

			SequencerState.writeTodo(db, SequencerState.Action.PICK, picks);
			assertTrue(SequencerState.isInProgress(db));
			try (RevWalk rw = new RevWalk(db)) {
				List<SequencerState.TodoLine> todo = SequencerState
						.readTodo(db);
				assertEquals(2, todo.size());
				assertEquals(SequencerState.Action.PICK, todo.get(0).action);
				assertEquals(c1.getId(), todo.get(0).commitId);
				assertEquals(c2.getId(), todo.get(1).commitId);
			}

			SequencerState.end(db);
			assertFalse(SequencerState.isInProgress(db));
			assertFalse(SequencerState.readHead(db) != null);
		}
	}
}
