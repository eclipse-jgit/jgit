/*
 * Copyright (C) 2012 Google Inc.
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
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.Object;
import java.lang.String;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.pgm.CLIGitCommand;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ArchiveTest extends CLIRepositoryTestCase {
	private Git git;
	private String emptyTree;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		git.commit().setMessage("initial commit").call();
		emptyTree = db.resolve("HEAD^{tree}").abbreviate(12).name();
	}

	@Ignore("Some versions of java.util.zip refuse to write an empty ZIP")
	@Test
	public void testEmptyArchive() throws Exception {
		final byte[] result = CLIGitCommand.rawExecute( //
				"git archive " + emptyTree, db);
		assertArrayEquals(new String[0], listZipEntries(result));
	}

	@Test
	public void testEmptyTar() throws Exception {
		final byte[] result = CLIGitCommand.rawExecute( //
				"git archive --format=tar " + emptyTree, db);
		assertArrayEquals(new String[0], listTarEntries(result));
	}

	@Test
	public void testUnrecognizedFormat() throws Exception {
		final String[]expect = new String[] { "fatal: Unknown archive format 'nonsense'" };
		final String[] actual = execute("git archive --format=nonsense " + emptyTree);
		assertArrayEquals(expect, actual);
	}

	@Test
	public void testArchiveWithFiles() throws Exception {
		writeTrashFile("a", "a file with content!");
		writeTrashFile("c", ""); // empty file
		writeTrashFile("unrelated", "another file, just for kicks");
		git.add().addFilepattern("a").call();
		git.add().addFilepattern("c").call();
		git.commit().setMessage("populate toplevel").call();

		final byte[] result = CLIGitCommand.rawExecute( //
				"git archive HEAD", db);
		assertArrayEquals(new String[] { "a", "c" }, //
				listZipEntries(result));
	}

	@Test
	public void testArchiveWithSubdir() throws Exception {
		writeTrashFile("a", "a file with content!");
		writeTrashFile("b.c", "before subdir in git sort order");
		writeTrashFile("b0c", "after subdir in git sort order");
		writeTrashFile("c", "");
		git.add().addFilepattern("a").call();
		git.add().addFilepattern("b.c").call();
		git.add().addFilepattern("b0c").call();
		git.add().addFilepattern("c").call();
		git.commit().setMessage("populate toplevel").call();
		writeTrashFile("b/b", "file in subdirectory");
		writeTrashFile("b/a", "another file in subdirectory");
		git.add().addFilepattern("b").call();
		git.commit().setMessage("add subdir").call();

		final byte[] result = CLIGitCommand.rawExecute( //
				"git archive master", db);
		String[] expect = { "a", "b.c", "b0c", "b/a", "b/b", "c" };
		String[] actual = listZipEntries(result);

		Arrays.sort(expect);
		Arrays.sort(actual);
		assertArrayEquals(expect, actual);
	}

	@Test
	public void testTarWithSubdir() throws Exception {
		writeTrashFile("a", "a file with content!");
		writeTrashFile("b.c", "before subdir in git sort order");
		writeTrashFile("b0c", "after subdir in git sort order");
		writeTrashFile("c", "");
		git.add().addFilepattern("a").call();
		git.add().addFilepattern("b.c").call();
		git.add().addFilepattern("b0c").call();
		git.add().addFilepattern("c").call();
		git.commit().setMessage("populate toplevel").call();
		writeTrashFile("b/b", "file in subdirectory");
		writeTrashFile("b/a", "another file in subdirectory");
		git.add().addFilepattern("b").call();
		git.commit().setMessage("add subdir").call();

		final byte[] result = CLIGitCommand.rawExecute( //
				"git archive --format=tar master", db);
		String[] expect = { "a", "b.c", "b0c", "b/a", "b/b", "c" };
		String[] actual = listTarEntries(result);

		Arrays.sort(expect);
		Arrays.sort(actual);
		assertArrayEquals(expect, actual);
	}

	@Test
	public void testArchivePreservesMode() throws Exception {
		writeTrashFile("plain", "a file with content");
		writeTrashFile("executable", "an executable file");
		writeTrashFile("symlink", "plain");
		git.add().addFilepattern("plain").call();
		git.add().addFilepattern("executable").call();
		git.add().addFilepattern("symlink").call();

		DirCache cache = db.lockDirCache();
		cache.getEntry("executable").setFileMode(FileMode.EXECUTABLE_FILE);
		cache.getEntry("symlink").setFileMode(FileMode.SYMLINK);
		cache.write();
		cache.commit();
		cache.unlock();

		git.commit().setMessage("three files with different modes").call();

		final byte[] zipData = CLIGitCommand.rawExecute( //
				"git archive master", db);
		writeRaw("zip-with-modes.zip", zipData);
		assertContainsEntryWithMode("zip-with-modes.zip", "-rw-", "plain");
		assertContainsEntryWithMode("zip-with-modes.zip", "-rwx", "executable");
		assertContainsEntryWithMode("zip-with-modes.zip", "l", "symlink");
	}

	@Test
	public void testTarPreservesMode() throws Exception {
		writeTrashFile("plain", "a file with content");
		writeTrashFile("executable", "an executable file");
		writeTrashFile("symlink", "plain");
		git.add().addFilepattern("plain").call();
		git.add().addFilepattern("executable").call();
		git.add().addFilepattern("symlink").call();

		DirCache cache = db.lockDirCache();
		cache.getEntry("executable").setFileMode(FileMode.EXECUTABLE_FILE);
		cache.getEntry("symlink").setFileMode(FileMode.SYMLINK);
		cache.write();
		cache.commit();
		cache.unlock();

		git.commit().setMessage("three files with different modes").call();

		final byte[] archive = CLIGitCommand.rawExecute( //
				"git archive --format=tar master", db);
		writeRaw("with-modes.tar", archive);
		assertTarContainsEntry("with-modes.tar", "-rw-r--r--", "plain");
		assertTarContainsEntry("with-modes.tar", "-rwxr-xr-x", "executable");
		assertTarContainsEntry("with-modes.tar", "l", "symlink -> plain");
	}

	@Test
	public void testArchivePreservesContent() throws Exception {
		final String payload = "“The quick brown fox jumps over the lazy dog!”";
		writeTrashFile("xyzzy", payload);
		git.add().addFilepattern("xyzzy").call();
		git.commit().setMessage("add file with content").call();

		final byte[] result = CLIGitCommand.rawExecute( //
				"git archive HEAD", db);
		assertArrayEquals(new String[] { payload }, //
				zipEntryContent(result, "xyzzy"));
	}

	@Test
	public void testTarPreservesContent() throws Exception {
		final String payload = "“The quick brown fox jumps over the lazy dog!”";
		writeTrashFile("xyzzy", payload);
		git.add().addFilepattern("xyzzy").call();
		git.commit().setMessage("add file with content").call();

		final byte[] result = CLIGitCommand.rawExecute( //
				"git archive --format=tar HEAD", db);
		assertArrayEquals(new String[] { payload }, //
				tarEntryContent(result, "xyzzy"));
	}

	private Process spawnAssumingCommandPresent(String... cmdline) {
		final File cwd = db.getWorkTree();
		final ProcessBuilder procBuilder = new ProcessBuilder(cmdline) //
				.directory(cwd) //
				.redirectErrorStream(true);
		Process proc = null;
		try {
			proc = procBuilder.start();
		} catch (IOException e) {
			// On machines without `cmdline[0]`, let the test pass.
			assumeNoException(e);
		}

		return proc;
	}

	private BufferedReader readFromProcess(Process proc) throws Exception {
		return new BufferedReader( //
				new InputStreamReader(proc.getInputStream(), "UTF-8"));
	}

	private void grepForEntry(String name, String mode, String... cmdline) //
			throws Exception {
		final Process proc = spawnAssumingCommandPresent(cmdline);
		proc.getOutputStream().close();
		final BufferedReader reader = readFromProcess(proc);
		try {
			String line;
			while ((line = reader.readLine()) != null)
				if (line.startsWith(mode) && line.endsWith(name))
					// found it!
					return;
			fail("expected entry " + name + " with mode " + mode + " but found none");
		} finally {
			proc.getOutputStream().close();
			proc.destroy();
		}
	}

	private void assertContainsEntryWithMode(String zipFilename, String mode, String name) //
			throws Exception {
		grepForEntry(name, mode, "zipinfo", zipFilename);
	}

	private void assertTarContainsEntry(String tarfile, String mode, String name) //
			throws Exception {
		grepForEntry(name, mode, "tar", "tvf", tarfile);
	}

	private void writeRaw(String filename, byte[] data) //
			throws IOException {
		final File path = new File(db.getWorkTree(), filename);
		final OutputStream out = new FileOutputStream(path);
		try {
			out.write(data);
		} finally {
			out.close();
		}
	}

	private static String[] listZipEntries(byte[] zipData) throws IOException {
		final List<String> l = new ArrayList<String>();
		final ZipInputStream in = new ZipInputStream( //
				new ByteArrayInputStream(zipData));

		ZipEntry e;
		while ((e = in.getNextEntry()) != null)
			l.add(e.getName());
		in.close();
		return l.toArray(new String[l.size()]);
	}

	private static Future<Object> writeAsync(final OutputStream stream, final byte[] data) {
		final ExecutorService executor = Executors.newSingleThreadExecutor();

		return executor.submit(new Callable<Object>() { //
			public Object call() throws IOException {
				try {
					stream.write(data);
					return null;
				} finally {
					stream.close();
				}
			}
		});
	}

	private String[] listTarEntries(byte[] tarData) throws Exception {
		final List<String> l = new ArrayList<String>();
		final Process proc = spawnAssumingCommandPresent("tar", "tf", "-");
		final BufferedReader reader = readFromProcess(proc);
		final OutputStream out = proc.getOutputStream();

		// Dump tarball to tar stdin in background
		final Future<?> writing = writeAsync(out, tarData);

		try {
			String line;
			while ((line = reader.readLine()) != null)
				l.add(line);

			return l.toArray(new String[l.size()]);
		} finally {
			writing.get();
			reader.close();
			proc.destroy();
		}
	}

	private static String[] zipEntryContent(byte[] zipData, String path) //
			throws IOException {
		final ZipInputStream in = new ZipInputStream( //
				new ByteArrayInputStream(zipData));
		ZipEntry e;
		while ((e = in.getNextEntry()) != null) {
			if (!e.getName().equals(path))
				continue;

			// found!
			final List<String> l = new ArrayList<String>();
			final BufferedReader reader = new BufferedReader( //
					new InputStreamReader(in, "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null)
				l.add(line);
			return l.toArray(new String[l.size()]);
		}

		// not found
		return null;
	}

	private String[] tarEntryContent(byte[] tarData, String path) //
			throws Exception {
		final List<String> l = new ArrayList<String>();
		final Process proc = spawnAssumingCommandPresent("tar", "Oxf", "-", path);
		final BufferedReader reader = readFromProcess(proc);
		final OutputStream out = proc.getOutputStream();
		final Future<?> writing = writeAsync(out, tarData);

		try {
			String line;
			while ((line = reader.readLine()) != null)
				l.add(line);

			return l.toArray(new String[l.size()]);
		} finally {
			writing.get();
			reader.close();
			proc.destroy();
		}
	}
}
