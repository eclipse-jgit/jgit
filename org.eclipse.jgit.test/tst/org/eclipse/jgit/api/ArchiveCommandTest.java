/*******************************************************************************
 * Copyright (c) 2014 Shaul Zorea, <shaulzorea@gmail.com>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Shaul Zorea - initial API and implementation and/or initial documentation
 *******************************************************************************/

package org.eclipse.jgit.api;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
		git.add().addFilepattern(".").call();
		git.commit().setMessage("commit_2").call();
		git.archive().setOutputStream(new MockOutputStream())
				.setFormat(format.SUFFIXES.get(0))
				.setTree(git.getRepository().resolve("HEAD"))
				.setPaths("some_directory/").call();

		assertEquals(UNEXPECTED_ARCHIVE_SIZE, 3, format.size());
		assertEquals(UNEXPECTED_FILE_CONTENTS, "content_1_2", format.getByPath(expectedFilePath1));
		assertEquals(UNEXPECTED_FILE_CONTENTS, "content_2_2", format.getByPath(expectedFilePath2));
        assertNull(UNEXPECTED_TREE_CONTENTS, format.getByPath("some_directory"));

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

	protected class MockOutputStream extends OutputStream {

		@Override
		public void write(int b) throws IOException {
			// Do nothing. for testing purposes.
		}
	}
}
