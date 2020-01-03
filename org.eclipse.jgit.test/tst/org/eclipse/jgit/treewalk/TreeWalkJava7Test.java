/*
 * Copyright (C) 2012-2013, Robin Rosenberg <robin.rosenberg@dewire.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.treewalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

public class TreeWalkJava7Test extends RepositoryTestCase {
	@Test
	public void testSymlinkToDirNotRecursingViaSymlink() throws Exception {
		org.junit.Assume.assumeTrue(FS.DETECTED.supportsSymlinks());
		FS fs = db.getFS();
		assertTrue(fs.supportsSymlinks());
		writeTrashFile("target/data", "targetdata");
		fs.createSymLink(new File(trash, "link"), "target");
		try (TreeWalk tw = new TreeWalk(db)) {
			tw.setRecursive(true);
			tw.addTree(new FileTreeIterator(db));
			assertTrue(tw.next());
			assertEquals("link", tw.getPathString());
			assertTrue(tw.next());
			assertEquals("target/data", tw.getPathString());
			assertFalse(tw.next());
		}
	}
}
