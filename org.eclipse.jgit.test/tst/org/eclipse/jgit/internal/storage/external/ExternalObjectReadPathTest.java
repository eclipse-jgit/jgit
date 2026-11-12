/*
 * Copyright (C) 2026, Digital.ai and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.external;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.junit.Test;

/**
 * Reading objects held by an external object database through the
 * repository's regular object directory.
 */
public class ExternalObjectReadPathTest extends LocalDiskRepositoryTestCase {

	private static final String FILE_NAME = "large.bin";

	private static final byte[] LARGE = largeContent(4096);

	private static final ObjectId UNKNOWN = ObjectId
			.fromString("0123456789abcdef0123456789abcdef01234567");

	private File storeDir;

	private FileRepository repo;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		FileRepository initial = createWorkRepository();
		storeDir = createTempDirectory("large-store");
		FileBasedConfig cfg = initial.getConfig();
		cfg.setString("objectStore", "large", "path",
				storeDir.getAbsolutePath());
		cfg.setLong("objectStore", "large", "minBlobSize", 1024);
		cfg.save();
		repo = reopen(initial);
	}

	@Test
	public void noExternalDatabasesWithoutConfiguration() throws IOException {
		assertTrue(createWorkRepository().getObjectDatabase()
				.getExternalObjectDatabases().isEmpty());
	}

	@Test
	public void configuredStoreIsOpened() {
		assertEquals(1,
				repo.getObjectDatabase().getExternalObjectDatabases().size());
		assertEquals("large", external(repo).getName());
	}

	@Test
	public void repositoryReadsObjectFromExternalStore() throws IOException {
		ObjectId id = storeExternally(repo, LARGE);

		assertFalse(repo.getObjectDatabase().fileFor(id).exists());
		assertTrue(repo.getObjectDatabase().getPacks().isEmpty());

		assertTrue(repo.getObjectDatabase().has(id));
		try (ObjectReader reader = repo.newObjectReader()) {
			assertTrue(reader.has(id));
			assertEquals(LARGE.length, reader.getObjectSize(id, OBJ_BLOB));
			try (ObjectStream in = reader.open(id, OBJ_BLOB).openStream()) {
				assertArrayEquals(LARGE, in.readAllBytes());
			}
		}
	}

	@Test
	public void abbreviatedIdDoesNotResolveToExternalObject()
			throws IOException {
		ObjectId id = storeExternally(repo, LARGE);

		assertNull(repo.resolve(id.name().substring(0, 10)));
		assertEquals(id, repo.resolve(id.name()));
	}

	@Test
	public void unknownObjectIsStillMissing() throws IOException {
		assertFalse(repo.getObjectDatabase().has(UNKNOWN));
		try (ObjectReader reader = repo.newObjectReader()) {
			assertThrows(MissingObjectException.class,
					() -> reader.open(UNKNOWN));
			assertThrows(MissingObjectException.class,
					() -> reader.getObjectSize(UNKNOWN, OBJ_BLOB));
		}
	}

	@Test
	public void storeFailureIsNotReportedAsMissing() throws IOException {
		// An unreadable store directory makes every lookup in the store fail
		// with an access error rather than "no such file".
		makeUnreadable(storeDir);
		try {
			assertThrows(IOException.class,
					() -> repo.getObjectDatabase().has(UNKNOWN));
			try (ObjectReader reader = repo.newObjectReader()) {
				IOException e = assertThrows(IOException.class,
						() -> reader.open(UNKNOWN));
				assertFalse(e instanceof MissingObjectException);
				e = assertThrows(IOException.class,
						() -> reader.getObjectSize(UNKNOWN, OBJ_BLOB));
				assertFalse(e instanceof MissingObjectException);
			}
			ObjectDatabase cached = repo.getObjectDatabase()
					.newCachedDatabase();
			assertThrows(IOException.class, () -> cached.has(UNKNOWN));
		} finally {
			makeReadable(storeDir);
		}
	}

	@Test
	public void localObjectsAreFoundWithoutAskingTheStore() throws Exception {
		ObjectId commit = commitFile(storeExternally(repo, LARGE));
		makeUnreadable(storeDir);
		try {
			assertTrue(repo.getObjectDatabase().has(commit));
		} finally {
			makeReadable(storeDir);
		}
	}

	@Test
	public void walkAndCheckoutUseExternalObject() throws Exception {
		ObjectId blob = storeExternally(repo, LARGE);
		ObjectId commit = commitFile(blob);

		try (RevWalk rw = new RevWalk(repo)) {
			RevCommit c = rw.parseCommit(commit);
			try (TreeWalk tw = TreeWalk.forPath(repo, FILE_NAME,
					c.getTree())) {
				assertEquals(blob, tw.getObjectId(0));
			}
		}

		try (Git git = Git.wrap(repo)) {
			git.reset().setMode(ResetType.HARD).setRef(commit.name()).call();
		}
		File checkedOut = new File(repo.getWorkTree(), FILE_NAME);
		assertArrayEquals(LARGE, Files.readAllBytes(checkedOut.toPath()));
	}

	@Test
	public void cachedDatabaseSeesExternalObjects() throws IOException {
		ObjectId id = storeExternally(repo, LARGE);

		ObjectDatabase cached = repo.getObjectDatabase().newCachedDatabase();
		assertTrue(cached.has(id));
		try (ObjectReader reader = cached.newReader();
				ObjectStream in = reader.open(id, OBJ_BLOB).openStream()) {
			assertArrayEquals(LARGE, in.readAllBytes());
		}
	}

	@Test
	public void objectsStoredByAnotherInstanceAreVisibleAtOnce()
			throws IOException {
		FileRepository other = reopen(repo);
		ObjectId id = new ObjectInserter.Formatter().idFor(OBJ_BLOB, LARGE);
		assertFalse(repo.getObjectDatabase().has(id));

		storeExternally(other, LARGE);

		assertTrue(repo.getObjectDatabase().has(id));
	}

	@Test
	public void misconfiguredStorePreventsOpeningRepository()
			throws IOException {
		FileBasedConfig cfg = repo.getConfig();
		cfg.setString("objectStore", "broken", "type", "nosuchtype");
		cfg.setString("objectStore", "broken", "path", "x");
		cfg.save();

		assertThrows(IOException.class,
				() -> new FileRepository(repo.getDirectory()));
	}

	private FileRepository reopen(FileRepository r) throws IOException {
		FileRepository reopened = new FileRepository(r.getDirectory());
		addRepoToClose(reopened);
		return reopened;
	}

	private static ExternalObjectDatabase external(FileRepository r) {
		return r.getObjectDatabase().getExternalObjectDatabases().get(0);
	}

	private static ObjectId storeExternally(FileRepository r, byte[] content)
			throws IOException {
		ObjectId id = new ObjectInserter.Formatter().idFor(OBJ_BLOB, content);
		try (ExternalObjectInserter ins = external(r).newInserter()) {
			ins.insert(id, OBJ_BLOB, content.length,
					new ByteArrayInputStream(content));
		}
		return id;
	}

	private ObjectId commitFile(ObjectId blob) throws IOException {
		PersonIdent ident = new PersonIdent("A U Thor", "author@example.com");
		ObjectId commit;
		try (ObjectInserter ins = repo.newObjectInserter()) {
			TreeFormatter tree = new TreeFormatter();
			tree.append(FILE_NAME, FileMode.REGULAR_FILE, blob);
			CommitBuilder cb = new CommitBuilder();
			cb.setTreeId(ins.insert(tree));
			cb.setAuthor(ident);
			cb.setCommitter(ident);
			cb.setMessage("Add large file\n");
			commit = ins.insert(cb);
			ins.flush();
		}
		RefUpdate ru = repo.updateRef("refs/heads/master");
		ru.setNewObjectId(commit);
		ru.forceUpdate();
		return commit;
	}

	private static byte[] largeContent(int size) {
		byte[] content = new byte[size];
		for (int i = 0; i < size; i++) {
			content[i] = (byte) ('a' + i % 26);
		}
		return content;
	}

	private static void makeUnreadable(File dir) throws IOException {
		Files.setPosixFilePermissions(dir.toPath(),
				PosixFilePermissions.fromString("---------"));
		// Skip where permissions are not enforced, for example as root.
		assumeFalse(dir.canRead() || dir.canExecute());
	}

	private static void makeReadable(File dir) throws IOException {
		Files.setPosixFilePermissions(dir.toPath(),
				PosixFilePermissions.fromString("rwx------"));
	}
}
