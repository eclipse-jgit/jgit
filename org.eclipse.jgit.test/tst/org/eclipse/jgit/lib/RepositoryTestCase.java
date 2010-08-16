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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import java.util.TreeSet;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.FileTreeIterator;

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

	public static final int MOD_TIME = 1;

	public static final int SMUDGE = 2;

	public static final int LENGTH = 4;

	public static final int CONTENT_ID = 8;

	public static final int CONTENT = 16;

	/**
	 * Represent the state of the index in one String. This representation is
	 * useful when writing tests which do assertions on the state of the index.
	 * By default information about path, mode, stage (if different from 0) is
	 * included. A bitmask controls which additional info about
	 * modificationTimes, smudge state and length is included.
	 * <p>
	 * The format of the returned string is described with this BNF:
	 *
	 * <pre>
	 * result = ( "[" path mode stage? time? smudge? length? sha1? content? "]" )* .
	 * mode = ", mode:" number .
	 * stage = ", stage:" number .
	 * time = ", time:t" timestamp-index .
	 * smudge = "" | ", smudged" .
	 * length = ", length:" number .
	 * sha1 = ", sha1:" hex-sha1 .
	 * content = ", content:" blob-data .
	 * </pre>
	 *
	 * 'stage' is only presented when the stage is different from 0. All
	 * reported time stamps are mapped to strings like "t0", "t1", ... "tn". The
	 * smallest reported time-stamp will be called "t0". This allows to write
	 * assertions against the string although the concrete value of the
	 * time stamps is unknown.
	 *
	 * @param includedOptions
	 *            a bitmask constructed out of the constants {@link #MOD_TIME},
	 *            {@link #SMUDGE}, {@link #LENGTH}, {@link #CONTENT_ID} and {@link #CONTENT}
	 *            controlling which info is present in the resulting string.
	 * @return a string encoding the index state
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	public String indexState(int includedOptions)
			throws IllegalStateException, IOException {
		DirCache dc = db.readDirCache();
		StringBuilder sb = new StringBuilder();
		TreeSet<Long> timeStamps = null;

		// iterate once over the dircache just to collect all time stamps
		if (0 != (includedOptions & MOD_TIME)) {
			timeStamps = new TreeSet<Long>();
			for (int i=0; i<dc.getEntryCount(); ++i)
				timeStamps.add(Long.valueOf(dc.getEntry(i).getLastModified()));
		}

		// iterate again, now produce the result string
		for (int i=0; i<dc.getEntryCount(); ++i) {
			DirCacheEntry entry = dc.getEntry(i);
			sb.append("["+entry.getPathString()+", mode:" + entry.getFileMode());
			int stage = entry.getStage();
			if (stage != 0)
				sb.append(", stage:" + stage);
			if (0 != (includedOptions & MOD_TIME)) {
				sb.append(", time:t"+
					timeStamps.headSet(Long.valueOf(entry.getLastModified())).size());
			}
			if (0 != (includedOptions & SMUDGE))
				if (entry.isSmudged())
					sb.append(", smudged");
			if (0 != (includedOptions & LENGTH))
				sb.append(", length:"
						+ Integer.toString(entry.getLength()));
			if (0 != (includedOptions & CONTENT_ID))
				sb.append(", sha1:" + ObjectId.toString(entry.getObjectId()));
			if (0 != (includedOptions & CONTENT)) {
				sb.append(", content:"
						+ new String(db.open(entry.getObjectId(),
								Constants.OBJ_BLOB).getCachedBytes(), "UTF-8"));
			}
			sb.append("]");
		}
		return sb.toString();
	}

	/**
	 * Resets the index to represent exactly some filesystem content. E.g. the
	 * following call will replace the index with the working tree content:
	 * <p>
	 * <code>resetIndex(new FileSystemIterator(db))</code>
	 * <p>
	 * This method can be used by testcases which first prepare a new commit
	 * somewhere in the filesystem (e.g. in the working-tree) and then want to
	 * have an index which matches their prepared content.
	 *
	 * @param treeItr
	 *            a {@link FileTreeIterator} which determines which files should
	 *            go into the new index
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	protected void resetIndex(FileTreeIterator treeItr)
			throws FileNotFoundException, IOException {
		ObjectInserter inserter = db.newObjectInserter();
		DirCacheBuilder builder = db.lockDirCache().builder();
		DirCacheEntry dce;

		while (!treeItr.eof()) {
			long len = treeItr.getEntryLength();

			dce = new DirCacheEntry(treeItr.getEntryPathString());
			dce.setFileMode(treeItr.getEntryFileMode());
			dce.setLastModified(treeItr.getEntryLastModified());
			dce.setLength((int) len);
			FileInputStream in = new FileInputStream(treeItr.getEntryFile());
			dce.setObjectId(inserter.insert(Constants.OBJ_BLOB, len, in));
			in.close();
			builder.add(dce);
			treeItr.next(1);
		}
		builder.commit();
		inserter.flush();
		inserter.release();
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
	 *         nameTemplates containing "%n" to always have unique names
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

	/**
	 * Waits until it is guaranteed that a subsequent file modification has a
	 * younger modification timestamp than the modification timestamp of the
	 * given file. This is done by touching a temporary file, reading the
	 * lastmodified attribute and, if needed, sleeping. After sleeping this loop
	 * starts again until the filesystem timer has advanced enough.
	 *
	 * @param lastFile
	 *            the file on which we want to wait until the filesystem timer
	 *            has advanced more than the lastmodification timestamp of this
	 *            file
	 * @return return the last measured value of the filesystem timer which is
	 *         greater than then the lastmodification time of lastfile.
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public static long fsTick(File lastFile) throws InterruptedException,
			IOException {
		long sleepTime = 1;
		File tmp = File.createTempFile("FileTreeIteratorWithTimeControl", null);
		try {
			long startTime = (lastFile == null) ? tmp.lastModified() : lastFile
					.lastModified();
			long actTime = tmp.lastModified();
			while (actTime <= startTime) {
				Thread.sleep(sleepTime);
				sleepTime *= 5;
				tmp.setLastModified(System.currentTimeMillis());
				actTime = tmp.lastModified();
			}
			return actTime;
		} finally {
			tmp.delete();
		}
	}
}
