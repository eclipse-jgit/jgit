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

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public class ArchiveCommandTest extends RepositoryTestCase {

	private static final String UNEXPECTED_ARCHIVE_SIZE  = "Unexpected archive size";
	private static final String UNEXPECTED_FILE_CONTENTS = "Unexpected file contents";
	private static final String UNEXPECTED_TREE_CONTENTS = "Unexpected tree contents";

	private MockFormat format = null;

	@Before
	public void setup() {
		format = new MockFormat();
		ArchiveCommand.registerFormat(format.SUFFIXES.get(0), format);
	}

	@After
	public void tearDown() {
		ArchiveCommand.unregisterFormat(format.SUFFIXES.get(0));
	}

	@Test
	public void archiveHeadAllFiles() throws IOException, GitAPIException {
		Git git = new Git(db);
		writeTrashFile("file_1.txt", "content_1_1");
		git.add().addFilepattern("file_1.txt").call();
		git.commit().setMessage("create file").call();

		writeTrashFile("file_1.txt", "content_1_2");
		writeTrashFile("file_2.txt", "content_2_2");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("updated file").call();

		git.archive().setOutputStream(new MockOutputStream())
				.setFormat(format.SUFFIXES.get(0))
				.setTree(git.getRepository().resolve("HEAD")).call();

		assertEquals(UNEXPECTED_ARCHIVE_SIZE, 2, format.size());
		assertEquals(UNEXPECTED_FILE_CONTENTS, "content_1_2", format.getByPath("file_1.txt"));
		assertEquals(UNEXPECTED_FILE_CONTENTS, "content_2_2", format.getByPath("file_2.txt"));
	}

	@Test
	public void archiveHeadSpecificPath() throws IOException, GitAPIException {
		Git git = new Git(db);
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

	@Test
	public void archiveByIdSpecificFile() throws IOException, GitAPIException {
		Git git = new Git(db);
		writeTrashFile("file_1.txt", "content_1_1");
		git.add().addFilepattern("file_1.txt").call();
		RevCommit first = git.commit().setMessage("create file").call();

		writeTrashFile("file_1.txt", "content_1_2");
		String expectedFilePath = "some_directory/file_2.txt";
		writeTrashFile(expectedFilePath, "content_2_2");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("updated file").call();

		git.archive().setOutputStream(new MockOutputStream())
				.setFormat(format.SUFFIXES.get(0)).setTree(first)
				.setPaths("file_1.txt").call();

		assertEquals(UNEXPECTED_ARCHIVE_SIZE, 1, format.size());
		assertEquals(UNEXPECTED_FILE_CONTENTS, "content_1_1", format.getByPath("file_1.txt"));
	}

	@Test
	public void archiveByDirectoryPath() throws GitAPIException, IOException {
		Git git = new Git(db);
		writeTrashFile("file_0.txt", "content_0_1");
		git.add().addFilepattern("file_0.txt").call();
		git.commit().setMessage("commit_1").call();

		writeTrashFile("file_0.txt", "content_0_2");
		String expectedFilePath1 = "some_directory/file_1.txt";
		writeTrashFile(expectedFilePath1, "content_1_2");
		String expectedFilePath2 = "some_directory/file_2.txt";
		writeTrashFile(expectedFilePath2, "content_2_2");
        String expectedFilePath3= "some_directory/nested_directory/file_3.txt";
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

	private class MockFormat implements ArchiveCommand.Format<MockOutputStream> {

		private Map<String, String> entries = new HashMap<String, String>();

		private int size() {
			return entries.size();
		}

		private String getByPath(String path) {
			return entries.get(path);
		}

		private final List<String> SUFFIXES = Collections
				.unmodifiableList(Arrays.asList(".mck"));

		public MockOutputStream createArchiveOutputStream(OutputStream s)
				throws IOException {
			return new MockOutputStream();
		}

		public void putEntry(MockOutputStream out, String path, FileMode mode, ObjectLoader loader) {
			String content = mode != FileMode.TREE ? new String(loader.getBytes()) : null;
			entries.put(path, content);
		}

		public Iterable<String> suffixes() {
			return SUFFIXES;
		}
	}

	private class MockOutputStream extends OutputStream {

		@Override
		public void write(int b) throws IOException {
			// Do nothing. for testing purposes.
		}
	}
}
