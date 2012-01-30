/*
 * Copyright (C) 2012, Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.TestRepository.BranchBuilder;
import org.eclipse.jgit.junit.TestRepository.CommitBuilder;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.file.PackIndex.MutableEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GCTest extends LocalDiskRepositoryTestCase {
	private TestRepository<FileRepository> tr;

	private GC gc;

	private FileRepository repo;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		repo = createWorkRepository();
		tr = new TestRepository<FileRepository>((repo));
		gc = new GC(repo);
	}

	@After
	public void tearDown() throws Exception {
		super.tearDown();
	}

	@Test
	public void emptyRepo_noPackCreated() throws IOException {
		gc.gc(null);
		assertEquals(0, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void oneNonReferencedObject_pruned() throws Exception {
		tr.blob("a");
		// TODO: need to specify --expire now once this parameter is supported
		// until then it is not clear if this test should succeed or fail
		// as the GC.gc doesn't tell anything about expire policy and the
		// implementation is not looking at the age of the objects
		gc.gc(null);
		assertEquals(0, repo.getObjectDatabase().getPacks().size());
		assertEquals(0, looseObjectIDs().size());
	}

	@Test
	public void nonReferencedObjectTree_pruned() throws Exception {
		tr.tree(tr.file("a", tr.blob("a")));
		// TODO: specify --expire now
		gc.gc(null);
		assertEquals(0, repo.getObjectDatabase().getPacks().size());
		assertEquals(0, looseObjectIDs().size());
	}

	@Test
	public void lightweightTag_objectNotPruned() throws Exception {
		RevBlob a = tr.blob("a");
		tr.lightweightTag("t", a);
		// TODO: specify --expire now
		gc.gc(null);
		assertEquals(1, repo.getObjectDatabase().getPacks().size());
		assertTrue(packedObjectIDs().contains(a.name()));
		assertEquals(0, looseObjectIDs().size());
	}

	@Test
	public void annotatedTag_objectNotPruned() throws Exception {
		RevBlob a = tr.blob("a");
		RevTag t = tr.tag("t", a);
		// TODO: specify --expire now
		gc.gc(null);
		assertEquals(1, repo.getObjectDatabase().getPacks().size());
		assertTrue(packedObjectIDs().contains(a.name()));
		assertTrue(packedObjectIDs().contains(t.name()));
		assertEquals(0, looseObjectIDs().size());
	}

	@Test
	public void branch_historyNotPruned() throws Exception {
		RevCommit tip = commitChain(10);
		tr.branch("b").update(tip);
		// TODO: specify --expire now
		gc.gc(null);
		assertEquals(1, repo.getObjectDatabase().getPacks().size());
		Set<String> packed = packedObjectIDs();
		do {
			assertTrue(packed.contains(tip.name()));
			tr.parseBody(tip);
			RevTree t = tip.getTree();
			assertTrue(packed.contains(t.name()));
			assertTrue(packed.contains(tr.get(t, "a").name()));
			tip = tip.getParentCount() > 0 ? tip.getParent(0) : null;
		} while (tip != null);

		assertEquals(0, looseObjectIDs().size());
	}

	@Test
	public void deleteBranch_historyPruned() throws Exception {
		RevCommit tip = commitChain(10);
		tr.branch("b").update(tip);
		RefUpdate update = repo.updateRef("refs/heads/b");
		update.setForceUpdate(true);
		update.delete();
		// TODO: specify --expire now
		// TODO: expire reflogs
		gc.gc(null);
		assertEquals(0, repo.getObjectDatabase().getPacks().size());
		assertEquals(0, looseObjectIDs().size());
	}

	@Test
	public void deleteOneBranch_otherBranchHistoryNotPruned() throws Exception {
		RevCommit parent = tr.commit().create();
		tr.branch("b1").commit().parent(parent).create();
		tr.branch("b2").commit().parent(parent).create();
		RefUpdate update = repo.updateRef("refs/heads/b2");
		update.setForceUpdate(true);
		update.delete();
		// TODO: specify --expire now
		gc.gc(null);
		assertEquals(1, repo.getObjectDatabase().getPacks().size());
		assertTrue(packedObjectIDs().contains(parent.name()));
		assertEquals(0, looseObjectIDs().size());
	}

	// TODO: merge cases
	// TODO: is git note a valid reference?
	// TODO: concurrency: two GCs at the same time
	// TODO: concurrency: new ref created while GC running

	@Test
	public void testPackAllObjectsInOnePack() throws Exception {
		tr.branch("refs/heads/master").commit().add("A", "A").add("B", "B")
				.create();
		assertEquals(4, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());
		gc.gc(null);
		assertEquals(0, looseObjectIDs().size());
		assertEquals(4, packedObjectIDs().size());
		assertEquals(1, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void testPackRepoWithNoRefs() throws Exception {
		tr.commit().add("A", "A").add("B", "B").create();
		assertEquals(4, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());
		gc.gc(null);
		assertEquals(4, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());
		assertEquals(0, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void testPack2Commits() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();

		assertEquals(8, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());
		gc.gc(null);
		assertEquals(0, looseObjectIDs().size()); // todo, should be 0
		assertEquals(8, packedObjectIDs().size());
		assertEquals(1, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void testPackCommitsAndLooseOne() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		RevCommit first = bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		tr.update("refs/heads/master", first);

		assertEquals(8, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());
		gc.gc(null);
		assertEquals(4, looseObjectIDs().size()); // todo, should be 0
		assertEquals(4, packedObjectIDs().size());
		assertEquals(1, repo.getObjectDatabase().getPacks().size());
	}

	@Test
	public void testIndexSavesObjects() throws Exception {
		BranchBuilder bb = tr.branch("refs/heads/master");
		bb.commit().add("A", "A").add("B", "B").create();
		bb.commit().add("A", "A2").add("B", "B2").create();
		bb.commit().add("A", "A3"); // this new content in index should survive
		assertEquals(9, looseObjectIDs().size());
		assertEquals(0, packedObjectIDs().size());
		gc.gc(null);
		assertEquals(1, looseObjectIDs().size()); // todo, should be 0
		assertEquals(8, packedObjectIDs().size());
		assertEquals(1, repo.getObjectDatabase().getPacks().size());
	}

	public Set<String> looseObjectIDs() {
		Set<String> ret = new HashSet<String>();
		for (File parent : repo.getObjectsDirectory().listFiles())
			if (parent.getName().matches("[0-9a-fA-F]{2}"))
				for (File obj : parent.listFiles())
					if (obj.getName().matches("[0-9a-fA-F]{38}"))
						ret.add(parent.getName() + obj.getName());
		return ret;
	}

	/**
	 * Create a chain of commits of given depth.
	 * <p>
	 * Each commit contains one file named "a" containing the index of the
	 * commit in the chain as its content. The created commit chain is
	 * referenced from any ref.
	 * <p>
	 * A chain of depth = N will create 3*N objects in Gits object database. For
	 * each depth level three objects are created: the commit object, the
	 * top-level tree object and a blob for the content of the file "a".
	 *
	 * @param depth
	 *            the depth of the commit chain.
	 * @return the commit that is the tip of the commit chain
	 * @throws Exception
	 */
	private RevCommit commitChain(int depth) throws Exception {
		if (depth <= 0)
			throw new IllegalArgumentException("Chain depth must be > 0");
		CommitBuilder cb = tr.commit();
		RevCommit tip;
		do {
			--depth;
			tip = cb.add("a", "" + depth).message("" + depth).create();
			cb = cb.child();
		} while (depth > 0);
		return tip;
	}

	public Set<String> packedObjectIDs() throws IOException {
		Set<String> ret = new HashSet<String>();
		repo.scanForRepoChanges();
		Iterator<PackFile> pi = repo.getObjectDatabase().getPacks().iterator();
		while (pi.hasNext()) {
			PackFile pf = pi.next();
			Iterator<MutableEntry> ei = pf.iterator();
			while (ei.hasNext()) {
				MutableEntry enty = ei.next();
				ret.add(enty.name());
			}
		}
		return ret;
	}
}
