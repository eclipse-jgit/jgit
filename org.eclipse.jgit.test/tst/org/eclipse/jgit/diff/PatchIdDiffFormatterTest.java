/*
 * Copyright (C) 2011, Stefan Lay <stefan.lay@.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
