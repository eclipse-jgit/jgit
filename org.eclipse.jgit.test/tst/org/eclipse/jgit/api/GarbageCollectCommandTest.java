/*
 * Copyright (C) 2012, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.Properties;

import org.eclipse.jgit.junit.RepositoryTestCase;
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
}
