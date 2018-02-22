package org.eclipse.jgit.transport;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.DfsGarbageCollector;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Sets;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.transport.UploadPack.RequestPolicy;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Tests for server upload-pack utilities.
 */
public class UploadPackTest {
	@Rule
	public ExpectedException thrown = ExpectedException.none();

	private URIish uri;

	private TestProtocol<Object> testProtocol;

	private Object ctx = new Object();

	private InMemoryRepository server;

	private InMemoryRepository client;

	private TestRepository<InMemoryRepository> remote;

	@Before
	public void setUp() throws Exception {
		server = newRepo("server");
		client = newRepo("client");

		remote = new TestRepository<>(server);
	}

	@After
	public void tearDown() {
		Transport.unregister(testProtocol);
	}

	private static InMemoryRepository newRepo(String name) {
		return new InMemoryRepository(new DfsRepositoryDescription(name));
	}

	private void generateBitmaps(InMemoryRepository repo) throws Exception {
		new DfsGarbageCollector(repo).pack(null);
		repo.scanForRepoChanges();
	}

	private static TestProtocol<Object> generateReachableCommitUploadPackProtocol() {
		return new TestProtocol<>(
				new UploadPackFactory<Object>() {
					@Override
					public UploadPack create(Object req, Repository db)
							throws ServiceNotEnabledException,
							ServiceNotAuthorizedException {
						UploadPack up = new UploadPack(db);
						up.setRequestPolicy(RequestPolicy.REACHABLE_COMMIT);
						return up;
					}
				}, null);
	}

	@Test
	public void testFetchParentOfShallowCommit() throws Exception {
		RevCommit commit0 = remote.commit().message("0").create();
		RevCommit commit1 = remote.commit().message("1").parent(commit0).create();
		RevCommit tip = remote.commit().message("2").parent(commit1).create();
		remote.update("master", tip);

		testProtocol = new TestProtocol<>(
				new UploadPackFactory<Object>() {
					@Override
					public UploadPack create(Object req, Repository db)
							throws ServiceNotEnabledException,
							ServiceNotAuthorizedException {
						UploadPack up = new UploadPack(db);
						up.setRequestPolicy(RequestPolicy.REACHABLE_COMMIT);
						// assume client has a shallow commit
						up.getRevWalk().assumeShallow(
								Collections.singleton(commit1.getId()));
						return up;
					}
				}, null);
		uri = testProtocol.register(ctx, server);

		assertFalse(client.hasObject(commit0.toObjectId()));

		// Fetch of the parent of the shallow commit
		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(commit0.name())));
			assertTrue(client.hasObject(commit0.toObjectId()));
		}
	}

	@Test
	public void testFetchUnreachableBlobWithBitmap() throws Exception {
		RevBlob blob = remote.blob("foo");
		remote.commit(remote.tree(remote.file("foo", blob)));
		generateBitmaps(server);

		testProtocol = generateReachableCommitUploadPackProtocol();
		uri = testProtocol.register(ctx, server);

		assertFalse(client.hasObject(blob.toObjectId()));

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			thrown.expect(TransportException.class);
			thrown.expectMessage(Matchers.containsString(
						"want " + blob.name() + " not valid"));
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(blob.name())));
		}
	}

	@Test
	public void testFetchReachableBlobWithBitmap() throws Exception {
		RevBlob blob = remote.blob("foo");
		RevCommit commit = remote.commit(remote.tree(remote.file("foo", blob)));
		remote.update("master", commit);
		generateBitmaps(server);

		testProtocol = generateReachableCommitUploadPackProtocol();
		uri = testProtocol.register(ctx, server);

		assertFalse(client.hasObject(blob.toObjectId()));

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(blob.name())));
			assertTrue(client.hasObject(blob.toObjectId()));
		}
	}

	@Test
	public void testFetchReachableBlobWithoutBitmap() throws Exception {
		RevBlob blob = remote.blob("foo");
		RevCommit commit = remote.commit(remote.tree(remote.file("foo", blob)));
		remote.update("master", commit);

		testProtocol = generateReachableCommitUploadPackProtocol();
		uri = testProtocol.register(ctx, server);

		assertFalse(client.hasObject(blob.toObjectId()));

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			thrown.expect(TransportException.class);
			thrown.expectMessage(Matchers.containsString(
						"want " + blob.name() + " not valid"));
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(blob.name())));
		}
	}

	@Test
	public void testFetchWithBlobNoneFilter() throws Exception {
		InMemoryRepository server2 = newRepo("server2");
		TestRepository<InMemoryRepository> remote2 =
				new TestRepository<>(server2);
		RevBlob blob1 = remote2.blob("foobar");
		RevBlob blob2 = remote2.blob("fooba");
		RevTree tree = remote2.tree(remote2.file("1", blob1),
				remote2.file("2", blob2));
		RevCommit commit = remote2.commit(tree);
		remote2.update("master", commit);

		server2.getConfig().setBoolean("uploadpack", null, "allowfilter", true);

		testProtocol = new TestProtocol<>(
				new UploadPackFactory<Object>() {
					@Override
					public UploadPack create(Object req, Repository db)
							throws ServiceNotEnabledException,
							ServiceNotAuthorizedException {
						UploadPack up = new UploadPack(db);
						return up;
					}
				}, null);
		uri = testProtocol.register(ctx, server2);

		try (Transport tn = testProtocol.open(uri, client, "server2")) {
			tn.setFilterBlobLimit(0);
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(commit.name())));
			assertTrue(client.hasObject(tree.toObjectId()));
			assertFalse(client.hasObject(blob1.toObjectId()));
			assertFalse(client.hasObject(blob2.toObjectId()));
		}
	}

	@Test
	public void testFetchWithBlobLimitFilter() throws Exception {
		InMemoryRepository server2 = newRepo("server2");
		TestRepository<InMemoryRepository> remote2 =
				new TestRepository<>(server2);
		RevBlob longBlob = remote2.blob("foobar");
		RevBlob shortBlob = remote2.blob("fooba");
		RevTree tree = remote2.tree(remote2.file("1", longBlob),
				remote2.file("2", shortBlob));
		RevCommit commit = remote2.commit(tree);
		remote2.update("master", commit);

		server2.getConfig().setBoolean("uploadpack", null, "allowfilter", true);

		testProtocol = new TestProtocol<>(
				new UploadPackFactory<Object>() {
					@Override
					public UploadPack create(Object req, Repository db)
							throws ServiceNotEnabledException,
							ServiceNotAuthorizedException {
						UploadPack up = new UploadPack(db);
						return up;
					}
				}, null);
		uri = testProtocol.register(ctx, server2);

		try (Transport tn = testProtocol.open(uri, client, "server2")) {
			tn.setFilterBlobLimit(5);
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(commit.name())));
			assertFalse(client.hasObject(longBlob.toObjectId()));
			assertTrue(client.hasObject(shortBlob.toObjectId()));
		}
	}

	@Test
	public void testFetchWithBlobLimitFilterAndBitmaps() throws Exception {
		InMemoryRepository server2 = newRepo("server2");
		TestRepository<InMemoryRepository> remote2 =
				new TestRepository<>(server2);
		RevBlob longBlob = remote2.blob("foobar");
		RevBlob shortBlob = remote2.blob("fooba");
		RevTree tree = remote2.tree(remote2.file("1", longBlob),
				remote2.file("2", shortBlob));
		RevCommit commit = remote2.commit(tree);
		remote2.update("master", commit);

		server2.getConfig().setBoolean("uploadpack", null, "allowfilter", true);

		// generate bitmaps
		new DfsGarbageCollector(server2).pack(null);
		server2.scanForRepoChanges();

		testProtocol = new TestProtocol<>(
				new UploadPackFactory<Object>() {
					@Override
					public UploadPack create(Object req, Repository db)
							throws ServiceNotEnabledException,
							ServiceNotAuthorizedException {
						UploadPack up = new UploadPack(db);
						return up;
					}
				}, null);
		uri = testProtocol.register(ctx, server2);

		try (Transport tn = testProtocol.open(uri, client, "server2")) {
			tn.setFilterBlobLimit(5);
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(commit.name())));
			assertFalse(client.hasObject(longBlob.toObjectId()));
			assertTrue(client.hasObject(shortBlob.toObjectId()));
		}
	}

	@Test
	public void testFetchWithNonSupportingServer() throws Exception {
		InMemoryRepository server2 = newRepo("server2");
		TestRepository<InMemoryRepository> remote2 =
				new TestRepository<>(server2);
		RevBlob blob = remote2.blob("foo");
		RevTree tree = remote2.tree(remote2.file("1", blob));
		RevCommit commit = remote2.commit(tree);
		remote2.update("master", commit);

		server2.getConfig().setBoolean("uploadpack", null, "allowfilter", false);

		testProtocol = new TestProtocol<>(
				new UploadPackFactory<Object>() {
					@Override
					public UploadPack create(Object req, Repository db)
							throws ServiceNotEnabledException,
							ServiceNotAuthorizedException {
						UploadPack up = new UploadPack(db);
						return up;
					}
				}, null);
		uri = testProtocol.register(ctx, server2);

		try (Transport tn = testProtocol.open(uri, client, "server2")) {
			tn.setFilterBlobLimit(0);

			thrown.expect(TransportException.class);
			thrown.expectMessage("filter requires server to advertise that capability");

			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(commit.name())));
		}
	}

	private static ByteArrayInputStream send(String... lines) throws Exception {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		PacketLineOut pckOut = new PacketLineOut(os);
		for (String line : lines) {
			if (line == PacketLineIn.END) {
				pckOut.end();
			} else if (line == PacketLineIn.DELIM) {
				pckOut.writeDelim();
			} else {
				pckOut.writeString(line);
			}
		}
		byte[] a = os.toByteArray();
		return new ByteArrayInputStream(a);
	}

	/*
	 * Invokes UploadPack with protocol v2 and sends it the given lines.
	 * Returns UploadPack's output stream, not including the capability
	 * advertisement by the server.
	 */
	private ByteArrayInputStream uploadPackV2(String... inputLines) throws Exception {
		ByteArrayOutputStream send = new ByteArrayOutputStream();
		PacketLineOut pckOut = new PacketLineOut(send);
		for (String line : inputLines) {
			if (line == PacketLineIn.END) {
				pckOut.end();
			} else if (line == PacketLineIn.DELIM) {
				pckOut.writeDelim();
			} else {
				pckOut.writeString(line);
			}
		}

		server.getConfig().setString("protocol", null, "version", "2");
		UploadPack up = new UploadPack(server);
		up.setExtraParameters(Sets.of("version=2"));

		ByteArrayOutputStream recv = new ByteArrayOutputStream();
		up.upload(new ByteArrayInputStream(send.toByteArray()), recv, null);

		ByteArrayInputStream recvStream = new ByteArrayInputStream(recv.toByteArray());
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		// capability advertisement (always sent)
		assertThat(pckIn.readString(), is("version 2"));
		assertTrue(pckIn.readString() == PacketLineIn.END);
		return recvStream;
	}

	@Test
	public void testV2EmptyRequest() throws Exception {
		ByteArrayInputStream recvStream = uploadPackV2(PacketLineIn.END);
		// Verify that there is nothing more after the capability
		// advertisement.
		assertThat(recvStream.available(), is(0));
	}

}
