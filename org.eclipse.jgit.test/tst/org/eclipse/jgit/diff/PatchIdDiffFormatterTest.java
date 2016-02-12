/*
 * Copyright (C) 2011, Stefan Lay <stefan.lay@.com>
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
package org.eclipse.jgit.diff;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junit.Test;

public class PatchIdDiffFormatterTest extends RepositoryTestCase {

	@Test
	public void testDiff() throws Exception {
		write(new File(db.getDirectory().getParent(), "test.txt"), "test");
		File folder = new File(db.getDirectory().getParent(), "folder");
		folder.mkdir();
		write(new File(folder, "folder.txt"), "folder");
		try (Git git = new Git(db);
				PatchIdDiffFormatter df = new PatchIdDiffFormatter()) {
			git.add().addFilepattern(".").call();
			git.commit().setMessage("Initial commit").call();
			write(new File(folder, "folder.txt"), "folder change");

			df.setRepository(db);
			df.setPathFilter(PathFilter.create("folder"));
			DirCacheIterator oldTree = new DirCacheIterator(db.readDirCache());
			FileTreeIterator newTree = new FileTreeIterator(db);
			df.format(oldTree, newTree);
			df.flush();

			assertEquals("1ff64e0f9333e9b81967c3e8d7a81362b14d5441", df
					.getCalulatedPatchId().name());
		}
	}

	@Test
	public void testSameDiff() throws Exception {
		write(new File(db.getDirectory().getParent(), "test.txt"), "test");
		File folder = new File(db.getDirectory().getParent(), "folder");
		folder.mkdir();
		write(new File(folder, "folder.txt"), "\n\n\n\nfolder");
		try (Git git = new Git(db)) {
			git.add().addFilepattern(".").call();
			git.commit().setMessage("Initial commit").call();
			write(new File(folder, "folder.txt"), "\n\n\n\nfolder change");

			try (PatchIdDiffFormatter df = new PatchIdDiffFormatter()) {
				df.setRepository(db);
				df.setPathFilter(PathFilter.create("folder"));
				DirCacheIterator oldTree = new DirCacheIterator(db.readDirCache());
				FileTreeIterator newTree = new FileTreeIterator(db);
				df.format(oldTree, newTree);
				df.flush();

				assertEquals("08fca5ac531383eb1da8bf6b6f7cf44411281407", df
						.getCalulatedPatchId().name());
			}

			write(new File(folder, "folder.txt"), "a\n\n\n\nfolder");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("Initial commit").call();
			write(new File(folder, "folder.txt"), "a\n\n\n\nfolder change");

			try (PatchIdDiffFormatter df = new PatchIdDiffFormatter()) {
				df.setRepository(db);
				df.setPathFilter(PathFilter.create("folder"));
				DirCacheIterator oldTree = new DirCacheIterator(db.readDirCache());
				FileTreeIterator newTree = new FileTreeIterator(db);
				df.format(oldTree, newTree);
				df.flush();

				assertEquals("08fca5ac531383eb1da8bf6b6f7cf44411281407", df
						.getCalulatedPatchId().name());
			}
		}
	}

}
