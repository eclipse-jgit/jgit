/*
 * Copyright (C) 2009, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2009, Christian Halstrick, Matthias Sohn, SAP AG
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;

public class ReflogConfigTest extends RepositoryTestCase {
	@Test
	public void testlogAllRefUpdates() throws Exception {
		Instant commitTime = Instant.ofEpochSecond(1154236443L);
		ZoneOffset tz = ZoneOffset.ofHours(-4);

		// check that there are no entries in the reflog and turn off writing
		// reflogs
		RefDatabase refDb = db.getRefDatabase();
		assertTrue(refDb.getReflogReader(Constants.HEAD).getReverseEntries()
				.isEmpty());
		FileBasedConfig cfg = db.getConfig();
		cfg.setBoolean("core", null, "logallrefupdates", false);
		cfg.save();

		// do one commit and check that reflog size is 0: no reflogs should be
		// written
		commit("A Commit\n", commitTime, tz);
		commitTime = commitTime.plus(Duration.ofMinutes(1));
		assertTrue("Reflog for HEAD still contain no entry", refDb
				.getReflogReader(Constants.HEAD).getReverseEntries().isEmpty());

		// set the logAllRefUpdates parameter to true and check it
		cfg.setBoolean("core", null, "logallrefupdates", true);
		cfg.save();
		assertEquals(CoreConfig.LogRefUpdates.TRUE,
				cfg.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES,
						CoreConfig.LogRefUpdates.FALSE));

		// do one commit and check that reflog size is increased to 1
		commit("A Commit\n", commitTime, tz);
		commitTime = commitTime.plus(Duration.ofMinutes(1));
		assertTrue("Reflog for HEAD should contain one entry",
				refDb.getReflogReader(Constants.HEAD).getReverseEntries()
						.size() == 1);

		// set the logAllRefUpdates parameter to false and check it
		cfg.setBoolean("core", null, "logallrefupdates", false);
		cfg.save();
		assertEquals(CoreConfig.LogRefUpdates.FALSE,
				cfg.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES,
						CoreConfig.LogRefUpdates.TRUE));

		// do one commit and check that reflog size is 2
		commit("A Commit\n", commitTime, tz);
		commitTime = commitTime.plus(Duration.ofMinutes(1));
		assertTrue("Reflog for HEAD should contain two entries",
				refDb.getReflogReader(Constants.HEAD).getReverseEntries()
						.size() == 2);

		// set the logAllRefUpdates parameter to false and check it
		cfg.setEnum("core", null, "logallrefupdates",
				CoreConfig.LogRefUpdates.ALWAYS);
		cfg.save();
		assertEquals(CoreConfig.LogRefUpdates.ALWAYS,
				cfg.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null,
						ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES,
						CoreConfig.LogRefUpdates.FALSE));

		// do one commit and check that reflog size is 3
		commit("A Commit\n", commitTime, tz);
		assertTrue("Reflog for HEAD should contain three entries",
				refDb.getReflogReader(Constants.HEAD).getReverseEntries()
						.size() == 3);
	}

	private void commit(String commitMsg, Instant commitTime, ZoneOffset tz)
			throws IOException {
		CommitBuilder commit = new CommitBuilder();
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
