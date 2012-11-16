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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.IOException;

import java.lang.String;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.pgm.CLIGitCommand;
import org.junit.Before;
import org.junit.Test;

public class ArchiveTest extends CLIRepositoryTestCase {
	private Git git;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		git.commit().setMessage("initial commit").call();
	}

	@Test
	public void testEmptyArchive() throws Exception {
		final byte[] result = CLIGitCommand.rawExecute( //
				"git archive 4b825dc642cb", db);
		assertArrayEquals(new String[0], listZipEntries(result));
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
					new InputStreamReader(in));
			String line;
			while ((line = reader.readLine()) != null)
				l.add(line);
			return l.toArray(new String[l.size()]);
		}

		// not found
		return null;
	}
}
