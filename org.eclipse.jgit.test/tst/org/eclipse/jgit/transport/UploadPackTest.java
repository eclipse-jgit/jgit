package org.eclipse.jgit.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collection;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.DfsGarbageCollector;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
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

	private final Object ctx = new Object();

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
	public void testSingleBranchCloneTagChain() throws Exception {
		RevBlob blob0 = remote.blob("Initial content of first file");
		RevBlob blob1 = remote.blob("Second file content");
		RevCommit commit0 = remote.commit(remote.tree(remote.file("prvni.txt", blob0)));
		RevCommit commit1 = remote.commit(remote.tree(remote.file("druhy.txt", blob1)), commit0);
		remote.update("master", commit1);

        RevTag heavyTag1 = remote.tag("commitTagRing", commit0);
        remote.getRevWalk().parseHeaders(heavyTag1);
        RevTag heavyTag2 = remote.tag("middleTagRing", heavyTag1);
        /* ObjectId lightweightTag = */ remote.lightweightTag("refTagRing", heavyTag2);

        UploadPack up = new UploadPack(remote.getRepository());

        ByteArrayOutputStream cli = new ByteArrayOutputStream();
        PacketLineOut clientWant = new PacketLineOut(cli);
        clientWant.writeString("want " + commit1.name() + " multi_ack_detailed include-tag thin-pack ofs-delta agent=tempo/pflaska");
        clientWant.end();
        clientWant.writeString("done\n");

        ByteArrayOutputStream serverResponse = new ByteArrayOutputStream();

        up.setPreUploadHook(new PreUploadHook() {
            @Override
            public void onBeginNegotiateRound(UploadPack up,
                Collection<? extends ObjectId> wants,
                int cntOffered)
                throws ServiceMayNotContinueException
            {}

            @Override
            public void onEndNegotiateRound(UploadPack up,
                Collection<? extends ObjectId> wants,
                int cntCommon,
                int cntNotFound,
                boolean ready)
                throws ServiceMayNotContinueException
            {}

            @Override
            public void onSendPack(UploadPack up,
                Collection<? extends ObjectId> wants,
                Collection<? extends ObjectId> haves)
                throws ServiceMayNotContinueException
            {
                // collect pack data
                serverResponse.reset();
            }
        });
        up.upload(new ByteArrayInputStream(cli.toByteArray()), serverResponse, System.err);
        InputStream packReceived = new ByteArrayInputStream(serverResponse.toByteArray());
        try (ObjectInserter ins = client.newObjectInserter()) {
            PackParser parser = ins.newPackParser(packReceived);
            parser.setAllowThin(true);
			parser.setLockMessage("receive-tag-chain");

            final ProgressMonitor mlc = NullProgressMonitor.INSTANCE;
            /* PackLock packLock = */ parser.parse(mlc, mlc);
            ins.flush();
        }
        assertTrue(client.hasObject(blob0.toObjectId()));
        assertTrue(client.hasObject(blob1.toObjectId()));
        assertTrue(client.hasObject(commit0.toObjectId()));
        assertTrue(client.hasObject(commit1.toObjectId()));
        assertTrue(client.hasObject(heavyTag1.toObjectId()));
        assertTrue(client.hasObject(heavyTag2.toObjectId()));
	}

}
