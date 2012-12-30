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
package org.eclipse.jgit.revwalk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class RevCommitListTest extends RepositoryTestCase {

	private RevCommitList<RevCommit> list;

	public void setup(int count) throws Exception {
		Git git = new Git(db);
		for (int i = 0; i < count; i++)
			git.commit().setCommitter(committer).setAuthor(author)
					.setMessage("commit " + i).call();
		list = new RevCommitList<RevCommit>();
		RevWalk w = new RevWalk(db);
		w.markStart(w.lookupCommit(db.resolve(Constants.HEAD)));
		list.source(w);
	}

	@Test
	public void testFillToHighMark2() throws Exception {
		setup(3);
		list.fillTo(1);
		assertEquals(2, list.size());
		assertEquals("commit 2", list.get(0).getFullMessage());
		assertEquals("commit 1", list.get(1).getFullMessage());
		assertNull("commit 0 shouldn't be loaded", list.get(2));
	}

	@Test
	public void testFillToHighMarkAll() throws Exception {
		setup(3);
		list.fillTo(2);
		assertEquals(3, list.size());
		assertEquals("commit 2", list.get(0).getFullMessage());
		assertEquals("commit 0", list.get(2).getFullMessage());
	}

	@Test
	public void testFillToHighMark4() throws Exception {
		setup(3);
		list.fillTo(3);
		assertEquals(3, list.size());
		assertEquals("commit 2", list.get(0).getFullMessage());
		assertEquals("commit 0", list.get(2).getFullMessage());
		assertNull("commit 3 can't be loaded", list.get(3));
	}

	@Test
	public void testFillToHighMarkMulitpleBlocks() throws Exception {
		setup(258);
		list.fillTo(257);
		assertEquals(258, list.size());
	}

	@Test
	public void testFillToCommit() throws Exception {
		setup(3);

		RevWalk w = new RevWalk(db);
		w.markStart(w.lookupCommit(db.resolve(Constants.HEAD)));

		w.next();
		RevCommit c = w.next();
		assertNotNull("should have found 2. commit", c);

		list.fillTo(c, 5);
		assertEquals(2, list.size());
		assertEquals("commit 1", list.get(1).getFullMessage());
		assertNull(list.get(3));
	}

	@Test
	public void testFillToUnknownCommit() throws Exception {
		setup(258);
		RevCommit c = new RevCommit(
				ObjectId.fromString("9473095c4cb2f12aefe1db8a355fe3fafba42f67"));

		list.fillTo(c, 300);
		assertEquals("loading to unknown commit should load all commits", 258,
				list.size());
	}

	@Test
	public void testFillToNullCommit() throws Exception {
		setup(3);
		list.fillTo(null, 1);
		assertNull(list.get(0));
	}
}
