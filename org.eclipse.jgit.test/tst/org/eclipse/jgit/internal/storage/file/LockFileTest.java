/*
 * Copyright (C) 2012, 2021 GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.LockFailedException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

/**
 * Unit tests of {@link LockFile}
 */
public class LockFileTest extends RepositoryTestCase {

	@Test
	public void lockFailedExceptionRecovery() throws Exception {
		try (Git git = new Git(db)) {
			writeTrashFile("file.txt", "content");
			git.add().addFilepattern("file.txt").call();
			RevCommit commit1 = git.commit().setMessage("create file").call();

			assertNotNull(commit1);
			writeTrashFile("file.txt", "content2");
			git.add().addFilepattern("file.txt").call();
			assertNotNull(git.commit().setMessage("edit file").call());

			LockFile lf = new LockFile(db.getIndexFile());
			assertTrue(lf.lock());
			try {
				git.checkout().setName(commit1.name()).call();
				fail("JGitInternalException not thrown");
			} catch (JGitInternalException e) {
				assertTrue(e.getCause() instanceof LockFailedException);
				lf.unlock();
				git.checkout().setName(commit1.name()).call();
			}
		}
	}

	@Test
	public void testLockTwice() throws Exception {
		File f = writeTrashFile("somefile", "content");
		LockFile lock = new LockFile(f);
		assertTrue(lock.lock());
		lock.write("other".getBytes(StandardCharsets.US_ASCII));
		lock.commit();
		assertFalse(lock.isLocked());
		checkFile(f, "other");
		assertTrue(lock.lock());
		assertTrue(lock.isLocked());
		try (OutputStream out = lock.getOutputStream()) {
			out.write("second".getBytes(StandardCharsets.US_ASCII));
		}
		lock.commit();
		assertFalse(lock.isLocked());
		checkFile(f, "second");
	}

	@Test
	public void testLockTwiceUnlock() throws Exception {
		File f = writeTrashFile("somefile", "content");
		LockFile lock = new LockFile(f);
		assertTrue(lock.lock());
		assertTrue(lock.isLocked());
		lock.write("other".getBytes(StandardCharsets.US_ASCII));
		lock.unlock();
		assertFalse(lock.isLocked());
		checkFile(f, "content");
		assertTrue(lock.lock());
		assertTrue(lock.isLocked());
		try (OutputStream out = lock.getOutputStream()) {
			out.write("second".getBytes(StandardCharsets.US_ASCII));
		}
		lock.commit();
		assertFalse(lock.isLocked());
		checkFile(f, "second");
	}

	@Test
	public void testLockWriteTwiceThrows1() throws Exception {
		File f = writeTrashFile("somefile", "content");
		LockFile lock = new LockFile(f);
		assertTrue(lock.lock());
		assertTrue(lock.isLocked());
		lock.write("other".getBytes(StandardCharsets.US_ASCII));
		assertThrows(Exception.class,
				() -> lock.write("second".getBytes(StandardCharsets.US_ASCII)));
		lock.unlock();
	}

	@Test
	public void testLockWriteTwiceThrows2() throws Exception {
		File f = writeTrashFile("somefile", "content");
		LockFile lock = new LockFile(f);
		assertTrue(lock.lock());
		assertTrue(lock.isLocked());
		try (OutputStream out = lock.getOutputStream()) {
			out.write("other".getBytes(StandardCharsets.US_ASCII));
		}
		assertThrows(Exception.class,
				() -> lock.write("second".getBytes(StandardCharsets.US_ASCII)));
		lock.unlock();
	}

	@Test
	public void testLockWriteTwiceThrows3() throws Exception {
		File f = writeTrashFile("somefile", "content");
		LockFile lock = new LockFile(f);
		assertTrue(lock.lock());
		assertTrue(lock.isLocked());
		lock.write("other".getBytes(StandardCharsets.US_ASCII));
		assertThrows(Exception.class, () -> {
			try (OutputStream out = lock.getOutputStream()) {
				out.write("second".getBytes(StandardCharsets.US_ASCII));
			}
		});
		lock.unlock();
	}

	@Test
	public void testLockWriteTwiceThrows4() throws Exception {
		File f = writeTrashFile("somefile", "content");
		LockFile lock = new LockFile(f);
		assertTrue(lock.lock());
		assertTrue(lock.isLocked());
		try (OutputStream out = lock.getOutputStream()) {
			out.write("other".getBytes(StandardCharsets.US_ASCII));
		}
		assertThrows(Exception.class, () -> {
			try (OutputStream out = lock.getOutputStream()) {
				out.write("second".getBytes(StandardCharsets.US_ASCII));
			}
		});
		lock.unlock();
	}

	@Test
	public void testLockUnclosedCommitThrows() throws Exception {
		File f = writeTrashFile("somefile", "content");
		LockFile lock = new LockFile(f);
		assertTrue(lock.lock());
		assertTrue(lock.isLocked());
		try (OutputStream out = lock.getOutputStream()) {
			out.write("other".getBytes(StandardCharsets.US_ASCII));
			assertThrows(Exception.class, () -> lock.commit());
		}
	}

	@Test
	public void testLockNested() throws Exception {
		File f = writeTrashFile("somefile", "content");
		LockFile lock = new LockFile(f);
		assertTrue(lock.lock());
		assertTrue(lock.isLocked());
		assertThrows(IllegalStateException.class, () -> lock.lock());
		assertTrue(lock.isLocked());
		lock.unlock();
	}

	@Test
	public void testLockHeld() throws Exception {
		File f = writeTrashFile("somefile", "content");
		LockFile lock = new LockFile(f);
		assertTrue(lock.lock());
		assertTrue(lock.isLocked());
		LockFile lock2 = new LockFile(f);
		assertFalse(lock2.lock());
		assertFalse(lock2.isLocked());
		assertTrue(lock.isLocked());
		lock.unlock();
	}

	@Test
	public void testLockForAppend() throws Exception {
		File f = writeTrashFile("somefile", "content");
		LockFile lock = new LockFile(f);
		assertTrue(lock.lockForAppend());
		assertTrue(lock.isLocked());
		lock.write("other".getBytes(StandardCharsets.US_ASCII));
		lock.commit();
		assertFalse(lock.isLocked());
		checkFile(f, "contentother");
	}

	@Test
	public void testUnlockNoop() throws Exception {
		File f = writeTrashFile("somefile", "content");
		try {
			LockFile lock = new LockFile(f);
			lock.unlock();
			lock.unlock();
		} catch (Throwable e) {
			fail("unlock should be noop if not locked at all.");
		}
	}
}
