/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com>
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
package org.eclipse.jgit.util;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.attributes.FilterCommand;
import org.eclipse.jgit.attributes.FilterCommandFactory;
import org.eclipse.jgit.attributes.FilterCommandRegistry;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class FilterCommandsTest extends RepositoryTestCase {
	private Git git;

	RevCommit initialCommit;

	RevCommit secondCommit;

	class TestCommandFactory implements FilterCommandFactory {
		private int prefix;

		public TestCommandFactory(int prefix) {
			this.prefix = prefix;
		}

		@Override
		public FilterCommand create(Repository repo, InputStream in,
				final OutputStream out) {
			FilterCommand cmd = new FilterCommand(in, out) {

				@Override
				public int run() throws IOException {
					int b = in.read();
					if (b == -1) {
						in.close();
						out.close();
						return b;
					}
					out.write(prefix);
					out.write(b);
					return 1;
				}
			};
			return cmd;
		}
	}

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);
		// commit something
		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		initialCommit = git.commit().setMessage("Initial commit").call();

		// create a master branch and switch to it
		git.branchCreate().setName("test").call();
		RefUpdate rup = db.updateRef(Constants.HEAD);
		rup.link("refs/heads/test");

		// commit something on the test branch
		writeTrashFile("Test.txt", "Some change");
		git.add().addFilepattern("Test.txt").call();
		secondCommit = git.commit().setMessage("Second commit").call();
	}

	@Test
	public void testBuiltinCleanFilter()
			throws IOException, GitAPIException {
		String builtinCommandName = "jgit://builtin/test/clean";
		FilterCommandRegistry.register(builtinCommandName,
				new TestCommandFactory('c'));
		StoredConfig config = git.getRepository().getConfig();
		config.setString("filter", "test", "clean", builtinCommandName);
		config.save();

		writeTrashFile(".gitattributes", "*.txt filter=test");
		git.add().addFilepattern(".gitattributes").call();
		git.commit().setMessage("add filter").call();

		writeTrashFile("Test.txt", "Hello again");
		git.add().addFilepattern("Test.txt").call();
		assertEquals(
				"[.gitattributes, mode:100644, content:*.txt filter=test][Test.txt, mode:100644, content:cHceclclcoc cacgcacicn]",
				indexState(CONTENT));

		writeTrashFile("Test.bin", "Hello again");
		git.add().addFilepattern("Test.bin").call();
		assertEquals(
				"[.gitattributes, mode:100644, content:*.txt filter=test][Test.bin, mode:100644, content:Hello again][Test.txt, mode:100644, content:cHceclclcoc cacgcacicn]",
				indexState(CONTENT));

		config.setString("filter", "test", "clean", null);
		config.save();

		git.add().addFilepattern("Test.txt").call();
		assertEquals(
				"[.gitattributes, mode:100644, content:*.txt filter=test][Test.bin, mode:100644, content:Hello again][Test.txt, mode:100644, content:Hello again]",
				indexState(CONTENT));

		config.setString("filter", "test", "clean", null);
		config.save();
	}

	@Test
	public void testBuiltinSmudgeFilter() throws IOException, GitAPIException {
		String builtinCommandName = "jgit://builtin/test/smudge";
		FilterCommandRegistry.register(builtinCommandName,
				new TestCommandFactory('s'));
		StoredConfig config = git.getRepository().getConfig();
		config.setString("filter", "test", "smudge", builtinCommandName);
		config.save();

		writeTrashFile(".gitattributes", "*.txt filter=test");
		git.add().addFilepattern(".gitattributes").call();
		git.commit().setMessage("add filter").call();

		writeTrashFile("Test.txt", "Hello again");
		git.add().addFilepattern("Test.txt").call();
		assertEquals(
				"[.gitattributes, mode:100644, content:*.txt filter=test][Test.txt, mode:100644, content:Hello again]",
				indexState(CONTENT));
		assertEquals("Hello again", read("Test.txt"));
		deleteTrashFile("Test.txt");
		git.checkout().addPath("Test.txt").call();
		assertEquals("sHseslslsos sasgsasisn", read("Test.txt"));

		writeTrashFile("Test.bin", "Hello again");
		git.add().addFilepattern("Test.bin").call();
		assertEquals(
				"[.gitattributes, mode:100644, content:*.txt filter=test][Test.bin, mode:100644, content:Hello again][Test.txt, mode:100644, content:Hello again]",
				indexState(CONTENT));
		deleteTrashFile("Test.bin");
		git.checkout().addPath("Test.bin").call();
		assertEquals("Hello again", read("Test.bin"));

		config.setString("filter", "test", "clean", null);
		config.save();

		git.add().addFilepattern("Test.txt").call();
		assertEquals(
				"[.gitattributes, mode:100644, content:*.txt filter=test][Test.bin, mode:100644, content:Hello again][Test.txt, mode:100644, content:sHseslslsos sasgsasisn]",
				indexState(CONTENT));

		config.setString("filter", "test", "clean", null);
		config.save();
	}

	@Test
	public void testBuiltinCleanAndSmudgeFilter() throws IOException, GitAPIException {
		String builtinCommandPrefix = "jgit://builtin/test/";
		FilterCommandRegistry.register(builtinCommandPrefix + "smudge",
				new TestCommandFactory('s'));
		FilterCommandRegistry.register(builtinCommandPrefix + "clean",
				new TestCommandFactory('c'));
		StoredConfig config = git.getRepository().getConfig();
		config.setString("filter", "test", "smudge", builtinCommandPrefix+"smudge");
		config.setString("filter", "test", "clean",
				builtinCommandPrefix + "clean");
		config.save();

		writeTrashFile(".gitattributes", "*.txt filter=test");
		git.add().addFilepattern(".gitattributes").call();
		git.commit().setMessage("add filter").call();

		writeTrashFile("Test.txt", "Hello again");
		git.add().addFilepattern("Test.txt").call();
		assertEquals(
				"[.gitattributes, mode:100644, content:*.txt filter=test][Test.txt, mode:100644, content:cHceclclcoc cacgcacicn]",
				indexState(CONTENT));
		assertEquals("Hello again", read("Test.txt"));
		deleteTrashFile("Test.txt");
		git.checkout().addPath("Test.txt").call();
		assertEquals("scsHscsescslscslscsoscs scsascsgscsascsiscsn",
				read("Test.txt"));

		writeTrashFile("Test.bin", "Hello again");
		git.add().addFilepattern("Test.bin").call();
		assertEquals(
				"[.gitattributes, mode:100644, content:*.txt filter=test][Test.bin, mode:100644, content:Hello again][Test.txt, mode:100644, content:cHceclclcoc cacgcacicn]",
				indexState(CONTENT));
		deleteTrashFile("Test.bin");
		git.checkout().addPath("Test.bin").call();
		assertEquals("Hello again", read("Test.bin"));

		config.setString("filter", "test", "clean", null);
		config.save();

		git.add().addFilepattern("Test.txt").call();
		assertEquals(
				"[.gitattributes, mode:100644, content:*.txt filter=test][Test.bin, mode:100644, content:Hello again][Test.txt, mode:100644, content:scsHscsescslscslscsoscs scsascsgscsascsiscsn]",
				indexState(CONTENT));

		config.setString("filter", "test", "clean", null);
		config.save();
	}

}
