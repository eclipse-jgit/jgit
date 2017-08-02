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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.CoreConfig.LogAllRefUpdates;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;

public class ReflogConfigTest extends RepositoryTestCase {
	private static final String CORE = ConfigConstants.CONFIG_CORE_SECTION;
	private static final String LOGALL =
			ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES;

	@Test
	public void testlogAllRefUpdates() throws Exception {
		long commitTime = 1154236443000L;
		int tz = -4 * 60;

		// check that there are no entries in the reflog and turn off writing
		// reflogs
		assertEquals(0, db.getReflogReader(Constants.HEAD).getReverseEntries().size());
		final FileBasedConfig cfg = db.getConfig();
		cfg.setBoolean("core", null, "logallrefupdates", false);
		cfg.save();

		// do one commit and check that reflog size is 0: no reflogs should be
		// written
		commit("A Commit\n", commitTime, tz);
		commitTime += 60 * 1000;
		assertTrue(
				"Reflog for HEAD still contain no entry",
				db.getReflogReader(Constants.HEAD).getReverseEntries().size() == 0);

		// set the logAllRefUpdates parameter to true and check it
		cfg.setBoolean("core", null, "logallrefupdates", true);
		cfg.save();
		assertEquals(LogAllRefUpdates.TRUE,
				cfg.get(CoreConfig.key(db)).getLogAllRefUpdates());

		// do one commit and check that reflog size is increased to 1
		commit("A Commit\n", commitTime, tz);
		commitTime += 60 * 1000;
		assertTrue(
				"Reflog for HEAD should contain one entry",
				db.getReflogReader(Constants.HEAD).getReverseEntries().size() == 1);

		// set the logAllRefUpdates parameter to false and check it
		cfg.setBoolean("core", null, "logallrefupdates", false);
		cfg.save();
		assertEquals(LogAllRefUpdates.FALSE,
				cfg.get(CoreConfig.key(db)).getLogAllRefUpdates());

		// do one commit and check that reflog size is 2
		commit("A Commit\n", commitTime, tz);
		assertTrue(
				"Reflog for HEAD should contain two entries",
				db.getReflogReader(Constants.HEAD).getReverseEntries().size() == 2);
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testlogAllRefUpdatesRespectsWhetherRepoIsBare()
			throws Exception {
		Repository bareDb = createBareRepository();

		// This test is just testing what happens when the value is unset.
		db.getConfig().unset(CORE, null, LOGALL);
		bareDb.getConfig().unset(CORE, null, LOGALL);

		// Deprecated KEY always defaults to true, regardless of bareness.
		assertEquals(LogAllRefUpdates.TRUE,
				db.getConfig().get(CoreConfig.KEY).getLogAllRefUpdates());
		assertEquals(LogAllRefUpdates.TRUE,
				bareDb.getConfig().get(CoreConfig.KEY).getLogAllRefUpdates());

		// key(Repository) defaults to !bare.
		assertEquals(LogAllRefUpdates.TRUE,
				db.getConfig().get(CoreConfig.key(db)).getLogAllRefUpdates());
		assertTrue(db.getConfig().get(CoreConfig.key(db)).isLogAllRefUpdates());
		assertEquals(LogAllRefUpdates.FALSE,
				bareDb.getConfig().get(CoreConfig.key(bareDb)).getLogAllRefUpdates());
		assertFalse(
				bareDb.getConfig().get(CoreConfig.key(bareDb)).isLogAllRefUpdates());

		// Overriding default always works.
		db.getConfig().setBoolean(CORE, null, LOGALL, true);
		bareDb.getConfig().setBoolean(CORE, null, LOGALL, true);
		assertEquals(LogAllRefUpdates.TRUE,
				db.getConfig().get(CoreConfig.key(db)).getLogAllRefUpdates());
		assertTrue(db.getConfig().get(CoreConfig.key(db)).isLogAllRefUpdates());
		assertEquals(LogAllRefUpdates.TRUE,
				bareDb.getConfig().get(CoreConfig.key(bareDb)).getLogAllRefUpdates());
		assertTrue(
				bareDb.getConfig().get(CoreConfig.key(bareDb)).isLogAllRefUpdates());

		db.getConfig().setBoolean(CORE, null, LOGALL, false);
		bareDb.getConfig().setBoolean(CORE, null, LOGALL, false);
		assertEquals(LogAllRefUpdates.FALSE,
				db.getConfig().get(CoreConfig.key(db)).getLogAllRefUpdates());
		assertFalse(db.getConfig().get(CoreConfig.key(db)).isLogAllRefUpdates());
		assertEquals(LogAllRefUpdates.FALSE,
				bareDb.getConfig().get(CoreConfig.key(bareDb)).getLogAllRefUpdates());
		assertFalse(
				bareDb.getConfig().get(CoreConfig.key(bareDb)).isLogAllRefUpdates());

		db.getConfig().setString(CORE, null, LOGALL, "always");
		bareDb.getConfig().setString(CORE, null, LOGALL, "always");
		assertEquals(LogAllRefUpdates.ALWAYS,
				db.getConfig().get(CoreConfig.key(db)).getLogAllRefUpdates());
		assertTrue(db.getConfig().get(CoreConfig.key(db)).isLogAllRefUpdates());
		assertEquals(LogAllRefUpdates.ALWAYS,
				bareDb.getConfig().get(CoreConfig.key(bareDb)).getLogAllRefUpdates());
		assertTrue(
				bareDb.getConfig().get(CoreConfig.key(bareDb)).isLogAllRefUpdates());
	}

	@Test
	public void testLogAllRefUpdatesInitialValueInConfigMatchesCGit()
			throws Exception {
		assertEquals("true", db.getConfig().getString(CORE, null, LOGALL));

		Repository bareDb = createBareRepository();
		assertNull(bareDb.getConfig().getString(CORE, null, LOGALL));
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
		ru.setRefLogMessage("commit : "
				+ ((nl == -1) ? commitMsg : commitMsg.substring(0, nl)), false);
		ru.forceUpdate();
	}
}
