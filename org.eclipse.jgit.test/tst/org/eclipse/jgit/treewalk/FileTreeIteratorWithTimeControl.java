/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.treewalk;

import java.io.File;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;

/**
 * A {@link FileTreeIterator} used in tests which allows to specify explicitly
 * what will be returned by {@link #getEntryLastModified()}. This allows to
 * write tests where certain files have to have the same modification time.
 * <p>
 * This iterator is configured by a list of strictly increasing long values
 * t(0), t(1), ..., t(n). For each file with a modification between t(x) and
 * t(x+1) [ t(x) &lt;= time &lt; t(x+1) ] this iterator will report t(x). For
 * files with a modification time smaller t(0) a modification time of 0 is
 * returned. For files with a modification time greater or equal t(n) t(n) will
 * be returned.
 * <p>
 * This class was written especially to test racy-git problems
 */
public class FileTreeIteratorWithTimeControl extends FileTreeIterator {
	private TreeSet<Long> modTimes;

	public FileTreeIteratorWithTimeControl(FileTreeIterator p, Repository repo,
			TreeSet<Long> modTimes) {
		super(p, repo.getWorkTree(), repo.getFS());
		this.modTimes = modTimes;
	}

	public FileTreeIteratorWithTimeControl(FileTreeIterator p, File f, FS fs,
			TreeSet<Long> modTimes) {
		super(p, f, fs);
		this.modTimes = modTimes;
	}

	public FileTreeIteratorWithTimeControl(Repository repo,
			TreeSet<Long> modTimes) {
		super(repo);
		this.modTimes = modTimes;
	}

	public FileTreeIteratorWithTimeControl(File f, FS fs,
			TreeSet<Long> modTimes) {
		super(f, fs, new Config().get(WorkingTreeOptions.KEY));
		this.modTimes = modTimes;
	}

	@Override
	public AbstractTreeIterator createSubtreeIterator(ObjectReader reader) {
		return new FileTreeIteratorWithTimeControl(this,
				((FileEntry) current()).getFile(), fs, modTimes);
	}

	@Override
	public long getEntryLastModified() {
		if (modTimes == null)
			return 0;
		Long cutOff = Long.valueOf(super.getEntryLastModified() + 1);
		SortedSet<Long> head = modTimes.headSet(cutOff);
		return head.isEmpty() ? 0 : head.last().longValue();
	}
}
