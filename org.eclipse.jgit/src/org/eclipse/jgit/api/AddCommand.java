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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
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

	private WorkingTreeIterator workingTreeIterator;

	private boolean update = false;

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
	 * Allow clients to provide their own implementation of a FileTreeIterator
	 * @param f
	 * @return {@code this}
	 */
	public AddCommand setWorkingTreeIterator(WorkingTreeIterator f) {
		workingTreeIterator = f;
		return this;
	}

	/**
	 * Executes the {@code Add} command. Each instance of this class should only
	 * be used for one invocation of the command. Don't call this method twice
	 * on an instance.
	 *
	 * @return the DirCache after Add
	 */
	public DirCache call() throws GitAPIException, NoFilepatternException {

		if (filepatterns.isEmpty())
			throw new NoFilepatternException(JGitText.get().atLeastOnePatternIsRequired);
		checkCallable();
		DirCache dc = null;
		boolean addAll = false;
		if (filepatterns.contains(".")) //$NON-NLS-1$
			addAll = true;

		ObjectInserter inserter = repo.newObjectInserter();
		try {
			dc = repo.lockDirCache();
			DirCacheIterator c;

			DirCacheBuilder builder = dc.builder();
			final TreeWalk tw = new TreeWalk(repo);
			tw.addTree(new DirCacheBuildIterator(builder));
			if (workingTreeIterator == null)
				workingTreeIterator = new FileTreeIterator(repo);
			tw.addTree(workingTreeIterator);
			tw.setRecursive(true);
			if (!addAll)
				tw.setFilter(PathFilterGroup.createFromStrings(filepatterns));

			String lastAddedFile = null;

			while (tw.next()) {
				String path = tw.getPathString();

				WorkingTreeIterator f = tw.getTree(1, WorkingTreeIterator.class);
				if (tw.getTree(0, DirCacheIterator.class) == null &&
						f != null && f.isEntryIgnored()) {
					// file is not in index but is ignored, do nothing
				}
				// In case of an existing merge conflict the
				// DirCacheBuildIterator iterates over all stages of
				// this path, we however want to add only one
				// new DirCacheEntry per path.
				else if (!(path.equals(lastAddedFile))) {
					if (!(update && tw.getTree(0, DirCacheIterator.class) == null)) {
						c = tw.getTree(0, DirCacheIterator.class);
						if (f != null) { // the file exists
							long sz = f.getEntryLength();
							DirCacheEntry entry = new DirCacheEntry(path);
							if (c == null || c.getDirCacheEntry() == null
									|| !c.getDirCacheEntry().isAssumeValid()) {
								FileMode mode = f.getIndexFileMode(c);
								entry.setFileMode(mode);

								if (FileMode.GITLINK != mode) {
									entry.setLength(sz);
									entry.setLastModified(f
											.getEntryLastModified());
									long contentSize = f
											.getEntryContentLength();
									InputStream in = f.openEntryStream();
									try {
										entry.setObjectId(inserter.insert(
												Constants.OBJ_BLOB, contentSize, in));
									} finally {
										in.close();
									}
								} else
									entry.setObjectId(f.getEntryObjectId());
								builder.add(entry);
								lastAddedFile = path;
							} else {
								builder.add(c.getDirCacheEntry());
							}

						} else if (c != null
								&& (!update || FileMode.GITLINK == c
										.getEntryFileMode()))
							builder.add(c.getDirCacheEntry());
					}
				}
			}
			inserter.flush();
			builder.commit();
			setCallable(false);
		} catch (IOException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfAddCommand, e);
		} finally {
			inserter.release();
			if (dc != null)
				dc.unlock();
		}

		return dc;
	}

	/**
	 * @param update
	 *            If set to true, the command only matches {@code filepattern}
	 *            against already tracked files in the index rather than the
	 *            working tree. That means that it will never stage new files,
	 *            but that it will stage modified new contents of tracked files
	 *            and that it will remove files from the index if the
	 *            corresponding files in the working tree have been removed.
	 *            In contrast to the git command line a {@code filepattern} must
	 *            exist also if update is set to true as there is no
	 *            concept of a working directory here.
	 *
	 * @return {@code this}
	 */
	public AddCommand setUpdate(boolean update) {
		this.update = update;
		return this;
	}

	/**
	 * @return is the parameter update is set
	 */
	public boolean isUpdate() {
		return update;
	}
}
