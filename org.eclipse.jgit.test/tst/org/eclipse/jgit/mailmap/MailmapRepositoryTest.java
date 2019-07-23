/*
 * Copyright (c) 2026 Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.mailmap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Assume;
import org.junit.Test;

public class MailmapRepositoryTest extends RepositoryTestCase {

	@Test
	public void testSymlinkNotFollowed() throws Exception {
		Assume.assumeTrue(db.getFS().supportsSymlinks());
		File f = writeTrashFile(Constants.MAILMAP_FILENAME,
				"Peter Pan <peter@neverland.xx> First <first@example.com>");
		Mailmap map = db.getMailmap();
		PersonIdent p = new PersonIdent("First", "first@example.com");
		PersonIdent mapped = map.map(p);
		assertThat(mapped.getName(), is("Peter Pan"));
		assertThat(mapped.getEmailAddress(), is("peter@neverland.xx"));
		Path movedFile = db.getWorkTree().toPath().resolve("mymailmap.txt");
		Files.move(f.toPath(), movedFile);
		assertFalse(Files.exists(f.toPath()));
		Files.createSymbolicLink(f.toPath(), movedFile);
		map = db.getMailmap();
		p = new PersonIdent("First", "first@example.com");
		assertThat(map.map(p), is(p));
	}
}
