package org.eclipse.jgit.transport;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.theInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.DfsGarbageCollector;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Sets;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.transport.UploadPack.RequestPolicy;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.eclipse.jgit.util.io.NullOutputStream;
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

	/*
	 * Invokes UploadPack with protocol v2 and sends it the given lines.
	 * Returns UploadPack's output stream, not including the capability
	 * advertisement by the server.
	 */
	private ByteArrayInputStream uploadPackV2(RequestPolicy requestPolicy,
			RefFilter refFilter, String... inputLines) throws Exception {
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
		if (requestPolicy != null)
			up.setRequestPolicy(requestPolicy);
		if (refFilter != null)
			up.setRefFilter(refFilter);
		up.setExtraParameters(Sets.of("version=2"));

		ByteArrayOutputStream recv = new ByteArrayOutputStream();
		up.upload(new ByteArrayInputStream(send.toByteArray()), recv, null);

		ByteArrayInputStream recvStream = new ByteArrayInputStream(recv.toByteArray());
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		// capability advertisement (always sent)
		assertThat(pckIn.readString(), is("version 2"));
		assertThat(
			Arrays.asList(pckIn.readString(), pckIn.readString()),
			// TODO(jonathantanmy) This check is written this way
			// to make it simple to see that we expect this list of
			// capabilities, but probably should be loosened to
			// allow additional commands to be added to the list,
			// and additional capabilities to be added to existing
			// commands without requiring test changes.
			hasItems("ls-refs", "fetch"));
		assertTrue(pckIn.readString() == PacketLineIn.END);
		return recvStream;
	}

	private ByteArrayInputStream uploadPackV2(String... inputLines) throws Exception {
		return uploadPackV2(null, null, inputLines);
	}

	@Test
	@SuppressWarnings("boxing")
	public void testV2EmptyRequest() throws Exception {
		ByteArrayInputStream recvStream = uploadPackV2(PacketLineIn.END);
		// Verify that there is nothing more after the capability
		// advertisement.
		assertThat(recvStream.available(), is(0));
	}

	@Test
	public void testV2LsRefs() throws Exception {
		RevCommit tip = remote.commit().message("message").create();
		remote.update("master", tip);
		server.updateRef("HEAD").link("refs/heads/master");
		RevTag tag = remote.tag("tag", tip);
		remote.update("refs/tags/tag", tag);

		ByteArrayInputStream recvStream = uploadPackV2("command=ls-refs\n", PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is(tip.toObjectId().getName() + " HEAD"));
		assertThat(pckIn.readString(), is(tip.toObjectId().getName() + " refs/heads/master"));
		assertThat(pckIn.readString(), is(tag.toObjectId().getName() + " refs/tags/tag"));
		assertTrue(pckIn.readString() == PacketLineIn.END);
	}

	@Test
	public void testV2LsRefsSymrefs() throws Exception {
		RevCommit tip = remote.commit().message("message").create();
		remote.update("master", tip);
		server.updateRef("HEAD").link("refs/heads/master");
		RevTag tag = remote.tag("tag", tip);
		remote.update("refs/tags/tag", tag);

		ByteArrayInputStream recvStream = uploadPackV2("command=ls-refs\n", PacketLineIn.DELIM, "symrefs", PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is(tip.toObjectId().getName() + " HEAD symref-target:refs/heads/master"));
		assertThat(pckIn.readString(), is(tip.toObjectId().getName() + " refs/heads/master"));
		assertThat(pckIn.readString(), is(tag.toObjectId().getName() + " refs/tags/tag"));
		assertTrue(pckIn.readString() == PacketLineIn.END);
	}

	@Test
	public void testV2LsRefsPeel() throws Exception {
		RevCommit tip = remote.commit().message("message").create();
		remote.update("master", tip);
		server.updateRef("HEAD").link("refs/heads/master");
		RevTag tag = remote.tag("tag", tip);
		remote.update("refs/tags/tag", tag);

		ByteArrayInputStream recvStream = uploadPackV2("command=ls-refs\n", PacketLineIn.DELIM, "peel", PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is(tip.toObjectId().getName() + " HEAD"));
		assertThat(pckIn.readString(), is(tip.toObjectId().getName() + " refs/heads/master"));
		assertThat(
			pckIn.readString(),
			is(tag.toObjectId().getName() + " refs/tags/tag peeled:"
				+ tip.toObjectId().getName()));
		assertTrue(pckIn.readString() == PacketLineIn.END);
	}

	@Test
	public void testV2LsRefsMultipleCommands() throws Exception {
		RevCommit tip = remote.commit().message("message").create();
		remote.update("master", tip);
		server.updateRef("HEAD").link("refs/heads/master");
		RevTag tag = remote.tag("tag", tip);
		remote.update("refs/tags/tag", tag);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=ls-refs\n", PacketLineIn.DELIM, "symrefs", "peel", PacketLineIn.END,
			"command=ls-refs\n", PacketLineIn.DELIM, PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is(tip.toObjectId().getName() + " HEAD symref-target:refs/heads/master"));
		assertThat(pckIn.readString(), is(tip.toObjectId().getName() + " refs/heads/master"));
		assertThat(
			pckIn.readString(),
			is(tag.toObjectId().getName() + " refs/tags/tag peeled:"
				+ tip.toObjectId().getName()));
		assertTrue(pckIn.readString() == PacketLineIn.END);
		assertThat(pckIn.readString(), is(tip.toObjectId().getName() + " HEAD"));
		assertThat(pckIn.readString(), is(tip.toObjectId().getName() + " refs/heads/master"));
		assertThat(pckIn.readString(), is(tag.toObjectId().getName() + " refs/tags/tag"));
		assertTrue(pckIn.readString() == PacketLineIn.END);
	}

	@Test
	public void testV2LsRefsRefPrefix() throws Exception {
		RevCommit tip = remote.commit().message("message").create();
		remote.update("master", tip);
		remote.update("other", tip);
		remote.update("yetAnother", tip);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=ls-refs\n",
			PacketLineIn.DELIM,
			"ref-prefix refs/heads/maste",
			"ref-prefix refs/heads/other",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is(tip.toObjectId().getName() + " refs/heads/master"));
		assertThat(pckIn.readString(), is(tip.toObjectId().getName() + " refs/heads/other"));
		assertTrue(pckIn.readString() == PacketLineIn.END);
	}

	@Test
	public void testV2LsRefsRefPrefixNoSlash() throws Exception {
		RevCommit tip = remote.commit().message("message").create();
		remote.update("master", tip);
		remote.update("other", tip);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=ls-refs\n",
			PacketLineIn.DELIM,
			"ref-prefix refs/heads/maste",
			"ref-prefix r",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is(tip.toObjectId().getName() + " refs/heads/master"));
		assertThat(pckIn.readString(), is(tip.toObjectId().getName() + " refs/heads/other"));
		assertTrue(pckIn.readString() == PacketLineIn.END);
	}

	/*
	 * Parse multiplexed packfile output from upload-pack using protocol V2
	 * into the client repository.
	 */
	private void parsePack(ByteArrayInputStream recvStream) throws Exception {
		parsePack(recvStream, NullProgressMonitor.INSTANCE);
	}

	private void parsePack(ByteArrayInputStream recvStream, ProgressMonitor pm)
			throws Exception {
		SideBandInputStream sb = new SideBandInputStream(
				recvStream, pm, new StringWriter(), NullOutputStream.INSTANCE);
		client.newObjectInserter().newPackParser(sb).parse(NullProgressMonitor.INSTANCE);
	}

	@Test
	public void testV2FetchRequestPolicyAdvertised() throws Exception {
		RevCommit advertized = remote.commit().message("x").create();
		RevCommit unadvertized = remote.commit().message("y").create();
		remote.update("branch1", advertized);

		// This works
		uploadPackV2(
			RequestPolicy.ADVERTISED,
			null,
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + advertized.name() + "\n",
			PacketLineIn.END);

		// This doesn't
		thrown.expect(TransportException.class);
		thrown.expectMessage(Matchers.containsString(
					"want " + unadvertized.name() + " not valid"));
		uploadPackV2(
			RequestPolicy.ADVERTISED,
			null,
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + unadvertized.name() + "\n",
			PacketLineIn.END);
	}

	@Test
	public void testV2FetchRequestPolicyReachableCommit() throws Exception {
		RevCommit reachable = remote.commit().message("x").create();
		RevCommit advertized = remote.commit().message("x").parent(reachable).create();
		RevCommit unreachable = remote.commit().message("y").create();
		remote.update("branch1", advertized);

		// This works
		uploadPackV2(
			RequestPolicy.REACHABLE_COMMIT,
			null,
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + reachable.name() + "\n",
			PacketLineIn.END);

		// This doesn't
		thrown.expect(TransportException.class);
		thrown.expectMessage(Matchers.containsString(
					"want " + unreachable.name() + " not valid"));
		uploadPackV2(
			RequestPolicy.REACHABLE_COMMIT,
			null,
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + unreachable.name() + "\n",
			PacketLineIn.END);
	}

	@Test
	public void testV2FetchRequestPolicyTip() throws Exception {
		RevCommit parentOfTip = remote.commit().message("x").create();
		RevCommit tip = remote.commit().message("y").parent(parentOfTip).create();
		remote.update("secret", tip);

		// This works
		uploadPackV2(
			RequestPolicy.TIP,
			new RejectAllRefFilter(),
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + tip.name() + "\n",
			PacketLineIn.END);

		// This doesn't
		thrown.expect(TransportException.class);
		thrown.expectMessage(Matchers.containsString(
					"want " + parentOfTip.name() + " not valid"));
		uploadPackV2(
			RequestPolicy.TIP,
			new RejectAllRefFilter(),
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + parentOfTip.name() + "\n",
			PacketLineIn.END);
	}

	@Test
	public void testV2FetchRequestPolicyReachableCommitTip() throws Exception {
		RevCommit parentOfTip = remote.commit().message("x").create();
		RevCommit tip = remote.commit().message("y").parent(parentOfTip).create();
		RevCommit unreachable = remote.commit().message("y").create();
		remote.update("secret", tip);

		// This works
		uploadPackV2(
			RequestPolicy.REACHABLE_COMMIT_TIP,
			new RejectAllRefFilter(),
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + parentOfTip.name() + "\n",
			PacketLineIn.END);

		// This doesn't
		thrown.expect(TransportException.class);
		thrown.expectMessage(Matchers.containsString(
					"want " + unreachable.name() + " not valid"));
		uploadPackV2(
			RequestPolicy.REACHABLE_COMMIT_TIP,
			new RejectAllRefFilter(),
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + unreachable.name() + "\n",
			PacketLineIn.END);
	}

	@Test
	public void testV2FetchRequestPolicyAny() throws Exception {
		RevCommit unreachable = remote.commit().message("y").create();

		// Exercise to make sure that even unreachable commits can be fetched
		uploadPackV2(
			RequestPolicy.ANY,
			null,
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + unreachable.name() + "\n",
			PacketLineIn.END);
	}

	@Test
	public void testV2FetchServerDoesNotStopNegotiation() throws Exception {
		RevCommit fooParent = remote.commit().message("x").create();
		RevCommit fooChild = remote.commit().message("x").parent(fooParent).create();
		RevCommit barParent = remote.commit().message("y").create();
		RevCommit barChild = remote.commit().message("y").parent(barParent).create();
		remote.update("branch1", fooChild);
		remote.update("branch2", barChild);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + fooChild.toObjectId().getName() + "\n",
			"want " + barChild.toObjectId().getName() + "\n",
			"have " + fooParent.toObjectId().getName() + "\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is("acknowledgments"));
		assertThat(pckIn.readString(), is("ACK " + fooParent.toObjectId().getName()));
		assertThat(pckIn.readString(), theInstance(PacketLineIn.END));
	}

	@Test
	public void testV2FetchServerStopsNegotiation() throws Exception {
		RevCommit fooParent = remote.commit().message("x").create();
		RevCommit fooChild = remote.commit().message("x").parent(fooParent).create();
		RevCommit barParent = remote.commit().message("y").create();
		RevCommit barChild = remote.commit().message("y").parent(barParent).create();
		remote.update("branch1", fooChild);
		remote.update("branch2", barChild);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + fooChild.toObjectId().getName() + "\n",
			"want " + barChild.toObjectId().getName() + "\n",
			"have " + fooParent.toObjectId().getName() + "\n",
			"have " + barParent.toObjectId().getName() + "\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is("acknowledgments"));
		assertThat(
			Arrays.asList(pckIn.readString(), pckIn.readString()),
			hasItems(
				"ACK " + fooParent.toObjectId().getName(),
				"ACK " + barParent.toObjectId().getName()));
		assertThat(pckIn.readString(), is("ready"));
		assertThat(pckIn.readString(), theInstance(PacketLineIn.DELIM));
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);
		assertFalse(client.hasObject(fooParent.toObjectId()));
		assertTrue(client.hasObject(fooChild.toObjectId()));
		assertFalse(client.hasObject(barParent.toObjectId()));
		assertTrue(client.hasObject(barChild.toObjectId()));
	}

	@Test
	public void testV2FetchClientStopsNegotiation() throws Exception {
		RevCommit fooParent = remote.commit().message("x").create();
		RevCommit fooChild = remote.commit().message("x").parent(fooParent).create();
		RevCommit barParent = remote.commit().message("y").create();
		RevCommit barChild = remote.commit().message("y").parent(barParent).create();
		remote.update("branch1", fooChild);
		remote.update("branch2", barChild);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + fooChild.toObjectId().getName() + "\n",
			"want " + barChild.toObjectId().getName() + "\n",
			"have " + fooParent.toObjectId().getName() + "\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);
		assertFalse(client.hasObject(fooParent.toObjectId()));
		assertTrue(client.hasObject(fooChild.toObjectId()));
		assertTrue(client.hasObject(barParent.toObjectId()));
		assertTrue(client.hasObject(barChild.toObjectId()));
	}

	@Test
	public void testV2FetchThinPack() throws Exception {
		String commonInBlob = "abcdefghijklmnopqrstuvwxyz";

		RevBlob parentBlob = remote.blob(commonInBlob + "a");
		RevCommit parent = remote.commit(remote.tree(remote.file("foo", parentBlob)));
		RevBlob childBlob = remote.blob(commonInBlob + "b");
		RevCommit child = remote.commit(remote.tree(remote.file("foo", childBlob)), parent);
		remote.update("branch1", child);

		// Pretend that we have parent to get a thin pack based on it.
		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + child.toObjectId().getName() + "\n",
			"have " + parent.toObjectId().getName() + "\n",
			"thin-pack\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is("packfile"));

		// Verify that we received a thin pack by trying to apply it
		// against the client repo, which does not have parent.
		thrown.expect(IOException.class);
		thrown.expectMessage("pack has unresolved deltas");
		parsePack(recvStream);
	}

	@Test
	public void testV2FetchNoProgress() throws Exception {
		RevCommit commit = remote.commit().message("x").create();
		remote.update("branch1", commit);

		// Without no-progress, progress is reported.
		StringWriter sw = new StringWriter();
		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + commit.toObjectId().getName() + "\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream, new TextProgressMonitor(sw));
		assertFalse(sw.toString().isEmpty());

		// With no-progress, progress is not reported.
		sw = new StringWriter();
		recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + commit.toObjectId().getName() + "\n",
			"no-progress\n",
			"done\n",
			PacketLineIn.END);
		pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream, new TextProgressMonitor(sw));
		assertTrue(sw.toString().isEmpty());
	}

	private static class RejectAllRefFilter implements RefFilter {
		@Override
		public Map<String, Ref> filter(Map<String, Ref> refs) {
			return new HashMap<>();
		}
	}
}
