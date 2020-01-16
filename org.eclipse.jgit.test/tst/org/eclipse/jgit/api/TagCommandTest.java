/*
 * Copyright (C) 2010, 2013 Chris Aniszczyk <caniszczyk@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.eclipse.jgit.lib.Constants.R_TAGS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidTagNameException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

public class TagCommandTest extends RepositoryTestCase {

	@Test
	public void testTaggingOnHead() throws GitAPIException, IOException {
		try (Git git = new Git(db);
				RevWalk walk = new RevWalk(db)) {
			RevCommit commit = git.commit().setMessage("initial commit").call();
			Ref tagRef = git.tag().setName("tag").call();
			assertEquals(commit.getId(),
					db.getRefDatabase().peel(tagRef).getPeeledObjectId());
			assertEquals("tag", walk.parseTag(tagRef.getObjectId()).getTagName());
		}
	}

	@Test
	public void testTagging()
			throws GitAPIException, JGitInternalException, IOException {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
			RevCommit commit = git.commit().setMessage("second commit").call();
			git.commit().setMessage("third commit").call();
			Ref tagRef = git.tag().setObjectId(commit).setName("tag").call();
			assertEquals(commit.getId(),
					db.getRefDatabase().peel(tagRef).getPeeledObjectId());
		}
	}

	@Test
	public void testUnannotatedTagging() throws GitAPIException,
			JGitInternalException {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
			RevCommit commit = git.commit().setMessage("second commit").call();
			git.commit().setMessage("third commit").call();
			Ref tagRef = git.tag().setObjectId(commit).setName("tag")
					.setAnnotated(false).call();
			assertEquals(commit.getId(), tagRef.getObjectId());
		}
	}

	@Test
	public void testEmptyTagName() throws GitAPIException {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
			try {
				// forget to tag name
				git.tag().setMessage("some message").call();
				fail("We should have failed without a tag name");
			} catch (InvalidTagNameException e) {
				// should hit here
			}
		}
	}

	@Test
	public void testInvalidTagName() throws GitAPIException {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
			try {
				git.tag().setName("bad~tag~name").setMessage("some message").call();
				fail("We should have failed due to a bad tag name");
			} catch (InvalidTagNameException e) {
				// should hit here
			}
		}
	}

	@Test
	public void testFailureOnSignedTags() throws GitAPIException {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
			try {
				git.tag().setSigned(true).setName("tag").call();
				fail("We should have failed with an UnsupportedOperationException due to signed tag");
			} catch (UnsupportedOperationException e) {
				// should hit here
			}
		}
	}

	private List<Ref> getTags() throws Exception {
		return db.getRefDatabase().getRefsByPrefix(R_TAGS);
	}

	@Test
	public void testDelete() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
			Ref tagRef = git.tag().setName("tag").call();
			assertEquals(1, getTags().size());

			List<String> deleted = git.tagDelete().setTags(tagRef.getName())
					.call();
			assertEquals(1, deleted.size());
			assertEquals(tagRef.getName(), deleted.get(0));
			assertEquals(0, getTags().size());

			Ref tagRef1 = git.tag().setName("tag1").call();
			Ref tagRef2 = git.tag().setName("tag2").call();
			assertEquals(2, getTags().size());
			deleted = git.tagDelete().setTags(tagRef1.getName(), tagRef2.getName())
					.call();
			assertEquals(2, deleted.size());
			assertEquals(0, getTags().size());
		}
	}

	@Test
	public void testDeleteFullName() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();
			Ref tagRef = git.tag().setName("tag").call();
			assertEquals(1, getTags().size());

			List<String> deleted = git.tagDelete()
					.setTags(Repository.shortenRefName(tagRef.getName())).call();
			assertEquals(1, deleted.size());
			assertEquals(tagRef.getName(), deleted.get(0));
			assertEquals(0, getTags().size());
		}
	}

	@Test
	public void testDeleteEmptyTagNames() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();

			List<String> deleted = git.tagDelete().setTags().call();
			assertEquals(0, deleted.size());
		}
	}

	@Test
	public void testDeleteNonExisting() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();

			List<String> deleted = git.tagDelete().setTags("tag").call();
			assertEquals(0, deleted.size());
		}
	}

	@Test
	public void testDeleteBadName() throws Exception {
		try (Git git = new Git(db)) {
			git.commit().setMessage("initial commit").call();

			List<String> deleted = git.tagDelete().setTags("bad~tag~name")
					.call();
			assertEquals(0, deleted.size());
		}
	}

	@Test
	public void testShouldNotBlowUpIfThereAreNoTagsInRepository()
			throws Exception {
		try (Git git = new Git(db)) {
			git.add().addFilepattern("*").call();
			git.commit().setMessage("initial commit").call();
			List<Ref> list = git.tagList().call();
			assertEquals(0, list.size());
		}
	}

	@Test
	public void testShouldNotBlowUpIfThereAreNoCommitsInRepository()
			throws Exception {
		try (Git git = new Git(db)) {
			List<Ref> list = git.tagList().call();
			assertEquals(0, list.size());
		}
	}

	@Test
	public void testListAllTagsInRepositoryInOrder() throws Exception {
		try (Git git = new Git(db)) {
			git.add().addFilepattern("*").call();
			git.commit().setMessage("initial commit").call();

			git.tag().setName("v3").call();
			git.tag().setName("v2").call();
			git.tag().setName("v10").call();

			List<Ref> list = git.tagList().call();

			assertEquals(3, list.size());
			assertEquals("refs/tags/v10", list.get(0).getName());
			assertEquals("refs/tags/v2", list.get(1).getName());
			assertEquals("refs/tags/v3", list.get(2).getName());
		}
	}

}
