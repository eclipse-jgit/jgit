/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2009, Christian Halstrick, Matthias Sohn, SAP AG
 * Copyright (C) 2009-2010, Google Inc.
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

package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;

public class ReflogConfigTest extends RepositoryTestCase {
	@Test
	public void testlogAllRefUpdates() throws Exception {
		long commitTime = 1154236443000L;
		int tz = -4 * 60;

		// check that there are no entries in the reflog and turn off writing
		// reflogs
		assertEquals(0,
				db.getReflogReader(Constants.HEAD).getReverseEntries().size());
		final FileBasedConfig cfg = db.getConfig();
		cfg.setBoolean("core", null, "logallrefupdates", false);
		cfg.save();

		// do one commit and check that reflog size is 0: no reflogs should be
		// written
		commit("A Commit\n", commitTime, tz);
		commitTime += 60 * 1000;
		assertTrue("Reflog for HEAD still contain no entry", db
				.getReflogReader(Constants.HEAD).getReverseEntries().isEmpty());

		// set the logAllRefUpdates parameter to true and check it
		cfg.setBoolean("core", null, "logallrefupdates", true);
		cfg.save();
		assertTrue(cfg.get(CoreConfig.KEY).isLogAllRefUpdates());

		// do one commit and check that reflog size is increased to 1
		commit("A Commit\n", commitTime, tz);
		commitTime += 60 * 1000;
		assertTrue("Reflog for HEAD should contain one entry",
				db.getReflogReader(Constants.HEAD).getReverseEntries()
						.size() == 1);

		// set the logAllRefUpdates parameter to false and check it
		cfg.setBoolean("core", null, "logallrefupdates", false);
		cfg.save();
		assertFalse(cfg.get(CoreConfig.KEY).isLogAllRefUpdates());

		// do one commit and check that reflog size is 2
		commit("A Commit\n", commitTime, tz);
		assertTrue("Reflog for HEAD should contain two entries",
				db.getReflogReader(Constants.HEAD).getReverseEntries()
						.size() == 2);
	}

	private void commit(String commitMsg, long commitTime, int tz)
			throws IOException {
		final CommitBuilder commit = new CommitBuilder();
		commit.setAuthor(new PersonIdent(author, commitTime, tz));
		commit.setCommitter(new PersonIdent(committer, commitTime, tz));
		commit.setMessage(commitMsg);
		ObjectId id;
		try (ObjectInserter inserter = db.newObjectInserter()) {
			commit.setTreeId(inserter.insert(new TreeFormatter()));
			id = inserter.insert(commit);
			inserter.flush();
		}

		int nl = commitMsg.indexOf('\n');
		final RefUpdate ru = db.updateRef(Constants.HEAD);
		ru.setNewObjectId(id);
		ru.setRefLogMessage(
				"commit : "
						+ ((nl == -1) ? commitMsg : commitMsg.substring(0, nl)),
				false);
		ru.forceUpdate();
	}
}
