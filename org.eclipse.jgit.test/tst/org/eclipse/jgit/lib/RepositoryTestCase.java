/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2007-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2007, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2009, Yann Simon <yann.simon.fr@gmail.com>
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

package org.eclipse.jgit.lib;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.FileTreeIteratorWithTimeControl;
import org.eclipse.jgit.treewalk.NameConflictTreeWalk;

/**
 * Base class for most JGit unit tests.
 *
 * Sets up a predefined test repository and has support for creating additional
 * repositories and destroying them when the tests are finished.
 */
public abstract class RepositoryTestCase extends LocalDiskRepositoryTestCase {
	protected static void copyFile(final File src, final File dst)
			throws IOException {
		final FileInputStream fis = new FileInputStream(src);
		try {
			final FileOutputStream fos = new FileOutputStream(dst);
			try {
				final byte[] buf = new byte[4096];
				int r;
				while ((r = fis.read(buf)) > 0) {
					fos.write(buf, 0, r);
				}
			} finally {
				fos.close();
			}
		} finally {
			fis.close();
		}
	}

	protected File writeTrashFile(final String name, final String data)
			throws IOException {
		File path = new File(db.getWorkTree(), name);
		write(path, data);
		return path;
	}

	protected static void checkFile(File f, final String checkData)
			throws IOException {
		Reader r = new InputStreamReader(new FileInputStream(f), "ISO-8859-1");
		try {
			char[] data = new char[(int) f.length()];
			if (f.length() !=  r.read(data))
				throw new IOException("Internal error reading file data from "+f);
			assertEquals(checkData, new String(data));
		} finally {
			r.close();
		}
	}

	/** Test repository, initialized for this test case. */
	protected FileRepository db;

	/** Working directory of {@link #db}. */
	protected File trash;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		db = createWorkRepository();
		trash = db.getWorkTree();
	}

	public String indexState(TreeSet<Long> modTimes)
			throws IllegalStateException, MissingObjectException,
			IncorrectObjectTypeException, IOException {
		DirCache dc = db.readDirCache();
		Map lookup = new HashMap();
		List ret = new ArrayList(dc.getEntryCount());
		NameConflictTreeWalk tw = new NameConflictTreeWalk(db);
		tw.reset();
		tw.addTree(new FileTreeIteratorWithTimeControl(db, modTimes));
		tw.addTree(new DirCacheIterator(dc));
		boolean smudgedBefore;
		while (tw.next()) {
			List entry = new ArrayList(4);
			FileTreeIteratorWithTimeControl fIt = tw.getTree(0,
					FileTreeIteratorWithTimeControl.class);
			DirCacheIterator dcIt = tw.getTree(1, DirCacheIterator.class);
			entry.add(tw.getPathString());
			entry.add("modTime(index/file): "
					+ ((dcIt == null) ? "null" : lookup(Long.valueOf(dcIt
							.getDirCacheEntry().getLastModified()), "t%n",
							lookup))
					+ "/"
					+ ((fIt == null) ? "null" : lookup(
							Long.valueOf(fIt.getEntryLastModified()), "t%n",
							lookup)));
			smudgedBefore = (dcIt == null) ? false : dcIt.getDirCacheEntry()
					.isSmudged();
			if (fIt != null
					&& dcIt != null
					&& fIt.isModified(dcIt.getDirCacheEntry(), true, true,
							db.getFS()))
				entry.add("dirty");
			if (dcIt != null && dcIt.getDirCacheEntry().isSmudged())
				entry.add("smudged");
			else if (smudgedBefore)
				entry.add("unsmudged");
			ret.add(entry);
		}
		return ret.toString();
	}

	/**
	 * Helper method to map arbitrary objects to user-defined names. This can be
	 * used create short names for objects to produce small and stable debug
	 * output. It is guaranteed that when you lookup the same object multiple
	 * times even with different nameTemplates this method will always return
	 * the same name which was derived from the first nameTemplate.
	 * nameTemplates can contain "%n" which will be replaced by a running number
	 * before used as a name.
	 *
	 * @param l
	 *            the object to lookup
	 * @param nameTemplate
	 *            the name for that object. Can contain "%n" which will be
	 *            replaced by a running number before used as a name. If the
	 *            lookup table already contains the object this parameter will
	 *            be ignored
	 * @param lookupTable
	 *            a table storing object-name mappings.
	 * @return a name of that object. Is not guaranteed to be unique. Use
	 *         nameTemplates containing "%n" to always have uniqe names
	 */
	public static String lookup(Object l, String nameTemplate,
			Map<Object, String> lookupTable) {
		String name = lookupTable.get(l);
		if (name == null) {
			name = nameTemplate.replaceAll("%n",
					Integer.toString(lookupTable.size()));
			lookupTable.put(l, name);
		}
		return name;
	}
}
