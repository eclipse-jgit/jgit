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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.junit.Before;
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

	@Test
	public void testEmptyArchive() throws Exception {
		byte[] result = CLIGitCommand
				.executeRaw("git archive --format=zip " + emptyTree, db)
				.outBytes();
		assertArrayEquals(new String[0], listZipEntries(result));
	}

	@Test
	public void testEmptyTar() throws Exception {
		byte[] result = CLIGitCommand
				.executeRaw("git archive --format=tar " + emptyTree, db)
				.outBytes();
		assertArrayEquals(new String[0], listTarEntries(result));
	}

	@Test
	public void testUnrecognizedFormat() throws Exception {
		String[] expect = new String[] {
				"fatal: Unknown archive format 'nonsense'", "" };
		String[] actual = executeUnchecked(
				"git archive --format=nonsense " + emptyTree);
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

		byte[] result = CLIGitCommand
				.executeRaw("git archive --format=zip HEAD", db).outBytes();
		assertArrayEquals(new String[] { "a", "c" }, listZipEntries(result));
	}

	private void commitGreeting() throws Exception {
		writeTrashFile("greeting", "hello, world!");
		git.add().addFilepattern("greeting").call();
		git.commit().setMessage("a commit with a file").call();
	}

	@Test
	public void testDefaultFormatIsTar() throws Exception {
		commitGreeting();
		byte[] result = CLIGitCommand.executeRaw("git archive HEAD", db)
				.outBytes();
		assertArrayEquals(new String[] { "greeting" }, listTarEntries(result));
	}

	private static String shellQuote(String s) {
		return "'" + s.replace("'", "'\\''") + "'";
	}

	@Test
	public void testFormatOverridesFilename() throws Exception {
		File archive = new File(db.getWorkTree(), "format-overrides-name.tar");
		String path = archive.getAbsolutePath();

		commitGreeting();
		assertArrayEquals(new String[] { "" },
				execute("git archive " + "--format=zip "
						+ shellQuote("--output=" + path) + " " + "HEAD"));
		assertContainsEntryWithMode(path, "", "greeting");
		assertIsZip(archive);
	}

	@Test
	public void testUnrecognizedExtensionMeansTar() throws Exception {
		File archive = new File(db.getWorkTree(), "example.txt");
		String path = archive.getAbsolutePath();

		commitGreeting();
		assertArrayEquals(new String[] { "" }, execute("git archive "
				+ shellQuote("--output=" + path) + " " + "HEAD"));
		assertTarContainsEntry(path, "", "greeting");
		assertIsTar(archive);
	}

	@Test
	public void testNoExtensionMeansTar() throws Exception {
		File archive = new File(db.getWorkTree(), "example");
		String path = archive.getAbsolutePath();

		commitGreeting();
		assertArrayEquals(new String[] { "" }, execute("git archive "
				+ shellQuote("--output=" + path) + " " + "HEAD"));
		assertIsTar(archive);
	}

	@Test
	public void testExtensionMatchIsAnchored() throws Exception {
		File archive = new File(db.getWorkTree(), "two-extensions.zip.bak");
		String path = archive.getAbsolutePath();

		commitGreeting();
		assertArrayEquals(new String[] { "" }, execute("git archive "
				+ shellQuote("--output=" + path) + " " + "HEAD"));
		assertIsTar(archive);
	}

	@Test
	public void testZipExtension() throws Exception {
		File archiveWithDot = new File(db.getWorkTree(), "greeting.zip");
		File archiveNoDot = new File(db.getWorkTree(), "greetingzip");

		commitGreeting();
		execute("git archive "
				+ shellQuote("--output=" + archiveWithDot.getAbsolutePath())
				+ " " + "HEAD");
		execute("git archive "
				+ shellQuote("--output=" + archiveNoDot.getAbsolutePath()) + " "
				+ "HEAD");
		assertIsZip(archiveWithDot);
		assertIsTar(archiveNoDot);
	}

	@Test
	public void testTarExtension() throws Exception {
		File archive = new File(db.getWorkTree(), "tarball.tar");
		String path = archive.getAbsolutePath();

		commitGreeting();
		assertArrayEquals(new String[] { "" }, execute("git archive "
				+ shellQuote("--output=" + path) + " " + "HEAD"));
		assertIsTar(archive);
	}

	@Test
	public void testTgzExtensions() throws Exception {
		commitGreeting();

		for (String ext : Arrays.asList("tar.gz", "tgz")) {
			File archiveWithDot = new File(db.getWorkTree(), "tarball." + ext);
			File archiveNoDot = new File(db.getWorkTree(), "tarball" + ext);

			execute("git archive "
					+ shellQuote("--output=" + archiveWithDot.getAbsolutePath())
					+ " " + "HEAD");
			execute("git archive "
					+ shellQuote("--output=" + archiveNoDot.getAbsolutePath())
					+ " " + "HEAD");
			assertIsGzip(archiveWithDot);
			assertIsTar(archiveNoDot);
		}
	}

	@Test
	public void testTbz2Extension() throws Exception {
		commitGreeting();

		for (String ext : Arrays.asList("tar.bz2", "tbz", "tbz2")) {
			File archiveWithDot = new File(db.getWorkTree(), "tarball." + ext);
			File archiveNoDot = new File(db.getWorkTree(), "tarball" + ext);

			execute("git archive "
					+ shellQuote("--output=" + archiveWithDot.getAbsolutePath())
					+ " " + "HEAD");
			execute("git archive "
					+ shellQuote("--output=" + archiveNoDot.getAbsolutePath())
					+ " " + "HEAD");
			assertIsBzip2(archiveWithDot);
			assertIsTar(archiveNoDot);
		}
	}

	@Test
	public void testTxzExtension() throws Exception {
		commitGreeting();

		for (String ext : Arrays.asList("tar.xz", "txz")) {
			File archiveWithDot = new File(db.getWorkTree(), "tarball." + ext);
			File archiveNoDot = new File(db.getWorkTree(), "tarball" + ext);

			execute("git archive "
					+ shellQuote("--output=" + archiveWithDot.getAbsolutePath())
					+ " " + "HEAD");
			execute("git archive "
					+ shellQuote("--output=" + archiveNoDot.getAbsolutePath())
					+ " " + "HEAD");
			assertIsXz(archiveWithDot);
			assertIsTar(archiveNoDot);
		}
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

		byte[] result = CLIGitCommand
				.executeRaw("git archive --format=zip master", db).outBytes();
		String[] expect = { "a", "b.c", "b0c", "b/", "b/a", "b/b", "c" };
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

		byte[] result = CLIGitCommand
				.executeRaw("git archive --format=tar master", db).outBytes();
		String[] expect = { "a", "b.c", "b0c", "b/", "b/a", "b/b", "c" };
		String[] actual = listTarEntries(result);

		Arrays.sort(expect);
		Arrays.sort(actual);
		assertArrayEquals(expect, actual);
	}

	private void commitBazAndFooSlashBar() throws Exception {
		writeTrashFile("baz", "a file");
		writeTrashFile("foo/bar", "another file");
		git.add().addFilepattern("baz").call();
		git.add().addFilepattern("foo").call();
		git.commit().setMessage("sample commit").call();
	}

	@Test
	public void testArchivePrefixOption() throws Exception {
		commitBazAndFooSlashBar();
		byte[] result = CLIGitCommand
				.executeRaw("git archive --prefix=x/ --format=zip master", db)
				.outBytes();
		String[] expect = { "x/", "x/baz", "x/foo/", "x/foo/bar" };
		String[] actual = listZipEntries(result);

		Arrays.sort(expect);
		Arrays.sort(actual);
		assertArrayEquals(expect, actual);
	}

	@Test
	public void testTarPrefixOption() throws Exception {
		commitBazAndFooSlashBar();
		byte[] result = CLIGitCommand
				.executeRaw("git archive --prefix=x/ --format=tar master", db)
				.outBytes();
		String[] expect = { "x/", "x/baz", "x/foo/", "x/foo/bar" };
		String[] actual = listTarEntries(result);

		Arrays.sort(expect);
		Arrays.sort(actual);
		assertArrayEquals(expect, actual);
	}

	private void commitFoo() throws Exception {
		writeTrashFile("foo", "a file");
		git.add().addFilepattern("foo").call();
		git.commit().setMessage("boring commit").call();
	}

	@Test
	public void testPrefixDoesNotNormalizeDoubleSlash() throws Exception {
		commitFoo();
		byte[] result = CLIGitCommand
				.executeRaw("git archive --prefix=x// --format=zip master", db)
				.outBytes();
		String[] expect = { "x/", "x//foo" };
		assertArrayEquals(expect, listZipEntries(result));
	}

	@Test
	public void testPrefixDoesNotNormalizeDoubleSlashInTar() throws Exception {
		commitFoo();
		byte[] result = CLIGitCommand
				.executeRaw("git archive --prefix=x// --format=tar master", db)
				.outBytes();
		String[] expect = { "x/", "x//foo" };
		assertArrayEquals(expect, listTarEntries(result));
	}

	/**
	 * The prefix passed to "git archive" need not end with '/'. In practice it
	 * is not very common to have a nonempty prefix that does not name a
	 * directory (and hence end with /), but since git has historically
	 * supported other prefixes, we do, too.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPrefixWithoutTrailingSlash() throws Exception {
		commitBazAndFooSlashBar();
		byte[] result = CLIGitCommand
				.executeRaw("git archive --prefix=my- --format=zip master", db)
				.outBytes();
		String[] expect = { "my-baz", "my-foo/", "my-foo/bar" };
		String[] actual = listZipEntries(result);

		Arrays.sort(expect);
		Arrays.sort(actual);
		assertArrayEquals(expect, actual);
	}

	@Test
	public void testTarPrefixWithoutTrailingSlash() throws Exception {
		commitBazAndFooSlashBar();
		byte[] result = CLIGitCommand
				.executeRaw("git archive --prefix=my- --format=tar master", db)
				.outBytes();
		String[] expect = { "my-baz", "my-foo/", "my-foo/bar" };
		String[] actual = listTarEntries(result);

		Arrays.sort(expect);
		Arrays.sort(actual);
		assertArrayEquals(expect, actual);
	}

	@Test
	public void testArchiveIncludesSubmoduleDirectory() throws Exception {
		writeTrashFile("a", "a file with content!");
		writeTrashFile("c", "after submodule");
		git.add().addFilepattern("a").call();
		git.add().addFilepattern("c").call();
		git.commit().setMessage("initial commit").call();
		git.submoduleAdd().setURI("./.").setPath("b").call().close();
		git.commit().setMessage("add submodule").call();

		byte[] result = CLIGitCommand
				.executeRaw("git archive --format=zip master", db).outBytes();
		String[] expect = { ".gitmodules", "a", "b/", "c" };
		String[] actual = listZipEntries(result);

		Arrays.sort(expect);
		Arrays.sort(actual);
		assertArrayEquals(expect, actual);
	}

	@Test
	public void testTarIncludesSubmoduleDirectory() throws Exception {
		writeTrashFile("a", "a file with content!");
		writeTrashFile("c", "after submodule");
		git.add().addFilepattern("a").call();
		git.add().addFilepattern("c").call();
		git.commit().setMessage("initial commit").call();
		git.submoduleAdd().setURI("./.").setPath("b").call().close();
		git.commit().setMessage("add submodule").call();

		byte[] result = CLIGitCommand
				.executeRaw("git archive --format=tar master", db).outBytes();
		String[] expect = { ".gitmodules", "a", "b/", "c" };
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
		writeTrashFile("dir/content", "clutter in a subdir");
		git.add().addFilepattern("plain").call();
		git.add().addFilepattern("executable").call();
		git.add().addFilepattern("symlink").call();
		git.add().addFilepattern("dir").call();

		DirCache cache = db.lockDirCache();
		cache.getEntry("executable").setFileMode(FileMode.EXECUTABLE_FILE);
		cache.getEntry("symlink").setFileMode(FileMode.SYMLINK);
		cache.write();
		cache.commit();
		cache.unlock();

		git.commit().setMessage("three files with different modes").call();

		byte[] zipData = CLIGitCommand
				.executeRaw("git archive --format=zip master", db).outBytes();
		writeRaw("zip-with-modes.zip", zipData);
		assertContainsEntryWithMode("zip-with-modes.zip", "-rw-", "plain");
		assertContainsEntryWithMode("zip-with-modes.zip", "-rwx", "executable");
		assertContainsEntryWithMode("zip-with-modes.zip", "l", "symlink");
		assertContainsEntryWithMode("zip-with-modes.zip", "-rw-", "dir/");
	}

	@Test
	public void testTarPreservesMode() throws Exception {
		writeTrashFile("plain", "a file with content");
		writeTrashFile("executable", "an executable file");
		writeTrashFile("symlink", "plain");
		writeTrashFile("dir/content", "clutter in a subdir");
		git.add().addFilepattern("plain").call();
		git.add().addFilepattern("executable").call();
		git.add().addFilepattern("symlink").call();
		git.add().addFilepattern("dir").call();

		DirCache cache = db.lockDirCache();
		cache.getEntry("executable").setFileMode(FileMode.EXECUTABLE_FILE);
		cache.getEntry("symlink").setFileMode(FileMode.SYMLINK);
		cache.write();
		cache.commit();
		cache.unlock();

		git.commit().setMessage("three files with different modes").call();

		byte[] archive = CLIGitCommand
				.executeRaw("git archive --format=tar master", db).outBytes();
		writeRaw("with-modes.tar", archive);
		assertTarContainsEntry("with-modes.tar", "-rw-r--r--", "plain");
		assertTarContainsEntry("with-modes.tar", "-rwxr-xr-x", "executable");
		assertTarContainsEntry("with-modes.tar", "l", "symlink -> plain");
		assertTarContainsEntry("with-modes.tar", "drwxr-xr-x", "dir/");
	}

	@Test
	public void testArchiveWithLongFilename() throws Exception {
		StringBuilder filename = new StringBuilder();
		List<String> l = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			filename.append("1234567890/");
			l.add(filename.toString());
		}
		filename.append("1234567890");
		l.add(filename.toString());
		writeTrashFile(filename.toString(), "file with long path");
		git.add().addFilepattern("1234567890").call();
		git.commit().setMessage("file with long name").call();

		byte[] result = CLIGitCommand
				.executeRaw("git archive --format=zip HEAD", db).outBytes();
		assertArrayEquals(l.toArray(new String[0]), listZipEntries(result));
	}

	@Test
	public void testTarWithLongFilename() throws Exception {
		StringBuilder filename = new StringBuilder();
		List<String> l = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			filename.append("1234567890/");
			l.add(filename.toString());
		}
		filename.append("1234567890");
		l.add(filename.toString());
		writeTrashFile(filename.toString(), "file with long path");
		git.add().addFilepattern("1234567890").call();
		git.commit().setMessage("file with long name").call();

		byte[] result = CLIGitCommand
				.executeRaw("git archive --format=tar HEAD", db).outBytes();
		assertArrayEquals(l.toArray(new String[0]), listTarEntries(result));
	}

	@Test
	public void testArchivePreservesContent() throws Exception {
		String payload = "“The quick brown fox jumps over the lazy dog!”";
		writeTrashFile("xyzzy", payload);
		git.add().addFilepattern("xyzzy").call();
		git.commit().setMessage("add file with content").call();

		byte[] result = CLIGitCommand
				.executeRaw("git archive --format=zip HEAD", db).outBytes();
		assertArrayEquals(new String[] { payload },
				zipEntryContent(result, "xyzzy"));
	}

	@Test
	public void testTarPreservesContent() throws Exception {
		String payload = "“The quick brown fox jumps over the lazy dog!”";
		writeTrashFile("xyzzy", payload);
		git.add().addFilepattern("xyzzy").call();
		git.commit().setMessage("add file with content").call();

		byte[] result = CLIGitCommand
				.executeRaw("git archive --format=tar HEAD", db).outBytes();
		assertArrayEquals(new String[] { payload },
				tarEntryContent(result, "xyzzy"));
	}

	private Process spawnAssumingCommandPresent(String... cmdline) {
		File cwd = db.getWorkTree();
		ProcessBuilder procBuilder = new ProcessBuilder(cmdline).directory(cwd)
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
		return new BufferedReader(
				new InputStreamReader(proc.getInputStream(), UTF_8));
	}

	private void grepForEntry(String name, String mode, String... cmdline)
			throws Exception {
		Process proc = spawnAssumingCommandPresent(cmdline);
		proc.getOutputStream().close();
		BufferedReader reader = readFromProcess(proc);
		try {
			String line;
			while ((line = reader.readLine()) != null)
				if (line.startsWith(mode) && line.endsWith(name))
					// found it!
					return;
			fail("expected entry " + name + " with mode " + mode
					+ " but found none");
		} finally {
			proc.getOutputStream().close();
			proc.destroy();
		}
	}

	private void assertMagic(long offset, byte[] magicBytes, File file)
			throws Exception {
		try (BufferedInputStream in = new BufferedInputStream(
				new FileInputStream(file))) {
			if (offset > 0) {
				long skipped = in.skip(offset);
				assertEquals(offset, skipped);
			}

			byte[] actual = new byte[magicBytes.length];
			in.read(actual);
			assertArrayEquals(magicBytes, actual);
		}
	}

	private void assertMagic(byte[] magicBytes, File file) throws Exception {
		assertMagic(0, magicBytes, file);
	}

	private void assertIsTar(File file) throws Exception {
		assertMagic(257, new byte[] { 'u', 's', 't', 'a', 'r', 0 }, file);
	}

	private void assertIsZip(File file) throws Exception {
		assertMagic(new byte[] { 'P', 'K', 3, 4 }, file);
	}

	private void assertIsGzip(File file) throws Exception {
		assertMagic(new byte[] { 037, (byte) 0213 }, file);
	}

	private void assertIsBzip2(File file) throws Exception {
		assertMagic(new byte[] { 'B', 'Z', 'h' }, file);
	}

	private void assertIsXz(File file) throws Exception {
		assertMagic(new byte[] { (byte) 0xfd, '7', 'z', 'X', 'Z', 0 }, file);
	}

	private void assertContainsEntryWithMode(String zipFilename, String mode,
			String name) throws Exception {
		grepForEntry(name, mode, "zipinfo", zipFilename);
	}

	private void assertTarContainsEntry(String tarfile, String mode,
			String name) throws Exception {
		grepForEntry(name, mode, "tar", "tvf", tarfile);
	}

	private void writeRaw(String filename, byte[] data) throws IOException {
		File path = new File(db.getWorkTree(), filename);
		try (OutputStream out = new FileOutputStream(path)) {
			out.write(data);
		}
	}

	private static String[] listZipEntries(byte[] zipData) throws IOException {
		List<String> l = new ArrayList<>();
		try (ZipInputStream in = new ZipInputStream(
				new ByteArrayInputStream(zipData))) {
			ZipEntry e;
			while ((e = in.getNextEntry()) != null)
				l.add(e.getName());
		}
		return l.toArray(new String[0]);
	}

	private static Future<Object> writeAsync(OutputStream stream, byte[] data) {
		ExecutorService executor = Executors.newSingleThreadExecutor();

		return executor.submit(() -> {
			try {
				stream.write(data);
				return null;
			} finally {
				stream.close();
			}
		});
	}

	private String[] listTarEntries(byte[] tarData) throws Exception {
		List<String> l = new ArrayList<>();
		Process proc = spawnAssumingCommandPresent("tar", "tf", "-");
		try (BufferedReader reader = readFromProcess(proc)) {
			OutputStream out = proc.getOutputStream();

			// Dump tarball to tar stdin in background
			Future<?> writing = writeAsync(out, tarData);

			try {
				String line;
				while ((line = reader.readLine()) != null)
					l.add(line);

				return l.toArray(new String[0]);
			} finally {
				writing.get();
				proc.destroy();
			}
		}
	}

	private static String[] zipEntryContent(byte[] zipData, String path)
			throws IOException {
		ZipInputStream in = new ZipInputStream(
				new ByteArrayInputStream(zipData));
		ZipEntry e;
		while ((e = in.getNextEntry()) != null) {
			if (!e.getName().equals(path))
				continue;

			// found!
			List<String> l = new ArrayList<>();
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(in, UTF_8));
			String line;
			while ((line = reader.readLine()) != null)
				l.add(line);
			return l.toArray(new String[0]);
		}

		// not found
		return null;
	}

	private String[] tarEntryContent(byte[] tarData, String path)
			throws Exception {
		List<String> l = new ArrayList<>();
		Process proc = spawnAssumingCommandPresent("tar", "Oxf", "-", path);
		try (BufferedReader reader = readFromProcess(proc)) {
			OutputStream out = proc.getOutputStream();
			Future<?> writing = writeAsync(out, tarData);

			try {
				String line;
				while ((line = reader.readLine()) != null)
					l.add(line);

				return l.toArray(new String[0]);
			} finally {
				writing.get();
				proc.destroy();
			}
		}
	}
}
