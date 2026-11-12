/*
 * Copyright (C) 2026, NVIDIA Corporation and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.Test;

public class LargeObjectAlternatesTest extends LocalDiskRepositoryTestCase {
	private static final String FILE = "info/test-alternates";

	private static final String LINK = "test://store";

	private static final byte[] CONTENT = "large blob held only in the store"
			.getBytes(UTF_8);

	private static class TestAlternates extends LargeObjectAlternates {
		TestAlternates(Config config, File objectsDir, Opener opener) {
			super(config, objectsDir, FILE, opener);
		}

		@Override
		boolean accepts(URI link) {
			return "test".equals(link.getScheme()); //$NON-NLS-1$
		}

		@Override
		String linkExample() {
			return "test://<name>"; //$NON-NLS-1$
		}
	}

	private FileRepository repo;

	private FileRepository store;

	private final List<URI> opened = new ArrayList<>();

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		repo = createBareRepository();
		store = createBareRepository();
	}

	@Test
	public void readsItsOwnFileAndLinks() throws Exception {
		RevBlob blob = storeBlob();
		TestAlternates alternates = new TestAlternates(repo.getConfig(),
				repo.getObjectsDirectory(), this::openStore);
		try {
			write("info/other-alternates", LINK);
			assertFalse(alternates.has(blob));

			write(FILE, "other://store", LINK);
			assertTrue(alternates.has(blob));
			assertEquals(CONTENT.length, alternates.getObjectSize(blob));
			assertArrayEquals(CONTENT,
					alternates.open(blob).getCachedBytes());
			assertEquals(Collections.singletonList(URI.create(LINK)), opened);
		} finally {
			alternates.close();
		}
	}

	@Test
	public void missingObjectIsNotFound() throws Exception {
		RevBlob blob;
		try (TestRepository<FileRepository> tr = new TestRepository<>(repo)) {
			blob = tr.blob("only local");
		}
		TestAlternates alternates = new TestAlternates(repo.getConfig(),
				repo.getObjectsDirectory(), this::openStore);
		try {
			write(FILE, LINK);
			assertFalse(alternates.has(blob));
			assertNull(alternates.open(blob));
			assertEquals(-1, alternates.getObjectSize(blob));
		} finally {
			alternates.close();
		}
	}

	@Test
	public void keepsOpenDatabasesAcrossReloads() throws Exception {
		RevBlob blob = storeBlob();
		URI link = URI.create(LINK);
		URI other = URI.create("test://other");
		List<URI> closed = new ArrayList<>();
		TestAlternates alternates = new TestAlternates(repo.getConfig(),
				repo.getObjectsDirectory(), (cfg, dir, l) -> {
					opened.add(l);
					return new ObjectDirectory(cfg,
							store.getObjectsDirectory(), null, FS.DETECTED,
							null) {
						@Override
						public void close() {
							closed.add(l);
							super.close();
						}
					};
				});
		try {
			write(FILE, LINK);
			assertTrue(alternates.has(blob));

			write(FILE, "# same link", LINK);
			assertTrue(alternates.has(blob));
			assertEquals(List.of(link), opened);

			write(FILE, LINK, other.toString());
			assertTrue(alternates.has(blob));
			assertEquals(List.of(link, other), opened);
			assertTrue(closed.isEmpty());

			write(FILE, other.toString());
			assertTrue(alternates.has(blob));
			assertEquals(List.of(link), closed);
		} finally {
			alternates.close();
		}
	}

	private ObjectDirectory openStore(Config cfg, File objectsDir, URI link)
			throws IOException {
		opened.add(link);
		return new ObjectDirectory(cfg, store.getObjectsDirectory(), null,
				FS.DETECTED, null);
	}

	private RevBlob storeBlob() throws Exception {
		try (TestRepository<FileRepository> tr = new TestRepository<>(store)) {
			return tr.blob(CONTENT);
		}
	}

	private void write(String fileName, String... lines) throws IOException {
		File file = new File(repo.getObjectsDirectory(), fileName);
		Files.write(file.toPath(),
				(String.join("\n", lines) + "\n").getBytes(UTF_8));
	}
}
