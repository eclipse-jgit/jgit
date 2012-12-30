/*
 * Copyright (C) 2011, Garmin International
 * Copyright (C) 2011, Jesse Greenwald <jesse.greenwald@gmail.com>
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
		ObjectReader or = db.newObjectReader();
		ObjectInserter oi = db.newObjectInserter();

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
		TreeWalk tw = new TreeWalk(or);
		tw.addTree(tree);
		ObjectId a = null;
		ObjectId aSlashC = null;
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

		assertEquals(a, TreeWalk.forPath(or, "a", tree).getObjectId(0));
		assertEquals(a, TreeWalk.forPath(or, "a/", tree).getObjectId(0));
		assertEquals(null, TreeWalk.forPath(or, "/a", tree));
		assertEquals(null, TreeWalk.forPath(or, "/a/", tree));

		assertEquals(aDotB.getObjectId(), TreeWalk.forPath(or, "a.b", tree)
				.getObjectId(0));
		assertEquals(null, TreeWalk.forPath(or, "/a.b", tree));
		assertEquals(null, TreeWalk.forPath(or, "/a.b/", tree));
		assertEquals(aDotB.getObjectId(), TreeWalk.forPath(or, "a.b/", tree)
				.getObjectId(0));

		assertEquals(aZeroB.getObjectId(), TreeWalk.forPath(or, "a0b", tree)
				.getObjectId(0));

		assertEquals(aSlashB.getObjectId(), TreeWalk.forPath(or, "a/b", tree)
				.getObjectId(0));
		assertEquals(aSlashB.getObjectId(), TreeWalk.forPath(or, "b", a)
				.getObjectId(0));

		assertEquals(aSlashC, TreeWalk.forPath(or, "a/c", tree).getObjectId(0));
		assertEquals(aSlashC, TreeWalk.forPath(or, "c", a).getObjectId(0));

		assertEquals(aSlashCSlashD.getObjectId(),
				TreeWalk.forPath(or, "a/c/d", tree).getObjectId(0));
		assertEquals(aSlashCSlashD.getObjectId(), TreeWalk
				.forPath(or, "c/d", a).getObjectId(0));

		or.release();
		oi.release();
	}

}
