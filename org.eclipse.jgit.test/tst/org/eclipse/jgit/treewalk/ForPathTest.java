/*
 * Copyright (C) 2011, Garmin International
 * Copyright (C) 2011, Jesse Greenwald <jesse.greenwald@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk;

import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.junit.Test;

public class ForPathTest extends RepositoryTestCase {

	private static final FileMode SYMLINK = FileMode.SYMLINK;

	private static final FileMode REGULAR_FILE = FileMode.REGULAR_FILE;

	private static final FileMode EXECUTABLE_FILE = FileMode.EXECUTABLE_FILE;

	@Test
	public void testFindObjects() throws Exception {
		final DirCache tree0 = DirCache.newInCore();
		final DirCacheBuilder b0 = tree0.builder();
		try (ObjectReader or = db.newObjectReader();
				ObjectInserter oi = db.newObjectInserter()) {

			DirCacheEntry aDotB = createEntry("a.b", EXECUTABLE_FILE);
			b0.add(aDotB);
			DirCacheEntry aSlashB = createEntry("a/b", REGULAR_FILE);
			b0.add(aSlashB);
			DirCacheEntry aSlashCSlashD = createEntry("a/c/d", REGULAR_FILE);
			b0.add(aSlashCSlashD);
			DirCacheEntry aZeroB = createEntry("a0b", SYMLINK);
			b0.add(aZeroB);
			b0.finish();
			assertEquals(4, tree0.getEntryCount());
			ObjectId tree = tree0.writeTree(oi);

			// Find the directories that were implicitly created above.
			ObjectId a = null;
			ObjectId aSlashC = null;
			try (TreeWalk tw = new TreeWalk(or)) {
				tw.addTree(tree);
				while (tw.next()) {
					if (tw.getPathString().equals("a")) {
						a = tw.getObjectId(0);
						tw.enterSubtree();
						while (tw.next()) {
							if (tw.getPathString().equals("a/c")) {
								aSlashC = tw.getObjectId(0);
								break;
							}
						}
						break;
					}
				}
			}

			assertEquals(a, TreeWalk.forPath(or, "a", tree).getObjectId(0));
			assertEquals(a, TreeWalk.forPath(or, "a/", tree).getObjectId(0));
			assertEquals(null, TreeWalk.forPath(or, "/a", tree));
			assertEquals(null, TreeWalk.forPath(or, "/a/", tree));

			assertEquals(aDotB.getObjectId(),
					TreeWalk.forPath(or, "a.b", tree).getObjectId(0));
			assertEquals(null, TreeWalk.forPath(or, "/a.b", tree));
			assertEquals(null, TreeWalk.forPath(or, "/a.b/", tree));
			assertEquals(aDotB.getObjectId(),
					TreeWalk.forPath(or, "a.b/", tree).getObjectId(0));

			assertEquals(aZeroB.getObjectId(),
					TreeWalk.forPath(or, "a0b", tree).getObjectId(0));

			assertEquals(aSlashB.getObjectId(),
					TreeWalk.forPath(or, "a/b", tree).getObjectId(0));
			assertEquals(aSlashB.getObjectId(),
					TreeWalk.forPath(or, "b", a).getObjectId(0));

			assertEquals(aSlashC,
					TreeWalk.forPath(or, "a/c", tree).getObjectId(0));
			assertEquals(aSlashC, TreeWalk.forPath(or, "c", a).getObjectId(0));

			assertEquals(aSlashCSlashD.getObjectId(),
					TreeWalk.forPath(or, "a/c/d", tree).getObjectId(0));
			assertEquals(aSlashCSlashD.getObjectId(),
					TreeWalk.forPath(or, "c/d", a).getObjectId(0));
		}
	}

}
