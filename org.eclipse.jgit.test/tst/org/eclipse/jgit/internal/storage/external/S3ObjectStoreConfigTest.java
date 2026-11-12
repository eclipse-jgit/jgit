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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Configuring an S3 object store through {@code [objectStore]} sections.
 */
public class S3ObjectStoreConfigTest {

	private static final String SECRET = "very-secret-key";

	private static final String CREDENTIALS = "[objectStore \"s3\"]\n"
			+ "  accessKey = test-access-key\n"
			+ "  secretKey = " + SECRET + "\n";

	private static final byte[] CONTENT = "large content".getBytes(UTF_8);

	private static final ObjectId ID = new ObjectInserter.Formatter()
			.idFor(OBJ_BLOB, CONTENT);

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private FakeS3Server server;

	private File objects;

	@Before
	public void setUp() throws IOException {
		server = new FakeS3Server();
		objects = tmp.newFolder("objects");
	}

	@After
	public void tearDown() {
		server.close();
	}

	@Test
	public void s3TypeCreatesWorkingStore() throws Exception {
		Config cfg = config(storeSection() + "  minBlobSize = 1k\n"
				+ "  accessKey = test-access-key\n"
				+ "  secretKey = " + SECRET + "\n");

		try (ExternalObjectDatabase db = only(cfg)) {
			assertEquals("s3", db.getName());
			assertEquals(1024, db.getMinBlobSize());
			assertTrue(db.getStore() instanceof S3ObjectStore);
			assertEquals("repos/foo.git/" + ID.name(),
					((S3ObjectStore) db.getStore()).keyFor(ID));

			roundTrip(db);
		}
		String auth = server.lastRequestHeaders().getFirst("Authorization");
		assertTrue(auth, auth.contains("Credential=test-access-key/"));
		assertTrue(auth, auth.contains("/eu-central-1/s3/"));
		assertTrue(server.object("jgit-test", "repos/foo.git/" + ID.name())
				.length > 0);
	}

	@Test
	public void credentialsAreInheritedFromBaseConfig() throws Exception {
		Config system = config(CREDENTIALS);
		Config repo = new Config(system);
		repo.fromText(storeSection());

		try (ExternalObjectDatabase db = only(repo)) {
			roundTrip(db);
		}
	}

	@Test
	public void prefixAndEndpointAreOptional() throws Exception {
		Config cfg = config("[objectStore \"s3\"]\n" + "  type = s3\n"
				+ "  bucket = jgit-test\n" + "  region = us-east-1\n"
				+ CREDENTIALS.substring(CREDENTIALS.indexOf('\n') + 1));

		try (ExternalObjectDatabase db = only(cfg)) {
			S3ObjectStore store = (S3ObjectStore) db.getStore();
			assertEquals(ID.name(), store.keyFor(ID));
			assertEquals("https://jgit-test.s3.us-east-1.amazonaws.com/k",
					store.urlFor("k").toExternalForm());
			assertEquals(ExternalObjectDatabase.DEFAULT_MIN_BLOB_SIZE,
					db.getMinBlobSize());
		}
	}

	@Test
	public void missingRequiredKeysAreReported() throws Exception {
		for (String key : new String[] { "bucket", "region", "accessKey",
				"secretKey" }) {
			Config cfg = config(storeSection() + CREDENTIALS
					.substring(CREDENTIALS.indexOf('\n') + 1));
			cfg.unset("objectStore", "s3", key);

			IOException e = assertThrows(key, IOException.class,
					() -> ExternalObjectDatabase.fromConfig(cfg, objects));
			assertTrue(e.getMessage(), e.getMessage().contains("'s3'"));
			assertTrue(e.getMessage(), e.getMessage().contains(key));
			assertFalse(e.getMessage(), e.getMessage().contains(SECRET));
		}
	}

	@Test
	public void invalidEndpointIsReported() throws Exception {
		for (String endpoint : new String[] { "localhost:9090",
				"http://bad host/" }) {
			Config cfg = config(storeSection() + CREDENTIALS
					.substring(CREDENTIALS.indexOf('\n') + 1));
			cfg.setString("objectStore", "s3", "endpoint", endpoint);

			IOException e = assertThrows(endpoint, IOException.class,
					() -> ExternalObjectDatabase.fromConfig(cfg, objects));
			assertTrue(e.getMessage(), e.getMessage().contains(endpoint));
		}
	}

	@Test
	public void invalidPrefixIsReported() throws Exception {
		Config cfg = config(storeSection()
				+ CREDENTIALS.substring(CREDENTIALS.indexOf('\n') + 1));
		cfg.setString("objectStore", "s3", "prefix", "my repo");

		IOException e = assertThrows(IOException.class,
				() -> ExternalObjectDatabase.fromConfig(cfg, objects));
		assertTrue(e.getMessage(), e.getMessage().contains("my repo"));
	}

	private String storeSection() throws IOException {
		return "[objectStore \"s3\"]\n" + "  type = s3\n"
				+ "  bucket = jgit-test\n" + "  region = eu-central-1\n"
				+ "  prefix = repos/foo.git\n"
				+ "  endpoint = " + server.endpoint() + "\n";
	}

	private ExternalObjectDatabase only(Config cfg) throws IOException {
		List<ExternalObjectDatabase> dbs = ExternalObjectDatabase
				.fromConfig(cfg, objects);
		assertEquals(1, dbs.size());
		return dbs.get(0);
	}

	private static void roundTrip(ExternalObjectDatabase db)
			throws IOException {
		try (ExternalObjectInserter ins = db.newInserter()) {
			ins.insert(ID, OBJ_BLOB, CONTENT.length,
					new ByteArrayInputStream(CONTENT));
		}
		assertTrue(db.has(ID));
		try (ObjectReader reader = db.newReader();
				ObjectStream in = reader.open(ID, OBJ_BLOB).openStream()) {
			assertArrayEquals(CONTENT, in.readAllBytes());
		}
	}

	private static Config config(String text) throws ConfigInvalidException {
		Config cfg = new Config();
		cfg.fromText(text);
		return cfg;
	}
}
