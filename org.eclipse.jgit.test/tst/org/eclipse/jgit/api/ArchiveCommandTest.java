/*
 * Copyright (C) 2014, Shaul Zorea <shaulzorea@gmail.com>
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.beans.Statement;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NoMessageException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.archive.ArchiveFormats;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ArchiveCommandTest extends RepositoryTestCase {

	// archives store timestamp with 1 second resolution
	private static final int WAIT = 2000;
	private static final String UNEXPECTED_ARCHIVE_SIZE  = "Unexpected archive size";
	private static final String UNEXPECTED_FILE_CONTENTS = "Unexpected file contents";
	private static final String UNEXPECTED_TREE_CONTENTS = "Unexpected tree contents";
	private static final String UNEXPECTED_LAST_MODIFIED =
			"Unexpected lastModified mocked by MockSystemReader, truncated to 1 second";
	private static final String UNEXPECTED_DIFFERENT_HASH = "Unexpected different hash";

	private MockFormat format = null;

	@Before
	public void setup() {
		format = new MockFormat();
		ArchiveCommand.registerFormat(format.SUFFIXES.get(0), format);
		ArchiveFormats.registerAll();
	}

	@Override
	@After
	public void tearDown() {
		ArchiveCommand.unregisterFormat(format.SUFFIXES.get(0));
		ArchiveFormats.unregisterAll();
	}

	@Test
	public void archiveHeadAllFiles() throws IOException, GitAPIException {
		try (Git git = new Git(db)) {
			createTestContent(git);

			git.archive().setOutputStream(new MockOutputStream())
					.setFormat(format.SUFFIXES.get(0))
					.setTree(git.getRepository().resolve("HEAD")).call();

			assertEquals(UNEXPECTED_ARCHIVE_SIZE, 2, format.size());
			assertEquals(UNEXPECTED_FILE_CONTENTS, "content_1_2", format.getByPath("file_1.txt"));
			assertEquals(UNEXPECTED_FILE_CONTENTS, "content_2_2", format.getByPath("file_2.txt"));
		}
	}

	@Test
	public void archiveHeadSpecificPath() throws IOException, GitAPIException {
		try (Git git = new Git(db)) {
			writeTrashFile("file_1.txt", "content_1_1");
			git.add().addFilepattern("file_1.txt").call();
			git.commit().setMessage("create file").call();

			writeTrashFile("file_1.txt", "content_1_2");
			String expectedFilePath = "some_directory/file_2.txt";
			writeTrashFile(expectedFilePath, "content_2_2");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("updated file").call();

			git.archive().setOutputStream(new MockOutputStream())
					.setFormat(format.SUFFIXES.get(0))
					.setTree(git.getRepository().resolve("HEAD"))
					.setPaths(expectedFilePath).call();

			assertEquals(UNEXPECTED_ARCHIVE_SIZE, 2, format.size());
			assertEquals(UNEXPECTED_FILE_CONTENTS, "content_2_2", format.getByPath(expectedFilePath));
			assertNull(UNEXPECTED_TREE_CONTENTS, format.getByPath("some_directory"));
		}
	}

	@Test
	public void archiveByIdSpecificFile() throws IOException, GitAPIException {
		try (Git git = new Git(db)) {
			writeTrashFile("file_1.txt", "content_1_1");
			git.add().addFilepattern("file_1.txt").call();
			RevCommit first = git.commit().setMessage("create file").call();

			writeTrashFile("file_1.txt", "content_1_2");
			String expectedFilePath = "some_directory/file_2.txt";
			writeTrashFile(expectedFilePath, "content_2_2");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("updated file").call();

			Map<String, Object> options = new HashMap<>();
			Integer opt = Integer.valueOf(42);
			options.put("foo", opt);
			MockOutputStream out = new MockOutputStream();
			git.archive().setOutputStream(out)
					.setFormat(format.SUFFIXES.get(0))
					.setFormatOptions(options)
					.setTree(first)
					.setPaths("file_1.txt").call();

			assertEquals(opt.intValue(), out.getFoo());
			assertEquals(UNEXPECTED_ARCHIVE_SIZE, 1, format.size());
			assertEquals(UNEXPECTED_FILE_CONTENTS, "content_1_1", format.getByPath("file_1.txt"));
		}
	}

	@Test
	public void archiveByDirectoryPath() throws GitAPIException, IOException {
		try (Git git = new Git(db)) {
			writeTrashFile("file_0.txt", "content_0_1");
			git.add().addFilepattern("file_0.txt").call();
			git.commit().setMessage("commit_1").call();

			writeTrashFile("file_0.txt", "content_0_2");
			String expectedFilePath1 = "some_directory/file_1.txt";
			writeTrashFile(expectedFilePath1, "content_1_2");
			String expectedFilePath2 = "some_directory/file_2.txt";
			writeTrashFile(expectedFilePath2, "content_2_2");
		        String expectedFilePath3 = "some_directory/nested_directory/file_3.txt";
			writeTrashFile(expectedFilePath3, "content_3_2");
			git.add().addFilepattern(".").call();
			git.commit().setMessage("commit_2").call();
			git.archive().setOutputStream(new MockOutputStream())
					.setFormat(format.SUFFIXES.get(0))
					.setTree(git.getRepository().resolve("HEAD"))
					.setPaths("some_directory/").call();

			assertEquals(UNEXPECTED_ARCHIVE_SIZE, 5, format.size());
			assertEquals(UNEXPECTED_FILE_CONTENTS, "content_1_2", format.getByPath(expectedFilePath1));
			assertEquals(UNEXPECTED_FILE_CONTENTS, "content_2_2", format.getByPath(expectedFilePath2));
			assertEquals(UNEXPECTED_FILE_CONTENTS, "content_3_2", format.getByPath(expectedFilePath3));
			assertNull(UNEXPECTED_TREE_CONTENTS, format.getByPath("some_directory"));
			assertNull(UNEXPECTED_TREE_CONTENTS, format.getByPath("some_directory/nested_directory"));
		}
	}

	@Test
	public void archiveHeadAllFilesTarTimestamps() throws Exception {
		try (Git git = new Git(db)) {
			createTestContent(git);
			String fmt = "tar";
			File archive = new File(getTemporaryDirectory(),
					"archive." + format);
			archive(git, archive, fmt);
			ObjectId hash1 = ObjectId.fromRaw(IO.readFully(archive));

			try (InputStream fi = Files.newInputStream(archive.toPath());
					InputStream bi = new BufferedInputStream(fi);
					ArchiveInputStream o = new TarArchiveInputStream(bi)) {
				assertEntries(o);
			}

			Thread.sleep(WAIT);
			archive(git, archive, fmt);
			assertEquals(UNEXPECTED_DIFFERENT_HASH, hash1,
					ObjectId.fromRaw(IO.readFully(archive)));
		}
	}

	@Test
	public void archiveHeadAllFilesTgzTimestamps() throws Exception {
		try (Git git = new Git(db)) {
			createTestContent(git);
			String fmt = "tgz";
			File archive = new File(getTemporaryDirectory(),
					"archive." + fmt);
			archive(git, archive, fmt);
			ObjectId hash1 = ObjectId.fromRaw(IO.readFully(archive));

			try (InputStream fi = Files.newInputStream(archive.toPath());
					InputStream bi = new BufferedInputStream(fi);
					InputStream gzi = new GzipCompressorInputStream(bi);
					ArchiveInputStream o = new TarArchiveInputStream(gzi)) {
				assertEntries(o);
			}

			Thread.sleep(WAIT);
			archive(git, archive, fmt);
			assertEquals(UNEXPECTED_DIFFERENT_HASH, hash1,
					ObjectId.fromRaw(IO.readFully(archive)));
		}
	}

	@Test
	public void archiveHeadAllFilesTbz2Timestamps() throws Exception {
		try (Git git = new Git(db)) {
			createTestContent(git);
			String fmt = "tbz2";
			File archive = new File(getTemporaryDirectory(),
					"archive." + fmt);
			archive(git, archive, fmt);
			ObjectId hash1 = ObjectId.fromRaw(IO.readFully(archive));

			try (InputStream fi = Files.newInputStream(archive.toPath());
					InputStream bi = new BufferedInputStream(fi);
					InputStream gzi = new BZip2CompressorInputStream(bi);
					ArchiveInputStream o = new TarArchiveInputStream(gzi)) {
				assertEntries(o);
			}

			Thread.sleep(WAIT);
			archive(git, archive, fmt);
			assertEquals(UNEXPECTED_DIFFERENT_HASH, hash1,
					ObjectId.fromRaw(IO.readFully(archive)));
		}
	}

	@Test
	public void archiveHeadAllFilesTxzTimestamps() throws Exception {
		try (Git git = new Git(db)) {
			createTestContent(git);
			String fmt = "txz";
			File archive = new File(getTemporaryDirectory(), "archive." + fmt);
			archive(git, archive, fmt);
			ObjectId hash1 = ObjectId.fromRaw(IO.readFully(archive));

			try (InputStream fi = Files.newInputStream(archive.toPath());
					InputStream bi = new BufferedInputStream(fi);
					InputStream gzi = new XZCompressorInputStream(bi);
					ArchiveInputStream o = new TarArchiveInputStream(gzi)) {
				assertEntries(o);
			}

			Thread.sleep(WAIT);
			archive(git, archive, fmt);
			assertEquals(UNEXPECTED_DIFFERENT_HASH, hash1,
					ObjectId.fromRaw(IO.readFully(archive)));
		}
	}

	@Test
	public void archiveHeadAllFilesZipTimestamps() throws Exception {
		try (Git git = new Git(db)) {
			createTestContent(git);
			String fmt = "zip";
			File archive = new File(getTemporaryDirectory(), "archive." + fmt);
			archive(git, archive, fmt);
			ObjectId hash1 = ObjectId.fromRaw(IO.readFully(archive));

			try (InputStream fi = Files.newInputStream(archive.toPath());
					InputStream bi = new BufferedInputStream(fi);
					ArchiveInputStream o = new ZipArchiveInputStream(bi)) {
				assertEntries(o);
			}

			Thread.sleep(WAIT);
			archive(git, archive, fmt);
			assertEquals(UNEXPECTED_DIFFERENT_HASH, hash1,
					ObjectId.fromRaw(IO.readFully(archive)));
		}
	}

	private void createTestContent(Git git) throws IOException, GitAPIException,
			NoFilepatternException, NoHeadException, NoMessageException,
			UnmergedPathsException, ConcurrentRefUpdateException,
			WrongRepositoryStateException, AbortedByHookException {
		writeTrashFile("file_1.txt", "content_1_1");
		git.add().addFilepattern("file_1.txt").call();
		git.commit().setMessage("create file").call();

		writeTrashFile("file_1.txt", "content_1_2");
		writeTrashFile("file_2.txt", "content_2_2");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("updated file").call();
	}

	private static void archive(Git git, File archive, String fmt)
			throws GitAPIException,
			FileNotFoundException, AmbiguousObjectException,
			IncorrectObjectTypeException, IOException {
		git.archive().setOutputStream(new FileOutputStream(archive))
				.setFormat(fmt)
				.setTree(git.getRepository().resolve("HEAD")).call();
	}

	private static void assertEntries(ArchiveInputStream o) throws IOException {
		ArchiveEntry e;
		int n = 0;
		while ((e = o.getNextEntry()) != null) {
			n++;
			assertEquals(UNEXPECTED_LAST_MODIFIED,
					(1250379778668L / 1000L) * 1000L,
					e.getLastModifiedDate().getTime());
		}
		assertEquals(UNEXPECTED_ARCHIVE_SIZE, 2, n);
	}

	private static class MockFormat
			implements ArchiveCommand.Format<MockOutputStream> {

		private Map<String, String> entries = new HashMap<>();

		private int size() {
			return entries.size();
		}

		private String getByPath(String path) {
			return entries.get(path);
		}

		private final List<String> SUFFIXES = Collections
				.unmodifiableList(Arrays.asList(".mck"));

		@Override
		public MockOutputStream createArchiveOutputStream(OutputStream s)
				throws IOException {
			return createArchiveOutputStream(s,
					Collections.<String, Object> emptyMap());
		}

		@Override
		public MockOutputStream createArchiveOutputStream(OutputStream s,
				Map<String, Object> o) throws IOException {
			for (Map.Entry<String, Object> p : o.entrySet()) {
				try {
					String methodName = "set"
							+ StringUtils.capitalize(p.getKey());
					new Statement(s, methodName, new Object[] { p.getValue() })
							.execute();
				} catch (Exception e) {
					throw new IOException("cannot set option: " + p.getKey(), e);
				}
			}
			return new MockOutputStream();
		}

		@Override
		public void putEntry(MockOutputStream out, ObjectId tree, String path, FileMode mode, ObjectLoader loader) {
			String content = mode != FileMode.TREE ? new String(loader.getBytes()) : null;
			entries.put(path, content);
		}

		@Override
		public Iterable<String> suffixes() {
			return SUFFIXES;
		}
	}

	public static class MockOutputStream extends OutputStream {

		private int foo;

		public void setFoo(int foo) {
			this.foo = foo;
		}

		public int getFoo() {
			return foo;
		}

		@Override
		public void write(int b) throws IOException {
			// Do nothing. for testing purposes.
		}
	}
}
