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

package org.eclipse.jgit.junit;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import java.util.TreeSet;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;

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
		return JGitTestUtil.writeTrashFile(db, name, data);
	}

	protected File writeTrashFile(final String subdir, final String name,
			final String data)
			throws IOException {
		return JGitTestUtil.writeTrashFile(db, subdir, name, data);
	}

	protected String read(final String name) throws IOException {
		return JGitTestUtil.read(db, name);
	}

	protected void deleteTrashFile(final String name) throws IOException {
		JGitTestUtil.deleteTrashFile(db, name);
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
	@Before
	public void setUp() throws Exception {
		super.setUp();
		db = createWorkRepository();
		trash = db.getWorkTree();
	}

	public static final int MOD_TIME = 1;

	public static final int SMUDGE = 2;

	public static final int LENGTH = 4;

	public static final int CONTENT_ID = 8;

	public static final int CONTENT = 16;

	public static final int ASSUME_UNCHANGED = 32;

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
	 * assertions against the string although the concrete value of the time
	 * stamps is unknown.
	 *
	 * @param repo
	 *            the repository the index state should be determined for
	 *
	 * @param includedOptions
	 *            a bitmask constructed out of the constants {@link #MOD_TIME},
	 *            {@link #SMUDGE}, {@link #LENGTH}, {@link #CONTENT_ID} and
	 *            {@link #CONTENT} controlling which info is present in the
	 *            resulting string.
	 * @return a string encoding the index state
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	public String indexState(Repository repo, int includedOptions)
			throws IllegalStateException, IOException {
		DirCache dc = repo.readDirCache();
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
			if (0 != (includedOptions & ASSUME_UNCHANGED))
				sb.append(", assume-unchanged:"
						+ Boolean.toString(entry.isAssumeValid()));
			sb.append("]");
		}
		return sb.toString();
	}

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
	 * assertions against the string although the concrete value of the time
	 * stamps is unknown.
	 *
	 * @param includedOptions
	 *            a bitmask constructed out of the constants {@link #MOD_TIME},
	 *            {@link #SMUDGE}, {@link #LENGTH}, {@link #CONTENT_ID} and
	 *            {@link #CONTENT} controlling which info is present in the
	 *            resulting string.
	 * @return a string encoding the index state
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	public String indexState(int includedOptions)
			throws IllegalStateException, IOException {
		return indexState(db, includedOptions);
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
		FS fs = FS.DETECTED;
		if (lastFile != null && !fs.exists(lastFile))
			throw new FileNotFoundException(lastFile.getPath());
		File tmp = File.createTempFile("FileTreeIteratorWithTimeControl", null);
		try {
			long startTime = (lastFile == null) ? fs.lastModified(tmp) : fs
					.lastModified(lastFile);
			long actTime = fs.lastModified(tmp);
			while (actTime <= startTime) {
				Thread.sleep(sleepTime);
				sleepTime *= 5;
				fs.setLastModified(tmp, System.currentTimeMillis());
				actTime = fs.lastModified(tmp);
			}
			return actTime;
		} finally {
			FileUtils.delete(tmp);
		}
	}

	protected void createBranch(ObjectId objectId, String branchName)
			throws IOException {
		RefUpdate updateRef = db.updateRef(branchName);
		updateRef.setNewObjectId(objectId);
		updateRef.update();
	}

	protected void checkoutBranch(String branchName)
			throws IllegalStateException, IOException {
		RevWalk walk = new RevWalk(db);
		RevCommit head = walk.parseCommit(db.resolve(Constants.HEAD));
		RevCommit branch = walk.parseCommit(db.resolve(branchName));
		DirCacheCheckout dco = new DirCacheCheckout(db, head.getTree().getId(),
				db.lockDirCache(), branch.getTree().getId());
		dco.setFailOnConflict(true);
		dco.checkout();
		walk.release();
		// update the HEAD
		RefUpdate refUpdate = db.updateRef(Constants.HEAD);
		refUpdate.link(branchName);
	}

	/**
	 * Writes a number of files in the working tree. The first content specified
	 * will be written into a file named '0', the second into a file named "1"
	 * and so on. If <code>null</code> is specified as content then this file is
	 * skipped.
	 *
	 * @param ensureDistinctTimestamps
	 *            if set to <code>true</code> then between two write operations
	 *            this method will wait to ensure that the second file will get
	 *            a different lastmodification timestamp than the first file.
	 * @param contents
	 *            the contents which should be written into the files
	 * @return the File object associated to the last written file.
	 * @throws IOException
	 * @throws InterruptedException
	 */
	protected File writeTrashFiles(boolean ensureDistinctTimestamps,
			String... contents)
			throws IOException, InterruptedException {
				File f = null;
				for (int i = 0; i < contents.length; i++)
					if (contents[i] != null) {
						if (ensureDistinctTimestamps && (f != null))
							fsTick(f);
						f = writeTrashFile(Integer.toString(i), contents[i]);
					}
				return f;
			}

	/**
	 * Commit a file with the specified contents on the specified branch,
	 * creating the branch if it didn't exist before.
	 * <p>
	 * It switches back to the original branch after the commit if there was
	 * one.
	 *
	 * @param filename
	 * @param contents
	 * @param branch
	 * @return the created commit
	 */
	protected RevCommit commitFile(String filename, String contents, String branch) {
		try {
			Git git = new Git(db);
			String originalBranch = git.getRepository().getFullBranch();
			if (git.getRepository().getRef(branch) == null)
				git.branchCreate().setName(branch).call();
			git.checkout().setName(branch).call();
			writeTrashFile(filename, contents);
			git.add().addFilepattern(filename).call();
			RevCommit commit = git.commit()
					.setMessage(branch + ": " + filename).call();
			if (originalBranch != null)
				git.checkout().setName(originalBranch).call();
			return commit;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	protected DirCacheEntry createEntry(final String path, final FileMode mode) {
		return createEntry(path, mode, DirCacheEntry.STAGE_0, path);
	}

	protected DirCacheEntry createEntry(final String path, final FileMode mode,
			final String content) {
		return createEntry(path, mode, DirCacheEntry.STAGE_0, content);
	}

	protected DirCacheEntry createEntry(final String path, final FileMode mode,
			final int stage, final String content) {
		final DirCacheEntry entry = new DirCacheEntry(path, stage);
		entry.setFileMode(mode);
		entry.setObjectId(new ObjectInserter.Formatter().idFor(
				Constants.OBJ_BLOB, Constants.encode(content)));
		return entry;
	}
}
