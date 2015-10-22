/*
 * Copyright (C) 2015, Dariusz Luksza <dariusz@luksza.org>
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

package org.eclipse.jgit.lfs.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

public class LfsPointerFilterTest {

	private static final int SIZE = 12345;

	private static final String OID = "4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393";

	private static final String[] NOT_VALID_LFS_FILES = { "", // empty file
			// simulate java file
			"package org.eclipse.jgit;",
			// invalid LFS pointer, no oid and version
			"version https://hawser.github.com/spec/v1\n",
			// invalid LFS pointer, no version
			"version https://hawser.github.com/spec/v1\n"
					+ "oid sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393\n",
			// invalid LFS pointer, no id
			"version https://hawser.github.com/spec/v1\n" + "size 12345\n",
			// invalid LFS pointer, wrong order of oid and size
			"version https://hawser.github.com/spec/v1\n" + "size 12345\n"
					+ "oid sha256:4d7a214614ab2935c943f9e0ff69d22eadbb8f32b1258daaa5e2ca24d17e2393\n" };

	private static final String[] LFS_VERSION_DOMAINS = {
			"hawser", "git-lfs"
	};

	private static final String[] VALID_LFS_FILES = {
			// valid LFS pointer
			"version https://%s.github.com/spec/v1\n"
					+ "oid sha256:" + OID + "\n"
					+ "size " + SIZE + "\n",
			// valid LFS pointer with "custom" key
			"version https://%s.github.com/spec/v1\n"
					+ "custom key with value\n"
					+ "oid sha256:" + OID + "\n"
					+ "size " + SIZE + "\n",
			// valid LFS pointer with key with "."
			"version https://%s.github.com/spec/v1\n"
					+ "oid sha256:" + OID + "\n"
					+ "r.key key with .\n"
					+ "size " + SIZE + "\n",
			// valid LFS pointer with key with "-"
			"version https://%s.github.com/spec/v1\n"
					+ "oid sha256:" + OID + "\n"
					+ "size " + SIZE + "\n"
					+ "valid-name another valid key\n" };

	@Test
	public void testRegularFilesInRepositoryRoot() throws Exception {
		for (String file : NOT_VALID_LFS_FILES) {
			assertLfs("file.bin", file).withRecursive(false).shouldBe(false);
		}
	}

	@Test
	public void testNestedRegularFiles() throws Exception {
		for (String file : NOT_VALID_LFS_FILES) {
			assertLfs("a/file.bin", file).withRecursive(true).shouldBe(false);
		}
	}

	@Test
	public void testValidPointersInRepositoryRoot() throws Exception {
		for (String domain : LFS_VERSION_DOMAINS) {
			for (String file : VALID_LFS_FILES) {
				assertLfs("file.bin", String.format(file, domain))
						.withRecursive(true).shouldBe(true)
					.check();
			}
		}
	}

	@Test
	public void testValidNestedPointers() throws Exception {
		for (String domain : LFS_VERSION_DOMAINS) {
			for (String file : VALID_LFS_FILES) {
				assertLfs("a/file.bin", String.format(file, domain))
						.withRecursive(true).shouldBe(true).check();
			}
		}
	}

	@Test
	public void testValidNestedPointersWithoutRecurrence() throws Exception {
		for (String domain : LFS_VERSION_DOMAINS) {
			for (String file : VALID_LFS_FILES) {
				assertLfs("file.bin", String.format(file, domain))
						.withRecursive(false).shouldBe(true).check();
				assertLfs("a/file.bin", String.format(file, domain))
						.withRecursive(false).shouldBe(false).check();
			}
		}
	}

	private static LfsTreeWalk assertLfs(String path, String content) {
		return new LfsTreeWalk(path, content);
	}

	private static class LfsTreeWalk {
		private final String path;

		private final String content;

		private boolean state;

		private boolean recursive;

		private TestRepository<InMemoryRepository> tr;

		LfsTreeWalk(String path, String content) {
			this.path = path;
			this.content = content;
		}

		LfsTreeWalk withRecursive(boolean shouldBeRecursive) {
			this.recursive = shouldBeRecursive;
			return this;
		}

		LfsTreeWalk shouldBe(boolean shouldBeValid) {
			this.state = shouldBeValid;
			return this;
		}

		void check() throws Exception {
			tr = new TestRepository<>(new InMemoryRepository(
					new DfsRepositoryDescription("test")));
			RevCommit commit = tr.branch("master").commit().add(path, content)
					.message("initial commit").create();
			RevTree tree = parseCommit(commit);
			LfsPointerFilter filter = new LfsPointerFilter();
			try (TreeWalk treeWalk = new TreeWalk(tr.getRepository())) {
				treeWalk.addTree(tree);
				treeWalk.setRecursive(recursive);
				treeWalk.setFilter(filter);

				if (state) {
					assertTrue(treeWalk.next());
					assertEquals(path, treeWalk.getPathString());
					assertNotNull(filter.getPointer());
					assertEquals(SIZE, filter.getPointer().getSize());
					assertEquals(OID, filter.getPointer().getOid().name());
				} else {
					assertFalse(treeWalk.next());
					assertNull(filter.getPointer());
				}
			}
		}

		private RevTree parseCommit(RevCommit commit) throws Exception {
			try (ObjectWalk ow = new ObjectWalk(tr.getRepository())) {
				return ow.parseCommit(commit).getTree();
			}
		}
	}
}
