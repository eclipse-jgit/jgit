/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2010, Stefan Lay <stefan.lay@sap.com>
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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;

import org.eclipse.jgit.JGitText;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.ObjectWriter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

/**
 * A class used to execute a {@code Add} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-add.html"
 *      >Git documentation about Add</a>
 */
public class AddCommand extends GitCommand<DirCache> {

	private Collection<String> filepatterns;

	/**
	 *
	 * @param repo
	 */
	public AddCommand(Repository repo) {
		super(repo);
		filepatterns = new LinkedList<String>();
	}

	/**
	 * @param filepattern
	 *            File to add content from. Also a leading directory name (e.g.
	 *            dir to add dir/file1 and dir/file2) can be given to add all
	 *            files in the directory, recursively. Fileglobs (e.g. *.c) are
	 *            not yet supported.
	 * @return {@code this}
	 */
	public AddCommand addFilepattern(String filepattern) {
		checkCallable();
		filepatterns.add(filepattern);
		return this;
	}

	/**
	 * Executes the {@code Add} command. Each instance of this class should only
	 * be used for one invocation of the command. Don't call this method twice
	 * on an instance.
	 *
	 * @return the DirCache after Add
	 */
	public DirCache call() throws NoFilepatternException {

		if (filepatterns.isEmpty())
			throw new NoFilepatternException(JGitText.get().atLeastOnePatternIsRequired);
		checkCallable();
		DirCache dc = null;

		try {
			dc = DirCache.lock(repo);
			ObjectWriter ow = new ObjectWriter(repo);
			DirCacheIterator c;

			DirCacheBuilder builder = dc.builder();
			final TreeWalk tw = new TreeWalk(repo);
			tw.reset();
			tw.addTree(new DirCacheBuildIterator(builder));
			FileTreeIterator fileTreeIterator = new FileTreeIterator(
					repo.getWorkDir(), repo.getFS());
			tw.addTree(fileTreeIterator);
			tw.setRecursive(true);
			tw.setFilter(PathFilterGroup.createFromStrings(filepatterns));

			String lastAddedFile = null;

			while (tw.next()) {
				String path = tw.getPathString();

				final File file = new File(repo.getWorkDir(), path);
				// In case of an existing merge conflict the
				// DirCacheBuildIterator iterates over all stages of
				// this path, we however want to add only one
				// new DirCacheEntry per path.
				if (!(path.equals(lastAddedFile))) {
					 FileTreeIterator f = tw.getTree(1, FileTreeIterator.class);
					 if (f != null) { // the file exists
						DirCacheEntry entry = new DirCacheEntry(path);
						entry.setLength((int)f.getEntryLength());
						entry.setLastModified(f.getEntryLastModified());
						entry.setFileMode(f.getEntryFileMode());
						entry.setObjectId(ow.writeBlob(file));

						builder.add(entry);
						lastAddedFile = path;
					} else {
						c = tw.getTree(0, DirCacheIterator.class);
						builder.add(c.getDirCacheEntry());
					}
				}
			}
			builder.commit();
			setCallable(false);
		} catch (IOException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfAddCommand, e);
		} finally {
			if (dc != null)
				dc.unlock();
		}

		return dc;
	}

}
