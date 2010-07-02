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
import java.text.MessageFormat;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectWriter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;

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

	private String filepath;

	/**
	 *
	 * @param repo
	 * @param filepath
	 *            the repository relative path of the file to add
	 */
	public AddCommand(Repository repo, String filepath) {
		super(repo);
		this.filepath = filepath;
	}

	/**
	 * Executes the {@code Add} command. Each instance of this class should only
	 * be used for one invocation of the command. Don't call this method twice
	 * on an instance.
	 *
	 * @return the DirCache after Add
	 */
	public DirCache call() throws IOException {
		final File file = new File(repo.getWorkDir(), filepath);
		if (!file.exists()) {
			DirCache dc = DirCache.lock(repo);
			try {
				if (dc.findEntry(filepath) < 0) {
					throw new IllegalStateException(MessageFormat.format(
							"File {0} does not exist", filepath));
				} else {
					return dc;
				}
			} finally {
				dc.unlock();
			}
		}

		if (file.isDirectory())
			throw new IllegalStateException(MessageFormat.format(
					"Adding directory {0} not yet supported", filepath));

		ObjectWriter ow = new ObjectWriter(repo);
		final ObjectId id = ow.writeBlob(file);

		DirCache dc = DirCache.lock(repo);

		try {
			DirCacheBuilder builder = dc.builder();
			final TreeWalk tw = new TreeWalk(repo);
			tw.reset();
			tw.addTree(new DirCacheBuildIterator(builder));
			FileTreeIterator fileTreeIterator = new FileTreeIterator(
					repo.getWorkDir(), repo.getFS());
			tw.addTree(fileTreeIterator);
			tw.setRecursive(true);

			String lastAddedFile = null;

			while (tw.next()) {
				if (tw.getPathString().equals(filepath)) {
					if (!(filepath.equals(lastAddedFile))) {
						DirCacheEntry entry = new DirCacheEntry(filepath);
						entry.setObjectId(id);
						entry.setFileMode(repo.getFS().canExecute(file) ? FileMode.EXECUTABLE_FILE
								: FileMode.REGULAR_FILE);
						entry.setLastModified(file.lastModified());
						entry.setLength((int) file.length());

						builder.add(entry);
						lastAddedFile = filepath;
					}
				} else {
					final DirCacheIterator c = tw.getTree(0,
							DirCacheIterator.class);
					builder.add(c.getDirCacheEntry());
				}
			}

			builder.commit();
		} finally {
			dc.unlock();
		}

		return dc;
	}

}
