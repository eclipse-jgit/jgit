/*
 * Copyright (C) 2012, IBM Corporation and others. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertArrayEquals;

import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.junit.Test;

public class CommitAndLogTest extends CLIRepositoryTestCase {
	@Test
	public void testCommitAmend() throws Exception {
		assertArrayEquals(new String[] { // commit
						"[master 101cffba0364877df1942891eba7f465f628a3d2] first comit", //
						"", // amend
						"[master d2169869dadf16549be20dcf8c207349d2ed6c62] first commit", //
						"", // log
						"commit d2169869dadf16549be20dcf8c207349d2ed6c62", //
						"Author: GIT_COMMITTER_NAME <GIT_COMMITTER_EMAIL>", //
						"Date:   Sat Aug 15 20:12:58 2009 -0330", //
						"", //
						"    first commit", //
						"", //
						"" //
				}, execute("git commit -m 'first comit'", //
						"git commit --amend -m 'first commit'", //
						"git log"));
	}
}
