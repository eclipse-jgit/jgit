/*
 * Copyright (C) 2017, Ned Twigg <ned.twigg@diffplug.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.eclipse.jgit.junit.JGitTestUtil.check;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.pgm.opt.CmdLineParser;
import org.eclipse.jgit.pgm.opt.SubcommandHandler;
import org.junit.Test;
import org.kohsuke.args4j.Argument;

public class TextBuiltinTest extends CLIRepositoryTestCase {
	public static class GitCliJGitWrapperParser {
		@Argument(index = 0, metaVar = "metaVar_command", required = true, handler = SubcommandHandler.class)
		TextBuiltin subcommand;

		@Argument(index = 1, metaVar = "metaVar_arg")
		List<String> arguments = new ArrayList<>();
	}

	private String[] runAndCaptureUsingInitRaw(String... args)
			throws Exception {
		CLIGitCommand.Result result = new CLIGitCommand.Result();

		GitCliJGitWrapperParser bean = new GitCliJGitWrapperParser();
		final CmdLineParser clp = new CmdLineParser(bean);
		clp.parseArgument(args);

		final TextBuiltin cmd = bean.subcommand;
		cmd.initRaw(db, null, null, result.out, result.err);
		cmd.execute(bean.arguments.toArray(new String[bean.arguments.size()]));
		if (cmd.getOutputWriter() != null) {
			cmd.getOutputWriter().flush();
		}
		if (cmd.getErrorWriter() != null) {
			cmd.getErrorWriter().flush();
		}
		return result.outLines().toArray(new String[0]);
	}

	@Test
	public void testCleanDeleteDirs() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();

			writeTrashFile("dir/file", "someData");
			writeTrashFile("a", "someData");
			writeTrashFile("b", "someData");

			// all these files should be there
			assertTrue(check(db, "a"));
			assertTrue(check(db, "b"));
			assertTrue(check(db, "dir/file"));

			assertArrayOfLinesEquals(new String[] { "Removing a", "Removing b",
					"Removing dir/" },
					runAndCaptureUsingInitRaw("clean", "-d", "-f"));
			assertFalse(check(db, "a"));
			assertFalse(check(db, "b"));
			assertFalse(check(db, "dir/file"));
		}
	}
}
