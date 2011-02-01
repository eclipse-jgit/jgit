/*
 * Copyright (C) 2011, Chris Aniszczyk <caniszczyk@gmail.com>
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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Test;

public class ResetCommandTest extends RepositoryTestCase {

	private Git git;

	private RevCommit initialCommit;

	private File indexFile;

	private File untrackedFile;

	public void setupRepository() throws IOException, NoFilepatternException,
			NoHeadException, NoMessageException, ConcurrentRefUpdateException,
			JGitInternalException, WrongRepositoryStateException {

		// create initial commit
		git = new Git(db);
		initialCommit = git.commit().setMessage("initial commit").call();

		// create file
		indexFile = new File(db.getWorkTree(), "a.txt");
		FileUtils.createNewFile(indexFile);
		PrintWriter writer = new PrintWriter(indexFile);
		writer.print("content");
		writer.flush();

		// add file and commit it
		git.add().addFilepattern("a.txt").call();
		git.commit().setMessage("adding a.txt").call();

		// modify file and add to index
		writer.print("new content");
		writer.close();
		git.add().addFilepattern("a.txt").call();

		// create a file not added to the index
		untrackedFile = new File(db.getWorkTree(),
				"notAddedToIndex.txt");
		FileUtils.createNewFile(untrackedFile);
		PrintWriter writer2 = new PrintWriter(untrackedFile);
		writer2.print("content");
		writer2.close();
	}

	@Test
	public void testHardReset() throws JGitInternalException,
			AmbiguousObjectException, IOException, NoFilepatternException,
			NoHeadException, NoMessageException, ConcurrentRefUpdateException,
			WrongRepositoryStateException {
		setupRepository();
		git.reset().setMode(ResetType.HARD).setRef(initialCommit.getName())
				.call();
		// check if HEAD points to initial commit now
		ObjectId head = db.resolve(Constants.HEAD);
		assertTrue(head.equals(initialCommit));
		// check if files were removed
		assertFalse(indexFile.exists());
		assertTrue(untrackedFile.exists());
		// fileInIndex must no longer be in HEAD and in the index
		String fileInIndexPath = indexFile.getAbsolutePath();
		assertFalse(inHead(fileInIndexPath));
		assertFalse(inIndex(indexFile.getName()));
	}

	@Test
	public void testSoftReset() throws JGitInternalException,
			AmbiguousObjectException, IOException, NoFilepatternException,
			NoHeadException, NoMessageException, ConcurrentRefUpdateException,
			WrongRepositoryStateException {
		setupRepository();
		git.reset().setMode(ResetType.SOFT).setRef(initialCommit.getName())
				.call();
		// check if HEAD points to initial commit now
		ObjectId head = db.resolve(Constants.HEAD);
		assertTrue(head.equals(initialCommit));
		// check if files still exist
		assertTrue(untrackedFile.exists());
		assertTrue(indexFile.exists());
		// fileInIndex must no longer be in HEAD but has to be in the index
		String fileInIndexPath = indexFile.getAbsolutePath();
		assertFalse(inHead(fileInIndexPath));
		assertTrue(inIndex(indexFile.getName()));
	}

	@Test
	public void testMixedReset() throws JGitInternalException,
			AmbiguousObjectException, IOException, NoFilepatternException,
			NoHeadException, NoMessageException, ConcurrentRefUpdateException,
			WrongRepositoryStateException {
		setupRepository();
		git.reset().setMode(ResetType.MIXED).setRef(initialCommit.getName())
				.call();
		// check if HEAD points to initial commit now
		ObjectId head = db.resolve(Constants.HEAD);
		assertTrue(head.equals(initialCommit));
		// check if files still exist
		assertTrue(untrackedFile.exists());
		assertTrue(indexFile.exists());
		// fileInIndex must no longer be in HEAD and in the index
		String fileInIndexPath = indexFile.getAbsolutePath();
		assertFalse(inHead(fileInIndexPath));
		assertFalse(inIndex(indexFile.getName()));
	}

	/**
	 * Checks if a file with the given path exists in the HEAD tree
	 *
	 * @param path
	 * @return true if the file exists
	 * @throws IOException
	 */
	private boolean inHead(String path) throws IOException {
		ObjectId headId = db.resolve(Constants.HEAD);
		RevWalk rw = new RevWalk(db);
		TreeWalk tw = null;
		try {
			tw = TreeWalk.forPath(db, path, rw.parseTree(headId));
			return tw != null;
		} finally {
			rw.release();
			rw.dispose();
			if (tw != null)
				tw.release();
		}
	}

	/**
	 * Checks if a file with the given path exists in the index
	 *
	 * @param path
	 * @return true if the file exists
	 * @throws IOException
	 */
	private boolean inIndex(String path) throws IOException {
		DirCache dc = DirCache.read(db.getIndexFile(), db.getFS());
		return dc.getEntry(path) != null;
	}

}
