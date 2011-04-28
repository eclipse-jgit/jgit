/*******************************************************************************
 *  Copyright (c) 2011 GitHub Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *    Kevin Sawicki (GitHub Inc.) - initial API and implementation
 *******************************************************************************/
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests of path-based uses of {@link CheckoutCommand}
 */
public class PathCheckoutCommandTest extends RepositoryTestCase {

	Git git;

	RevCommit initialCommit;

	RevCommit secondCommit;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		writeTrashFile("Test.txt", "1");
		writeTrashFile("Test2.txt", "a");
		git.add().addFilepattern("Test.txt").addFilepattern("Test2.txt").call();
		initialCommit = git.commit().setMessage("Initial commit").call();

		writeTrashFile("Test.txt", "2");
		writeTrashFile("Test2.txt", "b");
		git.add().addFilepattern("Test.txt").addFilepattern("Test2.txt").call();
		secondCommit = git.commit().setMessage("Second commit").call();

		writeTrashFile("Test.txt", "3");
		writeTrashFile("Test2.txt", "c");
		git.add().addFilepattern("Test.txt").addFilepattern("Test2.txt").call();
		git.commit().setMessage("Third commit").call();
	}

	@Test
	public void testUpdateWorkingDirectory() throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile("Test.txt", "");
		assertEquals("", read(written));
		co.addPath("Test.txt").call();
		assertEquals("3", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), "Test2.txt")));
	}

	@Test
	public void testCheckoutFirst() throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile("Test.txt", "");
		co.setStartPoint(initialCommit).addPath("Test.txt").call();
		assertEquals("1", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), "Test2.txt")));
	}

	@Test
	public void testCheckoutSecond() throws Exception {
		CheckoutCommand co = git.checkout();
		File written = writeTrashFile("Test.txt", "");
		co.setStartPoint("HEAD~1").addPath("Test.txt").call();
		assertEquals("2", read(written));
		assertEquals("c", read(new File(db.getWorkTree(), "Test2.txt")));
	}

	@Test
	public void testCheckoutMultiple() throws Exception {
		CheckoutCommand co = git.checkout();
		File test = writeTrashFile("Test.txt", "");
		File test2 = writeTrashFile("Test2.txt", "");
		co.setStartPoint("HEAD~2").addPath("Test.txt").addPath("Test2.txt")
				.call();
		assertEquals("1", read(test));
		assertEquals("a", read(test2));
	}
}
