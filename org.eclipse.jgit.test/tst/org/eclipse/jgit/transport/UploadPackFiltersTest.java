package org.eclipse.jgit.transport;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.DfsGarbageCollector;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UploadPackFiltersTest {
	private URIish uri;

	private TestProtocol<Object> testProtocol;

	private final Object ctx = new Object();

	private InMemoryRepository client;

	@Before
	public void setUp() throws Exception {
		client = newRepo("client");
	}

	@After
	public void tearDown() {
		Transport.unregister(testProtocol);
	}

	@Test
	public void testFetchWithBlobNoneFilter() throws Exception {
		InMemoryRepository server2 = newRepo("server2");
		try (TestRepository<InMemoryRepository> remote2 = new TestRepository<>(
				server2)) {
			RevBlob blob1 = remote2.blob("foobar");
			RevBlob blob2 = remote2.blob("fooba");
			RevTree tree = remote2.tree(remote2.file("1", blob1),
					remote2.file("2", blob2));
			RevCommit commit = remote2.commit(tree);
			remote2.update("master", commit);

			server2.getConfig().setBoolean("uploadpack", null, "allowfilter",
					true);

			testProtocol = new TestProtocol<>((Object req, Repository db) -> {
				UploadPack up = new UploadPack(db);
				return up;
			}, null);
			uri = testProtocol.register(ctx, server2);

			try (Transport tn = testProtocol.open(uri, client, "server2")) {
				tn.setFilterSpec(FilterSpec.withBlobLimit(0));
				tn.fetch(NullProgressMonitor.INSTANCE,
						Collections.singletonList(new RefSpec(commit.name())));
				assertTrue(client.getObjectDatabase().has(tree.toObjectId()));
				assertFalse(client.getObjectDatabase().has(blob1.toObjectId()));
				assertFalse(client.getObjectDatabase().has(blob2.toObjectId()));
			}
		}
	}

	@Test
	public void testFetchExplicitBlobWithFilter() throws Exception {
		InMemoryRepository server2 = newRepo("server2");
		try (TestRepository<InMemoryRepository> remote2 = new TestRepository<>(
				server2)) {
			RevBlob blob1 = remote2.blob("foobar");
			RevBlob blob2 = remote2.blob("fooba");
			RevTree tree = remote2.tree(remote2.file("1", blob1),
					remote2.file("2", blob2));
			RevCommit commit = remote2.commit(tree);
			remote2.update("master", commit);
			remote2.update("a_blob", blob1);

			server2.getConfig().setBoolean("uploadpack", null, "allowfilter",
					true);

			testProtocol = new TestProtocol<>((Object req, Repository db) -> {
				UploadPack up = new UploadPack(db);
				return up;
			}, null);
			uri = testProtocol.register(ctx, server2);

			try (Transport tn = testProtocol.open(uri, client, "server2")) {
				tn.setFilterSpec(FilterSpec.withBlobLimit(0));
				tn.fetch(NullProgressMonitor.INSTANCE, Arrays.asList(
						new RefSpec(commit.name()), new RefSpec(blob1.name())));
				assertTrue(client.getObjectDatabase().has(tree.toObjectId()));
				assertTrue(client.getObjectDatabase().has(blob1.toObjectId()));
				assertFalse(client.getObjectDatabase().has(blob2.toObjectId()));
			}
		}
	}

	@Test
	public void testFetchWithBlobLimitFilter() throws Exception {
		InMemoryRepository server2 = newRepo("server2");
		try (TestRepository<InMemoryRepository> remote2 = new TestRepository<>(
				server2)) {
			RevBlob longBlob = remote2.blob("foobar");
			RevBlob shortBlob = remote2.blob("fooba");
			RevTree tree = remote2.tree(remote2.file("1", longBlob),
					remote2.file("2", shortBlob));
			RevCommit commit = remote2.commit(tree);
			remote2.update("master", commit);

			server2.getConfig().setBoolean("uploadpack", null, "allowfilter",
					true);

			testProtocol = new TestProtocol<>((Object req, Repository db) -> {
				UploadPack up = new UploadPack(db);
				return up;
			}, null);
			uri = testProtocol.register(ctx, server2);

			try (Transport tn = testProtocol.open(uri, client, "server2")) {
				tn.setFilterSpec(FilterSpec.withBlobLimit(5));
				tn.fetch(NullProgressMonitor.INSTANCE,
						Collections.singletonList(new RefSpec(commit.name())));
				assertFalse(
						client.getObjectDatabase().has(longBlob.toObjectId()));
				assertTrue(
						client.getObjectDatabase().has(shortBlob.toObjectId()));
			}
		}
	}

	@Test
	public void testFetchExplicitBlobWithFilterAndBitmaps() throws Exception {
		InMemoryRepository server2 = newRepo("server2");
		try (TestRepository<InMemoryRepository> remote2 = new TestRepository<>(
				server2)) {
			RevBlob blob1 = remote2.blob("foobar");
			RevBlob blob2 = remote2.blob("fooba");
			RevTree tree = remote2.tree(remote2.file("1", blob1),
					remote2.file("2", blob2));
			RevCommit commit = remote2.commit(tree);
			remote2.update("master", commit);
			remote2.update("a_blob", blob1);

			server2.getConfig().setBoolean("uploadpack", null, "allowfilter",
					true);

			// generate bitmaps
			new DfsGarbageCollector(server2).pack(null);
			server2.scanForRepoChanges();

			testProtocol = new TestProtocol<>((Object req, Repository db) -> {
				UploadPack up = new UploadPack(db);
				return up;
			}, null);
			uri = testProtocol.register(ctx, server2);

			try (Transport tn = testProtocol.open(uri, client, "server2")) {
				tn.setFilterSpec(FilterSpec.withBlobLimit(0));
				tn.fetch(NullProgressMonitor.INSTANCE, Arrays.asList(
						new RefSpec(commit.name()), new RefSpec(blob1.name())));
				assertTrue(client.getObjectDatabase().has(blob1.toObjectId()));
				assertFalse(client.getObjectDatabase().has(blob2.toObjectId()));
			}
		}
	}

	@Test
	public void testFetchWithBlobLimitFilterAndBitmaps() throws Exception {
		InMemoryRepository server2 = newRepo("server2");
		try (TestRepository<InMemoryRepository> remote2 = new TestRepository<>(
				server2)) {
			RevBlob longBlob = remote2.blob("foobar");
			RevBlob shortBlob = remote2.blob("fooba");
			RevTree tree = remote2.tree(remote2.file("1", longBlob),
					remote2.file("2", shortBlob));
			RevCommit commit = remote2.commit(tree);
			remote2.update("master", commit);

			server2.getConfig().setBoolean("uploadpack", null, "allowfilter",
					true);

			// generate bitmaps
			new DfsGarbageCollector(server2).pack(null);
			server2.scanForRepoChanges();

			testProtocol = new TestProtocol<>((Object req, Repository db) -> {
				UploadPack up = new UploadPack(db);
				return up;
			}, null);
			uri = testProtocol.register(ctx, server2);

			try (Transport tn = testProtocol.open(uri, client, "server2")) {
				tn.setFilterSpec(FilterSpec.withBlobLimit(5));
				tn.fetch(NullProgressMonitor.INSTANCE,
						Collections.singletonList(new RefSpec(commit.name())));
				assertFalse(
						client.getObjectDatabase().has(longBlob.toObjectId()));
				assertTrue(
						client.getObjectDatabase().has(shortBlob.toObjectId()));
			}
		}
	}

	@Test
	public void testFetchWithNonSupportingServer() throws Exception {
		InMemoryRepository server2 = newRepo("server2");
		try (TestRepository<InMemoryRepository> remote2 = new TestRepository<>(
				server2)) {
			RevBlob blob = remote2.blob("foo");
			RevTree tree = remote2.tree(remote2.file("1", blob));
			RevCommit commit = remote2.commit(tree);
			remote2.update("master", commit);

			server2.getConfig().setBoolean("uploadpack", null, "allowfilter",
					false);

			testProtocol = new TestProtocol<>((Object req, Repository db) -> {
				UploadPack up = new UploadPack(db);
				return up;
			}, null);
			uri = testProtocol.register(ctx, server2);

			try (Transport tn = testProtocol.open(uri, client, "server2")) {
				tn.setFilterSpec(FilterSpec.withBlobLimit(0));

				TransportException e = assertThrows(TransportException.class,
						() -> tn.fetch(NullProgressMonitor.INSTANCE, Collections
								.singletonList(new RefSpec(commit.name()))));
				assertThat(e.getMessage(), containsString(
						"filter requires server to advertise that capability"));
			}
		}
	}

	private static InMemoryRepository newRepo(String name) {
		return new InMemoryRepository(new DfsRepositoryDescription(name));
	}
}
