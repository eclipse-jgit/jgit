/*
 * Copyright (C) 2007-2009, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2007, Shawn O. Pearce <spearce@spearce.org>
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
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.eclipse.jgit.util.JGitTestUtil;
import org.eclipse.jgit.util.SystemReader;

/**
 * Base class for most JGit unit tests.
 *
 * Sets up a predefined test repository and has support for creating additional
 * repositories and destroying them when the tests are finished.
 *
 * A system property <em>jgit.junit.usemmap</em> defines whether memory mapping
 * is used. Memory mapping has an effect on the file system, in that memory
 * mapped files in java cannot be deleted as long as they mapped arrays have not
 * been reclaimed by the garbage collector. The programmer cannot control this
 * with precision, though hinting using <em>{@link java.lang.System#gc}</em>
 * often helps.
 */
public abstract class RepositoryTestCase extends TestCase {

	protected final File trashParent = new File("trash");

	protected File trash;

	protected File trash_git;

	protected static final PersonIdent jauthor;

	protected static final PersonIdent jcommitter;

	static {
		jauthor = new PersonIdent("J. Author", "jauthor@example.com");
		jcommitter = new PersonIdent("J. Committer", "jcommitter@example.com");
	}

	protected boolean packedGitMMAP;

	/**
	 * Configure JGit before setting up test repositories.
	 */
	protected void configure() {
		final WindowCacheConfig c = new WindowCacheConfig();
		c.setPackedGitLimit(128 * WindowCacheConfig.KB);
		c.setPackedGitWindowSize(8 * WindowCacheConfig.KB);
		c.setPackedGitMMAP("true".equals(System.getProperty("jgit.junit.usemmap")));
		c.setDeltaBaseCacheLimit(8 * WindowCacheConfig.KB);
		WindowCache.reconfigure(c);
	}

	/**
	 * Utility method to delete a directory recursively. It is
	 * also used internally. If a file or directory cannot be removed
	 * it throws an AssertionFailure.
	 *
	 * @param dir
	 */
	protected void recursiveDelete(final File dir) {
		recursiveDelete(dir, false, getClass().getName() + "." + getName(), true);
	}

	protected static boolean recursiveDelete(final File dir, boolean silent,
			final String name, boolean failOnError) {
		assert !(silent && failOnError);
		if (!dir.exists())
			return silent;
		final File[] ls = dir.listFiles();
		if (ls != null) {
			for (int k = 0; k < ls.length; k++) {
				final File e = ls[k];
				if (e.isDirectory()) {
					silent = recursiveDelete(e, silent, name, failOnError);
				} else {
					if (!e.delete()) {
						if (!silent) {
							reportDeleteFailure(name, failOnError, e);
						}
						silent = !failOnError;
					}
				}
			}
		}
		if (!dir.delete()) {
			if (!silent) {
				reportDeleteFailure(name, failOnError, dir);
			}
			silent = !failOnError;
		}
		return silent;
	}

	private static void reportDeleteFailure(final String name,
			boolean failOnError, final File e) {
		String severity;
		if (failOnError)
			severity = "Error";
		else
			severity = "Warning";
		String msg = severity + ": Failed to delete " + e;
		if (name != null)
			msg += " in " + name;
		if (failOnError)
			fail(msg);
		else
			System.out.println(msg);
	}

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
		File tf = new File(trash, name);
		File tfp = tf.getParentFile();
		if (!tfp.exists() && !tf.getParentFile().mkdirs())
			throw new Error("Could not create directory " + tf.getParentFile());
		final OutputStreamWriter fw = new OutputStreamWriter(
				new FileOutputStream(tf), "UTF-8");
		try {
			fw.write(data);
		} finally {
			fw.close();
		}
		return tf;
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

	protected Repository db;

	private static Thread shutdownhook;
	private static List<Runnable> shutDownCleanups = new ArrayList<Runnable>();
	private static int testcount;

	private ArrayList<Repository> repositoriesToClose = new ArrayList<Repository>();

	public void setUp() throws Exception {
		super.setUp();
		configure();
		final String name = getClass().getName() + "." + getName();
		recursiveDelete(trashParent, true, name, false); // Cleanup old failed stuff
		trash = new File(trashParent,"trash"+System.currentTimeMillis()+"."+(testcount++));
		trash_git = new File(trash, ".git").getCanonicalFile();
		if (shutdownhook == null) {
			shutdownhook = new Thread() {
				@Override
				public void run() {
					// This may look superfluous, but is an extra attempt
					// to clean up. First GC to release as many resources
					// as possible and then try to clean up one test repo
					// at a time (to record problems) and finally to drop
					// the directory containing all test repositories.
					System.gc();
					for (Runnable r : shutDownCleanups)
						r.run();
					recursiveDelete(trashParent, false, null, false);
				}
			};
			Runtime.getRuntime().addShutdownHook(shutdownhook);
		}

		final MockSystemReader mockSystemReader = new MockSystemReader();
		mockSystemReader.userGitConfig = new FileBasedConfig(new File(
				trash_git, "usergitconfig"));
		SystemReader.setInstance(mockSystemReader);

		db = new Repository(trash_git);
		db.create();

		final String[] packs = {
				"pack-34be9032ac282b11fa9babdc2b2a93ca996c9c2f",
				"pack-df2982f284bbabb6bdb59ee3fcc6eb0983e20371",
				"pack-9fb5b411fe6dfa89cc2e6b89d2bd8e5de02b5745",
				"pack-546ff360fe3488adb20860ce3436a2d6373d2796",
				"pack-cbdeda40019ae0e6e789088ea0f51f164f489d14",
				"pack-e6d07037cbcf13376308a0a995d1fa48f8f76aaa",
				"pack-3280af9c07ee18a87705ef50b0cc4cd20266cf12"
		};
		final File packDir = new File(db.getObjectsDirectory(), "pack");
		for (int k = 0; k < packs.length; k++) {
			copyFile(JGitTestUtil.getTestResourceFile(packs[k] + ".pack"), new File(packDir,
					packs[k] + ".pack"));
			copyFile(JGitTestUtil.getTestResourceFile(packs[k] + ".idx"), new File(packDir,
					packs[k] + ".idx"));
		}

		copyFile(JGitTestUtil.getTestResourceFile("packed-refs"), new File(trash_git,"packed-refs"));
	}

	protected void tearDown() throws Exception {
		RepositoryCache.clear();
		db.close();
		for (Repository r : repositoriesToClose)
			r.close();

		// Since memory mapping is controlled by the GC we need to
		// tell it this is a good time to clean up and unlock
		// memory mapped files.
		if (packedGitMMAP)
			System.gc();

		final String name = getClass().getName() + "." + getName();
		recursiveDelete(trash, false, name, true);
		for (Repository r : repositoriesToClose)
			recursiveDelete(r.getWorkDir(), false, name, true);
		repositoriesToClose.clear();

		super.tearDown();
	}

	/**
	 * Helper for creating extra empty repos
	 *
	 * @return a new empty git repository for testing purposes
	 *
	 * @throws IOException
	 */
	protected Repository createNewEmptyRepo() throws IOException {
		return createNewEmptyRepo(false);
	}

	/**
	 * Helper for creating extra empty repos
	 *
	 * @param bare if true, create a bare repository.
	 * @return a new empty git repository for testing purposes
	 *
	 * @throws IOException
	 */
	protected Repository createNewEmptyRepo(boolean bare) throws IOException {
		final File newTestRepo = new File(trashParent, "new"
				+ System.currentTimeMillis() + "." + (testcount++)
				+ (bare ? "" : "/") + ".git").getCanonicalFile();
		assertFalse(newTestRepo.exists());
		final Repository newRepo = new Repository(newTestRepo);
		newRepo.create();
		final String name = getClass().getName() + "." + getName();
		shutDownCleanups.add(new Runnable() {
			public void run() {
				recursiveDelete(newTestRepo, false, name, false);
			}
		});
		repositoriesToClose.add(newRepo);
		return newRepo;
	}

	protected void setupReflog(String logName, byte[] data)
			throws FileNotFoundException, IOException {
				File logfile = new File(db.getDirectory(), logName);
				if (!logfile.getParentFile().mkdirs()
						&& !logfile.getParentFile().isDirectory()) {
					throw new IOException(
							"oops, cannot create the directory for the test reflog file"
									+ logfile);
				}
				FileOutputStream fileOutputStream = new FileOutputStream(logfile);
				try {
					fileOutputStream.write(data);
				} finally {
					fileOutputStream.close();
				}
			}

}
