/*
 * Copyright (C) 2026, Eclipse Foundation, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectFormat;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

public class FileRepositoryObjectFormatTest extends LocalDiskRepositoryTestCase {

	@Test
	public void defaultObjectFormatIsSha1() throws Exception {
		try (Repository repo = createWorkRepository()) {
			assertEquals(ObjectFormat.SHA_1, repo.getObjectFormat());
			StoredConfig config = repo.getConfig();
			assertEquals(0, config.getInt(ConfigConstants.CONFIG_CORE_SECTION,
					null, ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, -1));
			assertNull(config.getString(
					ConfigConstants.CONFIG_EXTENSIONS_SECTION, null,
					ConfigConstants.CONFIG_KEY_OBJECT_FORMAT));
		}
	}

	@Test
	public void createSha256Repository() throws Exception {
		File directory = createTempDirectory("testCreateSha256Repository");
		File gitDir = new File(directory, Constants.DOT_GIT);
		try (Repository repo = new FileRepositoryBuilder().setGitDir(gitDir)
				.setObjectFormat(ObjectFormat.SHA_256).build()) {
			assertEquals(ObjectFormat.SHA_256, repo.getObjectFormat());
			repo.create();
			assertEquals(ObjectFormat.SHA_256, repo.getObjectFormat());

			StoredConfig config = repo.getConfig();
			assertEquals(1, config.getInt(ConfigConstants.CONFIG_CORE_SECTION,
					null, ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, -1));
			assertEquals("sha256", config.getString(
					ConfigConstants.CONFIG_EXTENSIONS_SECTION, null,
					ConfigConstants.CONFIG_KEY_OBJECT_FORMAT));
		}

		// Reopen: the object format must be detected from the config.
		try (FileRepository repo = new FileRepository(gitDir)) {
			assertEquals(ObjectFormat.SHA_256, repo.getObjectFormat());
		}
	}

	@Test
	public void createSha1RepositoryDoesNotWriteExtension() throws Exception {
		File directory = createTempDirectory("testCreateSha1Repository");
		File gitDir = new File(directory, Constants.DOT_GIT);
		try (Repository repo = new FileRepositoryBuilder().setGitDir(gitDir)
				.setObjectFormat(ObjectFormat.SHA_1).build()) {
			repo.create();
			assertEquals(ObjectFormat.SHA_1, repo.getObjectFormat());

			StoredConfig config = repo.getConfig();
			assertEquals(0, config.getInt(ConfigConstants.CONFIG_CORE_SECTION,
					null, ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, -1));
			assertNull(config.getString(
					ConfigConstants.CONFIG_EXTENSIONS_SECTION, null,
					ConfigConstants.CONFIG_KEY_OBJECT_FORMAT));
		}

		try (FileRepository repo = new FileRepository(gitDir)) {
			assertEquals(ObjectFormat.SHA_1, repo.getObjectFormat());
		}
	}

	@Test
	public void explicitSha1ExtensionIsAccepted() throws Exception {
		File gitDir;
		try (Repository repo = createWorkRepository()) {
			gitDir = repo.getDirectory();
			StoredConfig config = repo.getConfig();
			config.setLong(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, 1);
			config.setString(ConfigConstants.CONFIG_EXTENSIONS_SECTION, null,
					ConfigConstants.CONFIG_KEY_OBJECT_FORMAT, "sha1");
			config.save();
		}
		try (FileRepository repo = new FileRepository(gitDir)) {
			assertEquals(ObjectFormat.SHA_1, repo.getObjectFormat());
		}
	}

	@Test
	public void unknownObjectFormatIsRejected() throws Exception {
		try (Repository repo = createWorkRepository()) {
			StoredConfig config = repo.getConfig();
			config.setLong(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, 1);
			config.setString(ConfigConstants.CONFIG_EXTENSIONS_SECTION, null,
					ConfigConstants.CONFIG_KEY_OBJECT_FORMAT, "md5");
			config.save();

			try (FileRepository reopened = new FileRepository(
					repo.getDirectory())) {
				fail("expected IOException for unknown object format");
			} catch (IOException e) {
				assertTrue(e.getMessage().contains("md5"));
			}
		}
	}

	@Test
	public void sha256ConfigIsDetected() throws Exception {
		File gitDir;
		try (Repository repo = createWorkRepository()) {
			gitDir = repo.getDirectory();
			StoredConfig config = repo.getConfig();
			config.setLong(ConfigConstants.CONFIG_CORE_SECTION, null,
					ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, 1);
			config.setString(ConfigConstants.CONFIG_EXTENSIONS_SECTION, null,
					ConfigConstants.CONFIG_KEY_OBJECT_FORMAT, "sha256");
			config.save();
		}

		try (FileRepository repo = new FileRepository(gitDir)) {
			assertEquals(ObjectFormat.SHA_256, repo.getObjectFormat());
		}
	}

	@Test
	public void insertAndReadBackSha256() throws Exception {
		File directory = createTempDirectory("testSha256Insertion");
		File gitDir = new File(directory, Constants.DOT_GIT);
		try (Repository repo = new FileRepositoryBuilder().setGitDir(gitDir)
				.setObjectFormat(ObjectFormat.SHA_256).build()) {
			repo.create();
			byte[] content = "hello world".getBytes(UTF_8);
			ObjectId blobId;
			try (ObjectInserter ins = repo.newObjectInserter()) {
				blobId = ins.insert(Constants.OBJ_BLOB, content);
			}
			assertEquals(32, blobId.getLength());
			assertEquals(64, blobId.name().length());

			// Verify against an independent SHA-256 computation.
			MessageDigest md = ObjectFormat.SHA_256.newMessageDigest();
			md.update(Constants.encodedTypeString(Constants.OBJ_BLOB));
			md.update((byte) ' ');
			md.update(Constants.encodeASCII(content.length));
			md.update((byte) 0);
			md.update(content);
			assertEquals(ObjectId.fromRaw(md.digest(), 0, 32), blobId);

			try (ObjectReader r = repo.newObjectReader()) {
				ObjectLoader l = r.open(blobId);
				assertEquals("hello world",
						new String(l.getBytes(), UTF_8));
			}
		}
	}

	@Test
	public void commitRoundTripSha256() throws Exception {
		File directory = createTempDirectory("testSha256Commit");
		File gitDir = new File(directory, Constants.DOT_GIT);
		ObjectId commitId;
		try (FileRepository repo = (FileRepository) new FileRepositoryBuilder()
				.setGitDir(gitDir).setObjectFormat(ObjectFormat.SHA_256)
				.build()) {
			repo.create();

			DirCache dc = repo.lockDirCache();
			DirCacheBuilder b = dc.builder();
			DirCacheEntry entry = new DirCacheEntry("hello.txt", 0,
					repo.getObjectFormat().getLength());
			entry.setFileMode(FileMode.REGULAR_FILE);
			entry.setLength(11);
			entry.setLastModified(Instant.ofEpochSecond(42L, 0));
			try (ObjectInserter ins = repo.newObjectInserter()) {
				entry.setObjectId(ins.insert(Constants.OBJ_BLOB,
						"hello world".getBytes(UTF_8)));
				b.add(entry);
				b.commit();

				ObjectId treeId = dc.writeTree(ins);
				CommitBuilder cb = new CommitBuilder();
				cb.setTreeId(treeId);
				cb.setAuthor(new PersonIdent("Test", "t@example.com"));
				cb.setCommitter(cb.getAuthor());
				cb.setMessage("first commit\n");
				commitId = ins.insert(cb);

				RefUpdate ru = repo
						.updateRef(Constants.R_HEADS + "master");
				ru.setNewObjectId(commitId);
				assertEquals(RefUpdate.Result.NEW, ru.forceUpdate());
			}
		}
		assertEquals(64, commitId.name().length());

		// Reopen: refs, commit, tree and blob must all be readable.
		try (FileRepository repo = new FileRepository(gitDir)) {
			assertEquals(ObjectFormat.SHA_256, repo.getObjectFormat());
			assertEquals(commitId, repo.resolve(Constants.R_HEADS + "master"));

			try (RevWalk rw = new RevWalk(repo)) {
				RevCommit c = rw.parseCommit(commitId);
				assertEquals("first commit", c.getShortMessage());
				assertEquals(0, c.getParentCount());

				try (TreeWalk tw = new TreeWalk(repo)) {
					tw.addTree(c.getTree());
					tw.addTree(new DirCacheIterator(repo.readDirCache()));
					assertTrue(tw.next());
					assertEquals("hello.txt", tw.getPathString());
					assertTrue(tw.idEqual(0, 1));
					ObjectId blobId = tw.getObjectId(0);
					assertEquals(32, blobId.getLength());
					try (ObjectReader r = repo.newObjectReader()) {
						assertEquals("hello world", new String(
								r.open(blobId).getBytes(), UTF_8));
					}
				}
			}
		}
	}
}
