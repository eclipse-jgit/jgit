package org.eclipse.jgit.transport;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.transport.UploadPack.RequestPolicy;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for server upload-pack utilities.
 */
public class UploadPackTest {
	private URIish uri;

	private TestProtocol<Object> testProtocol;

	private Object ctx = new Object();

	private InMemoryRepository server;

	private InMemoryRepository client;

	private RevCommit commit0;

	private RevCommit commit1;

	private RevCommit tip;

	@Before
	public void setUp() throws Exception {
		server = newRepo("server");
		client = newRepo("client");

		TestRepository<InMemoryRepository> remote =
				new TestRepository<>(server);
		commit0 = remote.commit().message("0").create();
		commit1 = remote.commit().message("1").parent(commit0).create();
		tip = remote.commit().message("2").parent(commit1).create();
		remote.update("master", tip);
	}

	@After
	public void tearDown() {
		Transport.unregister(testProtocol);
	}

	private static InMemoryRepository newRepo(String name) {
		return new InMemoryRepository(new DfsRepositoryDescription(name));
	}

	@Test
	public void testFetchParentOfShallowCommit() throws Exception {
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
	public void testFetchWithBlobMaxBytes() throws Exception {
		InMemoryRepository server2 = newRepo("server2");
		TestRepository<InMemoryRepository> remote =
				new TestRepository<>(server2);
		RevBlob longBlob = remote.blob("foobar");
		RevBlob shortBlob = remote.blob("f");
		RevBlob longGitBlob = remote.blob("foobar2");
		RevTree tree = remote.tree(
				remote.file("1", longBlob), remote.file("2", shortBlob),
				remote.file(".gitspecial", longGitBlob));
		RevCommit commit = remote.commit(tree);
		remote.update("master", commit);

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
			tn.setBlobMaxBytes(5);
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(commit.name())));
			assertFalse(client.hasObject(longBlob.toObjectId()));
			assertTrue(client.hasObject(shortBlob.toObjectId()));
			assertTrue(client.hasObject(longGitBlob.toObjectId()));
		}
	}

	@Test
	public void testFetchWithBlobMaxBytes_GitFileAliasedAsNonGit() throws Exception {
		InMemoryRepository server2 = newRepo("server2");
		TestRepository<InMemoryRepository> remote =
				new TestRepository<>(server2);
		RevBlob blob1 = remote.blob("appears as .git*");
		RevBlob blob2 = remote.blob("also appears as .git*");
		RevBlob nonGitBlob = remote.blob("never appearing as .git*");

		// Place one blob before the .git* special file in alphabetical
		// order, and one blob after, in order to ensure that no matter
		// the order of iteration, a blob that appears as both a .git*
		// and a non-.git* file is still served.
		RevTree tree = remote.tree(
				remote.file(".fitone", blob1),
				remote.file(".gitone", blob1),
				remote.file(".gittwo", blob2),
				remote.file(".hittwo", blob2),
				remote.file("nonGitBlob", nonGitBlob));
		RevCommit commit = remote.commit(tree);
		remote.update("master", commit);

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
			tn.setBlobMaxBytes(0);
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(commit.name())));
			assertTrue(client.hasObject(blob1.toObjectId()));
			assertTrue(client.hasObject(blob2.toObjectId()));
			assertFalse(client.hasObject(nonGitBlob.toObjectId()));
		}
	}
}
