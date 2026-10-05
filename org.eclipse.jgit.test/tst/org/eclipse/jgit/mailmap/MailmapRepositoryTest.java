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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
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

	@Test
	public void testLoadFromBlob() throws Exception {
		try (Git git = new Git(db)) {
			File f = writeTrashFile("somename",
					"Peter Pan <peter@neverland.xx> First <first@example.com>");
			git.add().addFilepattern("somename").call();
			git.commit().setMessage("initial").call();
			FileBasedConfig config = db.getConfig();
			config.setString(ConfigConstants.CONFIG_MAILMAP_SECTION, null,
					ConfigConstants.CONFIG_KEY_BLOB, "HEAD:somename");
			config.save();
			Files.delete(f.toPath());
			Mailmap map = db.getMailmap();
			PersonIdent p = new PersonIdent("First", "first@example.com");
			PersonIdent mapped = map.map(p);
			assertThat(mapped.getName(), is("Peter Pan"));
			assertThat(mapped.getEmailAddress(), is("peter@neverland.xx"));
			// Check with a tag pointing to the blob directly
			ObjectId oid = db.resolve("HEAD:somename");
			try (RevWalk walk = new RevWalk(db)) {
				RevObject obj = walk.parseAny(oid);
				git.tag().setObjectId(obj).setName("mailmap_tag").call();
			}
			config.setString(ConfigConstants.CONFIG_MAILMAP_SECTION, null,
					ConfigConstants.CONFIG_KEY_BLOB, "mailmap_tag");
			config.save();
			map = db.getMailmap();
			p = new PersonIdent("First", "first@example.com");
			mapped = map.map(p);
			assertThat(mapped.getName(), is("Peter Pan"));
			assertThat(mapped.getEmailAddress(), is("peter@neverland.xx"));
		}
	}
}
