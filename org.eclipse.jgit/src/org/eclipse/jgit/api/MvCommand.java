/*
 * Copyright (C) 2014, Tomasz Zarna <Tomasz.Zarna@tasktop.com>
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
import java.util.Collections;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.MoveException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;

/**
 * A class used to execute a {@code Mv} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-mv.html"
 *      >Git documentation about Mv</a>
 * @since 3.6
 */
public class MvCommand extends GitCommand<DirCache> {

	private String source;

	private String destination;

	MvCommand(Repository repo) {
		super(repo);
	}

	public DirCache call() throws GitAPIException {
		if (source == null || destination == null)
			throw new NoFilepatternException(
					JGitText.get().sourceDestinationMustBeProvided);
		try {
			Git git = new Git(getRepository());
			File srcFile = new File(getRepository().getWorkTree(), source);
			if (!isTracked(srcFile)) {
				throw new MoveException(
						JGitText.get().sourceIsNotUnderVersionControl);
			}
			File dstFile = new File(getRepository().getWorkTree(), destination);
			srcFile.renameTo(dstFile);
			git.add().addFilepattern(destination).call();
			return git.rm().addFilepattern(source).call();
			// TODO
			/*
			 * From Robin: Behavior is very different from C Git here. If a flle
			 * affected by the source was tracked but not in the index C Git
			 * fals. Mv'ing a directory without tracked files fails in C Git.
			 * You can mv a directory without some untracked files and the whole
			 * directory will be renamed. The -f and -k options are quite
			 * important to handle here.
			 */
		} catch (IOException e) {
			throw new JGitInternalException(
					MessageFormat.format(
							JGitText.get().exceptionCaughtDuringExecutionOfMvCommand,
							e), e);
		}
	}

	/**
	 * @param src
	 *            source file
	 * @return {@code this}
	 */
	public MvCommand setSource(String src) {
		this.source = src;
		return this;
	}

	/**
	 * @param dst
	 *            destination file or folder
	 * @return {@code this}
	 */
	public MvCommand setDestination(String dst) {
		this.destination = dst;
		return this;
	}

	private boolean isTracked(File file) throws IOException {
		ObjectId objectId = repo.resolve(Constants.HEAD);
		RevTree tree;
		if (objectId != null)
			tree = new RevWalk(repo).parseTree(objectId);
		else
			tree = null;

		TreeWalk treeWalk = new TreeWalk(repo);
		treeWalk.setRecursive(true);
		if (tree != null)
			treeWalk.addTree(tree);
		else
			treeWalk.addTree(new EmptyTreeIterator());
		treeWalk.addTree(new DirCacheIterator(repo.readDirCache()));
		treeWalk.setFilter(PathFilterGroup.createFromStrings(Collections
				.singleton(Repository.stripWorkDir(repo.getWorkTree(), file))));
		return treeWalk.next();
	}
}
