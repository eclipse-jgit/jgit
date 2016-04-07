/*
 * Copyright (C) 2012, Matthias Sohn <matthias.sohn@sap.com>
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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.GitDateParser;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Before;
import org.junit.Test;

public class GarbageCollectCommandTest extends RepositoryTestCase {
	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		String path = "a.txt";
		writeTrashFile(path, "content");
		git.add().addFilepattern(path).call();
		git.commit().setMessage("commit").call();
	}

	@Test
	public void testGConeCommit() throws Exception {
		Date expire = GitDateParser.parse("now", null, SystemReader
				.getInstance().getLocale());
		Properties res = git.gc().setExpire(expire).call();
		assertTrue(res.size() == 7);
	}

	@Test
	public void testGCmoreCommits() throws Exception {
		writeTrashFile("a.txt", "a couple of words for gc to pack");
		writeTrashFile("b.txt", "a couple of words for gc to pack 2");
		writeTrashFile("c.txt", "a couple of words for gc to pack 3");
		git.commit().setAll(true).setMessage("commit2").call();
		writeTrashFile("a.txt", "a couple of words for gc to pack more");
		writeTrashFile("b.txt", "a couple of words for gc to pack more 2");
		writeTrashFile("c.txt", "a couple of words for gc to pack more 3");
		git.commit().setAll(true).setMessage("commit3").call();
		Properties res = git
				.gc()
				.setExpire(
						GitDateParser.parse("now", null, SystemReader
								.getInstance().getLocale())).call();
		assertTrue(res.size() == 7);
	}

	@Test
	public void testPruneOldOrphanCommit() throws Exception {
		StoredConfig config = git.getRepository().getConfig();
		config.setString("gc", null, "prunePackExpire", "1.second.ago");
		config.setString("gc", null, "pruneExpire", "1.second.ago");
		config.save();
		ObjectId initial = git.getRepository().resolve("HEAD");
		RevCommit orphan = git.commit().setMessage("orphan").call();
		changeLastModified(orphan, subtractDays(new Date(), 365));
		RefUpdate refUpdate = git.getRepository()
				.updateRef("refs/heads/master");
		refUpdate.setNewObjectId(initial);
		refUpdate.forceUpdate();
		FileUtils.delete(new File(git.getRepository().getDirectory(), "logs"),
				FileUtils.RECURSIVE | FileUtils.RETRY);

		git.gc().setExpire(new Date()).call();
		Thread.sleep(4000);
		git.gc().setExpire(new Date()).call();

		assertNull(git.getRepository().resolve(orphan.name()));
	}

	private static Date subtractDays(Date date, int days) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(Calendar.DAY_OF_MONTH, days * (-1));
		return calendar.getTime();
	}

	private void changeLastModified(ObjectId commitId, Date date) {
		File objectsDirectory = new File(git.getRepository().getDirectory(),
				"objects");
		File commitObjectDirectory = new File(objectsDirectory,
				commitId.name().substring(0, 2));
		File commitObjectFile = new File(commitObjectDirectory,
				commitId.name().substring(2));
		commitObjectFile.setLastModified(date.getTime());
	}

}
