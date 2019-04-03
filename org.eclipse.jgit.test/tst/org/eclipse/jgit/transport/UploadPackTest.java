package org.eclipse.jgit.transport;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.theInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.storage.dfs.DfsGarbageCollector;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Sets;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevTree;
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

		assertFalse(client.getObjectDatabase().has(commit0.toObjectId()));

		// Fetch of the parent of the shallow commit
		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(commit0.name())));
			assertTrue(client.getObjectDatabase().has(commit0.toObjectId()));
		}
	}

	@Test
	public void testFetchUnreachableBlobWithBitmap() throws Exception {
		RevBlob blob = remote.blob("foo");
		remote.commit(remote.tree(remote.file("foo", blob)));
		generateBitmaps(server);

		testProtocol = generateReachableCommitUploadPackProtocol();
		uri = testProtocol.register(ctx, server);

		assertFalse(client.getObjectDatabase().has(blob.toObjectId()));

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

		assertFalse(client.getObjectDatabase().has(blob.toObjectId()));

		try (Transport tn = testProtocol.open(uri, client, "server")) {
			tn.fetch(NullProgressMonitor.INSTANCE,
					Collections.singletonList(new RefSpec(blob.name())));
			assertTrue(client.getObjectDatabase().has(blob.toObjectId()));
		}
	}

	@Test
	public void testFetchReachableBlobWithoutBitmap() throws Exception {
		RevBlob blob = remote.blob("foo");
		RevCommit commit = remote.commit(remote.tree(remote.file("foo", blob)));
		remote.update("master", commit);

		testProtocol = generateReachableCommitUploadPackProtocol();
		uri = testProtocol.register(ctx, server);

		assertFalse(client.getObjectDatabase().has(blob.toObjectId()));

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

			testProtocol = new TestProtocol<>(new UploadPackFactory<Object>() {
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

			testProtocol = new TestProtocol<>(new UploadPackFactory<Object>() {
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

			testProtocol = new TestProtocol<>(new UploadPackFactory<Object>() {
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

			testProtocol = new TestProtocol<>(new UploadPackFactory<Object>() {
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

			testProtocol = new TestProtocol<>(new UploadPackFactory<Object>() {
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

			testProtocol = new TestProtocol<>(new UploadPackFactory<Object>() {
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
				thrown.expectMessage(
						"filter requires server to advertise that capability");

				tn.fetch(NullProgressMonitor.INSTANCE,
						Collections.singletonList(new RefSpec(commit.name())));
			}
		}
	}

	/*
	 * Invokes UploadPack with protocol v2 and sends it the given lines,
	 * and returns UploadPack's output stream.
	 */
	private ByteArrayInputStream uploadPackV2Setup(
			Consumer<UploadPack> postConstructionSetup, String... inputLines)
			throws Exception {

		ByteArrayInputStream send = linesAsInputStream(inputLines);

		server.getConfig().setString("protocol", null, "version", "2");
		UploadPack up = new UploadPack(server);
		if (postConstructionSetup != null) {
			postConstructionSetup.accept(up);
		}
		up.setExtraParameters(Sets.of("version=2"));

		ByteArrayOutputStream recv = new ByteArrayOutputStream();
		up.upload(send, recv, null);

		return new ByteArrayInputStream(recv.toByteArray());
	}

	private static ByteArrayInputStream linesAsInputStream(String... inputLines)
			throws IOException {
		try (ByteArrayOutputStream send = new ByteArrayOutputStream()) {
			PacketLineOut pckOut = new PacketLineOut(send);
			for (String line : inputLines) {
				Objects.requireNonNull(line);
				if (line == PacketLineIn.END) {
					pckOut.end();
				} else if (line == PacketLineIn.DELIM) {
					pckOut.writeDelim();
				} else {
					pckOut.writeString(line);
				}
			}
			return new ByteArrayInputStream(send.toByteArray());
		}
	}

	/*
	 * Invokes UploadPack with protocol v2 and sends it the given lines.
	 * Returns UploadPack's output stream, not including the capability
	 * advertisement by the server.
	 */
	private ByteArrayInputStream uploadPackV2(
			Consumer<UploadPack> postConstructionSetup,
			String... inputLines)
			throws Exception {
		ByteArrayInputStream recvStream =
				uploadPackV2Setup(postConstructionSetup, inputLines);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		// drain capabilities
		while (pckIn.readString() != PacketLineIn.END) {
			// do nothing
		}
		return recvStream;
	}

	private ByteArrayInputStream uploadPackV2(String... inputLines) throws Exception {
		return uploadPackV2(null, inputLines);
	}

	private static class TestV2Hook implements ProtocolV2Hook {
		private CapabilitiesV2Request capabilitiesRequest;

		private LsRefsV2Request lsRefsRequest;

		private FetchV2Request fetchRequest;

		@Override
		public void onCapabilities(CapabilitiesV2Request req) {
			capabilitiesRequest = req;
		}

		@Override
		public void onLsRefs(LsRefsV2Request req) {
			lsRefsRequest = req;
		}

		@Override
		public void onFetch(FetchV2Request req) {
			fetchRequest = req;
		}
	}

	@Test
	public void testV2Capabilities() throws Exception {
		TestV2Hook hook = new TestV2Hook();
		ByteArrayInputStream recvStream = uploadPackV2Setup(
				(UploadPack up) -> {up.setProtocolV2Hook(hook);},
				PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);
		assertThat(hook.capabilitiesRequest, notNullValue());
		assertThat(pckIn.readString(), is("version 2"));
		assertThat(
				Arrays.asList(pckIn.readString(), pckIn.readString(),
						pckIn.readString()),
				// TODO(jonathantanmy) This check is written this way
				// to make it simple to see that we expect this list of
				// capabilities, but probably should be loosened to
				// allow additional commands to be added to the list,
				// and additional capabilities to be added to existing
				// commands without requiring test changes.
				hasItems("ls-refs", "fetch=shallow", "server-option"));
		assertTrue(pckIn.readString() == PacketLineIn.END);
	}

	private void checkAdvertisedIfAllowed(String configSection, String configName,
			String fetchCapability) throws Exception {
		server.getConfig().setBoolean(configSection, null, configName, true);
		ByteArrayInputStream recvStream =
				uploadPackV2Setup(null, PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is("version 2"));

		ArrayList<String> lines = new ArrayList<>();
		String line;
		while ((line = pckIn.readString()) != PacketLineIn.END) {
			if (line.startsWith("fetch=")) {
				assertThat(
					Arrays.asList(line.substring(6).split(" ")),
					containsInAnyOrder(fetchCapability, "shallow"));
				lines.add("fetch");
			} else {
				lines.add(line);
			}
		}
		assertThat(lines, containsInAnyOrder("ls-refs", "fetch", "server-option"));
	}

	private void checkUnadvertisedIfUnallowed(String fetchCapability) throws Exception {
		ByteArrayInputStream recvStream =
				uploadPackV2Setup(null, PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is("version 2"));

		ArrayList<String> lines = new ArrayList<>();
		String line;
		while ((line = pckIn.readString()) != PacketLineIn.END) {
			if (line.startsWith("fetch=")) {
				assertThat(
					Arrays.asList(line.substring(6).split(" ")),
					hasItems("shallow"));
				lines.add("fetch");
			} else {
				lines.add(line);
			}
		}
		assertThat(lines, hasItems("ls-refs", "fetch", "server-option"));
	}

	@Test
	public void testV2CapabilitiesAllowFilter() throws Exception {
		checkAdvertisedIfAllowed("uploadpack", "allowfilter", "filter");
		checkUnadvertisedIfUnallowed("filter");
	}

	@Test
	public void testV2CapabilitiesAllowRefInWant() throws Exception {
		checkAdvertisedIfAllowed("uploadpack", "allowrefinwant", "ref-in-want");
		checkUnadvertisedIfUnallowed("ref-in-want");
	}

	@Test
	public void testV2CapabilitiesAllowSidebandAll() throws Exception {
		checkAdvertisedIfAllowed("uploadpack", "allowsidebandall", "sideband-all");
		checkUnadvertisedIfUnallowed("sideband-all");
	}

	@Test
	public void testV2CapabilitiesRefInWantNotAdvertisedIfAdvertisingForbidden() throws Exception {
		server.getConfig().setBoolean("uploadpack", null, "allowrefinwant", true);
		server.getConfig().setBoolean("uploadpack", null, "advertiserefinwant", false);
		ByteArrayInputStream recvStream =
				uploadPackV2Setup(null, PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is("version 2"));
		assertThat(
				Arrays.asList(pckIn.readString(), pckIn.readString(),
						pckIn.readString()),
				hasItems("ls-refs", "fetch=shallow", "server-option"));
		assertTrue(pckIn.readString() == PacketLineIn.END);
	}

	@Test
	public void testV2EmptyRequest() throws Exception {
		ByteArrayInputStream recvStream = uploadPackV2(PacketLineIn.END);
		// Verify that there is nothing more after the capability
		// advertisement.
		assertEquals(0, recvStream.available());
	}

	@Test
	public void testV2LsRefs() throws Exception {
		RevCommit tip = remote.commit().message("message").create();
		remote.update("master", tip);
		server.updateRef("HEAD").link("refs/heads/master");
		RevTag tag = remote.tag("tag", tip);
		remote.update("refs/tags/tag", tag);

		TestV2Hook hook = new TestV2Hook();
		ByteArrayInputStream recvStream = uploadPackV2(
				(UploadPack up) -> {up.setProtocolV2Hook(hook);},
				"command=ls-refs\n", PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(hook.lsRefsRequest, notNullValue());
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

	@Test
	public void testV2LsRefsUnrecognizedArgument() throws Exception {
		thrown.expect(PackProtocolException.class);
		thrown.expectMessage("unexpected invalid-argument");
		uploadPackV2(
			"command=ls-refs\n",
			PacketLineIn.DELIM,
			"invalid-argument\n",
			PacketLineIn.END);
	}

	@Test
	public void testV2LsRefsServerOptions() throws Exception {
		String[] lines = { "command=ls-refs\n",
				"server-option=one\n", "server-option=two\n",
				PacketLineIn.DELIM,
				PacketLineIn.END };

		TestV2Hook testHook = new TestV2Hook();
		uploadPackV2Setup((UploadPack up) -> {up.setProtocolV2Hook(testHook);}, lines);

		LsRefsV2Request req = testHook.lsRefsRequest;
		assertEquals(2, req.getServerOptions().size());
		assertThat(req.getServerOptions(), hasItems("one", "two"));
	}

	/*
	 * Parse multiplexed packfile output from upload-pack using protocol V2
	 * into the client repository.
	 */
	private ReceivedPackStatistics parsePack(ByteArrayInputStream recvStream) throws Exception {
		return parsePack(recvStream, NullProgressMonitor.INSTANCE);
	}

	private ReceivedPackStatistics parsePack(ByteArrayInputStream recvStream, ProgressMonitor pm)
			throws Exception {
		SideBandInputStream sb = new SideBandInputStream(
				recvStream, pm,
				new StringWriter(), NullOutputStream.INSTANCE);
		PackParser pp = client.newObjectInserter().newPackParser(sb);
		pp.parse(NullProgressMonitor.INSTANCE);

		// Ensure that there is nothing left in the stream.
		assertEquals(-1, recvStream.read());

		return pp.getReceivedPackStatistics();
	}

	@Test
	public void testV2FetchRequestPolicyAdvertised() throws Exception {
		RevCommit advertized = remote.commit().message("x").create();
		RevCommit unadvertized = remote.commit().message("y").create();
		remote.update("branch1", advertized);

		// This works
		uploadPackV2(
			(UploadPack up) -> {up.setRequestPolicy(RequestPolicy.ADVERTISED);},
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + advertized.name() + "\n",
			PacketLineIn.END);

		// This doesn't
		thrown.expect(TransportException.class);
		thrown.expectMessage(Matchers.containsString(
					"want " + unadvertized.name() + " not valid"));
		uploadPackV2(
			(UploadPack up) -> {up.setRequestPolicy(RequestPolicy.ADVERTISED);},
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
			(UploadPack up) -> {up.setRequestPolicy(RequestPolicy.REACHABLE_COMMIT);},
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + reachable.name() + "\n",
			PacketLineIn.END);

		// This doesn't
		thrown.expect(TransportException.class);
		thrown.expectMessage(Matchers.containsString(
					"want " + unreachable.name() + " not valid"));
		uploadPackV2(
			(UploadPack up) -> {up.setRequestPolicy(RequestPolicy.REACHABLE_COMMIT);},
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
			(UploadPack up) -> {
				up.setRequestPolicy(RequestPolicy.TIP);
				up.setRefFilter(new RejectAllRefFilter());
			},
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + tip.name() + "\n",
			PacketLineIn.END);

		// This doesn't
		thrown.expect(TransportException.class);
		thrown.expectMessage(Matchers.containsString(
					"want " + parentOfTip.name() + " not valid"));
		uploadPackV2(
			(UploadPack up) -> {
				up.setRequestPolicy(RequestPolicy.TIP);
				up.setRefFilter(new RejectAllRefFilter());
			},
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
			(UploadPack up) -> {
				up.setRequestPolicy(RequestPolicy.REACHABLE_COMMIT_TIP);
				up.setRefFilter(new RejectAllRefFilter());
			},
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + parentOfTip.name() + "\n",
			PacketLineIn.END);

		// This doesn't
		thrown.expect(TransportException.class);
		thrown.expectMessage(Matchers.containsString(
					"want " + unreachable.name() + " not valid"));
		uploadPackV2(
			(UploadPack up) -> {
				up.setRequestPolicy(RequestPolicy.REACHABLE_COMMIT_TIP);
				up.setRefFilter(new RejectAllRefFilter());
			},
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
			(UploadPack up) -> {up.setRequestPolicy(RequestPolicy.ANY);},
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
		assertFalse(client.getObjectDatabase().has(fooParent.toObjectId()));
		assertTrue(client.getObjectDatabase().has(fooChild.toObjectId()));
		assertFalse(client.getObjectDatabase().has(barParent.toObjectId()));
		assertTrue(client.getObjectDatabase().has(barChild.toObjectId()));
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
		assertFalse(client.getObjectDatabase().has(fooParent.toObjectId()));
		assertTrue(client.getObjectDatabase().has(fooChild.toObjectId()));
		assertTrue(client.getObjectDatabase().has(barParent.toObjectId()));
		assertTrue(client.getObjectDatabase().has(barChild.toObjectId()));
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

	@Test
	public void testV2FetchIncludeTag() throws Exception {
		RevCommit commit = remote.commit().message("x").create();
		RevTag tag = remote.tag("tag", commit);
		remote.update("branch1", commit);
		remote.update("refs/tags/tag", tag);

		// Without include-tag.
		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + commit.toObjectId().getName() + "\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);
		assertFalse(client.getObjectDatabase().has(tag.toObjectId()));

		// With tag.
		recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + commit.toObjectId().getName() + "\n",
			"include-tag\n",
			"done\n",
			PacketLineIn.END);
		pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);
		assertTrue(client.getObjectDatabase().has(tag.toObjectId()));
	}

	@Test
	public void testV2FetchOfsDelta() throws Exception {
		String commonInBlob = "abcdefghijklmnopqrstuvwxyz";

		RevBlob parentBlob = remote.blob(commonInBlob + "a");
		RevCommit parent = remote.commit(remote.tree(remote.file("foo", parentBlob)));
		RevBlob childBlob = remote.blob(commonInBlob + "b");
		RevCommit child = remote.commit(remote.tree(remote.file("foo", childBlob)), parent);
		remote.update("branch1", child);

		// Without ofs-delta.
		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + child.toObjectId().getName() + "\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("packfile"));
		ReceivedPackStatistics stats = parsePack(recvStream);
		assertTrue(stats.getNumOfsDelta() == 0);

		// With ofs-delta.
		recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + child.toObjectId().getName() + "\n",
			"ofs-delta\n",
			"done\n",
			PacketLineIn.END);
		pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("packfile"));
		stats = parsePack(recvStream);
		assertTrue(stats.getNumOfsDelta() != 0);
	}

	@Test
	public void testV2FetchShallow() throws Exception {
		RevCommit commonParent = remote.commit().message("parent").create();
		RevCommit fooChild = remote.commit().message("x").parent(commonParent).create();
		RevCommit barChild = remote.commit().message("y").parent(commonParent).create();
		remote.update("branch1", barChild);

		// Without shallow, the server thinks that we have
		// commonParent, so it doesn't send it.
		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + barChild.toObjectId().getName() + "\n",
			"have " + fooChild.toObjectId().getName() + "\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);
		assertTrue(client.getObjectDatabase().has(barChild.toObjectId()));
		assertFalse(client.getObjectDatabase().has(commonParent.toObjectId()));

		// With shallow, the server knows that we don't have
		// commonParent, so it sends it.
		recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + barChild.toObjectId().getName() + "\n",
			"have " + fooChild.toObjectId().getName() + "\n",
			"shallow " + fooChild.toObjectId().getName() + "\n",
			"done\n",
			PacketLineIn.END);
		pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);
		assertTrue(client.getObjectDatabase().has(commonParent.toObjectId()));
	}

	@Test
	public void testV2FetchDeepenAndDone() throws Exception {
		RevCommit parent = remote.commit().message("parent").create();
		RevCommit child = remote.commit().message("x").parent(parent).create();
		remote.update("branch1", child);

		// "deepen 1" sends only the child.
		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + child.toObjectId().getName() + "\n",
			"deepen 1\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("shallow-info"));
		assertThat(pckIn.readString(), is("shallow " + child.toObjectId().getName()));
		assertThat(pckIn.readString(), theInstance(PacketLineIn.DELIM));
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);
		assertTrue(client.getObjectDatabase().has(child.toObjectId()));
		assertFalse(client.getObjectDatabase().has(parent.toObjectId()));

		// Without that, the parent is sent too.
		recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + child.toObjectId().getName() + "\n",
			"done\n",
			PacketLineIn.END);
		pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);
		assertTrue(client.getObjectDatabase().has(parent.toObjectId()));
	}

	@Test
	public void testV2FetchDeepenWithoutDone() throws Exception {
		RevCommit parent = remote.commit().message("parent").create();
		RevCommit child = remote.commit().message("x").parent(parent).create();
		remote.update("branch1", child);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + child.toObjectId().getName() + "\n",
			"deepen 1\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		// Verify that only the correct section is sent. "shallow-info"
		// is not sent because, according to the specification, it is
		// sent only if a packfile is sent.
		assertThat(pckIn.readString(), is("acknowledgments"));
		assertThat(pckIn.readString(), is("NAK"));
		assertThat(pckIn.readString(), theInstance(PacketLineIn.END));
	}

	@Test
	public void testV2FetchShallowSince() throws Exception {
		PersonIdent person = new PersonIdent(remote.getRepository());

		RevCommit beyondBoundary = remote.commit()
			.committer(new PersonIdent(person, 1510000000, 0)).create();
		RevCommit boundary = remote.commit().parent(beyondBoundary)
			.committer(new PersonIdent(person, 1520000000, 0)).create();
		RevCommit tooOld = remote.commit()
			.committer(new PersonIdent(person, 1500000000, 0)).create();
		RevCommit merge = remote.commit().parent(boundary).parent(tooOld)
			.committer(new PersonIdent(person, 1530000000, 0)).create();

		remote.update("branch1", merge);

		// Report that we only have "boundary" as a shallow boundary.
		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"shallow " + boundary.toObjectId().getName() + "\n",
			"deepen-since 1510000\n",
			"want " + merge.toObjectId().getName() + "\n",
			"have " + boundary.toObjectId().getName() + "\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("shallow-info"));

		// "merge" is shallow because one of its parents is committed
		// earlier than the given deepen-since time.
		assertThat(pckIn.readString(), is("shallow " + merge.toObjectId().getName()));

		// "boundary" is unshallow because its parent committed at or
		// later than the given deepen-since time.
		assertThat(pckIn.readString(), is("unshallow " + boundary.toObjectId().getName()));

		assertThat(pckIn.readString(), theInstance(PacketLineIn.DELIM));
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);

		// The server does not send this because it is committed
		// earlier than the given deepen-since time.
		assertFalse(client.getObjectDatabase().has(tooOld.toObjectId()));

		// The server does not send this because the client claims to
		// have it.
		assertFalse(client.getObjectDatabase().has(boundary.toObjectId()));

		// The server sends both these commits.
		assertTrue(client.getObjectDatabase().has(beyondBoundary.toObjectId()));
		assertTrue(client.getObjectDatabase().has(merge.toObjectId()));
	}

	@Test
	public void testV2FetchShallowSince_excludedParentWithMultipleChildren() throws Exception {
		PersonIdent person = new PersonIdent(remote.getRepository());

		RevCommit base = remote.commit()
			.committer(new PersonIdent(person, 1500000000, 0)).create();
		RevCommit child1 = remote.commit().parent(base)
			.committer(new PersonIdent(person, 1510000000, 0)).create();
		RevCommit child2 = remote.commit().parent(base)
			.committer(new PersonIdent(person, 1520000000, 0)).create();

		remote.update("branch1", child1);
		remote.update("branch2", child2);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"deepen-since 1510000\n",
			"want " + child1.toObjectId().getName() + "\n",
			"want " + child2.toObjectId().getName() + "\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("shallow-info"));

		// "base" is excluded, so its children are shallow.
		assertThat(
			Arrays.asList(pckIn.readString(), pckIn.readString()),
			hasItems(
				"shallow " + child1.toObjectId().getName(),
				"shallow " + child2.toObjectId().getName()));

		assertThat(pckIn.readString(), theInstance(PacketLineIn.DELIM));
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);

		// Only the children are sent.
		assertFalse(client.getObjectDatabase().has(base.toObjectId()));
		assertTrue(client.getObjectDatabase().has(child1.toObjectId()));
		assertTrue(client.getObjectDatabase().has(child2.toObjectId()));
	}

	@Test
	public void testV2FetchShallowSince_noCommitsSelected() throws Exception {
		PersonIdent person = new PersonIdent(remote.getRepository());

		RevCommit tooOld = remote.commit()
			.committer(new PersonIdent(person, 1500000000, 0)).create();

		remote.update("branch1", tooOld);

		thrown.expect(PackProtocolException.class);
		thrown.expectMessage("No commits selected for shallow request");
		uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"deepen-since 1510000\n",
			"want " + tooOld.toObjectId().getName() + "\n",
			"done\n",
			PacketLineIn.END);
	}

	@Test
	public void testV2FetchDeepenNot() throws Exception {
		RevCommit one = remote.commit().message("one").create();
		RevCommit two = remote.commit().message("two").parent(one).create();
		RevCommit three = remote.commit().message("three").parent(two).create();
		RevCommit side = remote.commit().message("side").parent(one).create();
		RevCommit merge = remote.commit().message("merge")
			.parent(three).parent(side).create();

		remote.update("branch1", merge);
		remote.update("side", side);

		// The client is a shallow clone that only has "three", and
		// wants "merge" while excluding "side".
		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"shallow " + three.toObjectId().getName() + "\n",
			"deepen-not side\n",
			"want " + merge.toObjectId().getName() + "\n",
			"have " + three.toObjectId().getName() + "\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("shallow-info"));

		// "merge" is shallow because "side" is excluded by deepen-not.
		// "two" is shallow because "one" (as parent of "side") is excluded by deepen-not.
		assertThat(
			Arrays.asList(pckIn.readString(), pckIn.readString()),
			hasItems(
				"shallow " + merge.toObjectId().getName(),
				"shallow " + two.toObjectId().getName()));

		// "three" is unshallow because its parent "two" is now available.
		assertThat(pckIn.readString(), is("unshallow " + three.toObjectId().getName()));

		assertThat(pckIn.readString(), theInstance(PacketLineIn.DELIM));
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);

		// The server does not send these because they are excluded by
		// deepen-not.
		assertFalse(client.getObjectDatabase().has(side.toObjectId()));
		assertFalse(client.getObjectDatabase().has(one.toObjectId()));

		// The server does not send this because the client claims to
		// have it.
		assertFalse(client.getObjectDatabase().has(three.toObjectId()));

		// The server sends both these commits.
		assertTrue(client.getObjectDatabase().has(merge.toObjectId()));
		assertTrue(client.getObjectDatabase().has(two.toObjectId()));
	}

	@Test
	public void testV2FetchDeepenNot_excludeDescendantOfWant() throws Exception {
		RevCommit one = remote.commit().message("one").create();
		RevCommit two = remote.commit().message("two").parent(one).create();
		RevCommit three = remote.commit().message("three").parent(two).create();
		RevCommit four = remote.commit().message("four").parent(three).create();

		remote.update("two", two);
		remote.update("four", four);

		thrown.expect(PackProtocolException.class);
		thrown.expectMessage("No commits selected for shallow request");
		uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"deepen-not four\n",
			"want " + two.toObjectId().getName() + "\n",
			"done\n",
			PacketLineIn.END);
	}

	@Test
	public void testV2FetchDeepenNot_supportAnnotatedTags() throws Exception {
		RevCommit one = remote.commit().message("one").create();
		RevCommit two = remote.commit().message("two").parent(one).create();
		RevCommit three = remote.commit().message("three").parent(two).create();
		RevCommit four = remote.commit().message("four").parent(three).create();
		RevTag twoTag = remote.tag("twotag", two);

		remote.update("refs/tags/twotag", twoTag);
		remote.update("four", four);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"deepen-not twotag\n",
			"want " + four.toObjectId().getName() + "\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("shallow-info"));
		assertThat(pckIn.readString(), is("shallow " + three.toObjectId().getName()));
		assertThat(pckIn.readString(), theInstance(PacketLineIn.DELIM));
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);
		assertFalse(client.getObjectDatabase().has(one.toObjectId()));
		assertFalse(client.getObjectDatabase().has(two.toObjectId()));
		assertTrue(client.getObjectDatabase().has(three.toObjectId()));
		assertTrue(client.getObjectDatabase().has(four.toObjectId()));
	}

	@Test
	public void testV2FetchDeepenNot_excludedParentWithMultipleChildren() throws Exception {
		PersonIdent person = new PersonIdent(remote.getRepository());

		RevCommit base = remote.commit()
			.committer(new PersonIdent(person, 1500000000, 0)).create();
		RevCommit child1 = remote.commit().parent(base)
			.committer(new PersonIdent(person, 1510000000, 0)).create();
		RevCommit child2 = remote.commit().parent(base)
			.committer(new PersonIdent(person, 1520000000, 0)).create();

		remote.update("base", base);
		remote.update("branch1", child1);
		remote.update("branch2", child2);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"deepen-not base\n",
			"want " + child1.toObjectId().getName() + "\n",
			"want " + child2.toObjectId().getName() + "\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("shallow-info"));

		// "base" is excluded, so its children are shallow.
		assertThat(
			Arrays.asList(pckIn.readString(), pckIn.readString()),
			hasItems(
				"shallow " + child1.toObjectId().getName(),
				"shallow " + child2.toObjectId().getName()));

		assertThat(pckIn.readString(), theInstance(PacketLineIn.DELIM));
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);

		// Only the children are sent.
		assertFalse(client.getObjectDatabase().has(base.toObjectId()));
		assertTrue(client.getObjectDatabase().has(child1.toObjectId()));
		assertTrue(client.getObjectDatabase().has(child2.toObjectId()));
	}

	@Test
	public void testV2FetchUnrecognizedArgument() throws Exception {
		thrown.expect(PackProtocolException.class);
		thrown.expectMessage("unexpected invalid-argument");
		uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"invalid-argument\n",
			PacketLineIn.END);
	}

	@Test
	public void testV2FetchServerOptions() throws Exception {
		String[] lines = { "command=fetch\n", "server-option=one\n",
				"server-option=two\n", PacketLineIn.DELIM,
				PacketLineIn.END };

		TestV2Hook testHook = new TestV2Hook();
		uploadPackV2Setup((UploadPack up) -> {up.setProtocolV2Hook(testHook);}, lines);

		FetchV2Request req = testHook.fetchRequest;
		assertNotNull(req);
		assertEquals(2, req.getServerOptions().size());
		assertThat(req.getServerOptions(), hasItems("one", "two"));
	}

	@Test
	public void testV2FetchFilter() throws Exception {
		RevBlob big = remote.blob("foobar");
		RevBlob small = remote.blob("fooba");
		RevTree tree = remote.tree(remote.file("1", big),
				remote.file("2", small));
		RevCommit commit = remote.commit(tree);
		remote.update("master", commit);

		server.getConfig().setBoolean("uploadpack", null, "allowfilter", true);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + commit.toObjectId().getName() + "\n",
			"filter blob:limit=5\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);

		assertFalse(client.getObjectDatabase().has(big.toObjectId()));
		assertTrue(client.getObjectDatabase().has(small.toObjectId()));
	}

	private void checkV2FetchWhenNotAllowed(String fetchLine, String expectedMessage)
			throws Exception {
		RevCommit commit = remote.commit().message("0").create();
		remote.update("master", commit);

		thrown.expect(PackProtocolException.class);
		thrown.expectMessage(expectedMessage);
		uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want " + commit.toObjectId().getName() + "\n",
			fetchLine,
			"done\n",
			PacketLineIn.END);
	}

	@Test
	public void testV2FetchFilterWhenNotAllowed() throws Exception {
		checkV2FetchWhenNotAllowed(
			"filter blob:limit=5\n",
			"unexpected filter blob:limit=5");
	}

	@Test
	public void testV2FetchWantRefIfNotAllowed() throws Exception {
		checkV2FetchWhenNotAllowed(
			"want-ref refs/heads/one\n",
			"unexpected want-ref refs/heads/one");
	}

	@Test
	public void testV2FetchSidebandAllIfNotAllowed() throws Exception {
		checkV2FetchWhenNotAllowed(
			"sideband-all\n",
			"unexpected sideband-all");
	}

	@Test
	public void testV2FetchWantRef() throws Exception {
		RevCommit one = remote.commit().message("1").create();
		RevCommit two = remote.commit().message("2").create();
		RevCommit three = remote.commit().message("3").create();
		remote.update("one", one);
		remote.update("two", two);
		remote.update("three", three);

		server.getConfig().setBoolean("uploadpack", null, "allowrefinwant", true);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want-ref refs/heads/one\n",
			"want-ref refs/heads/two\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("wanted-refs"));
		assertThat(
				Arrays.asList(pckIn.readString(), pckIn.readString()),
				hasItems(
					one.toObjectId().getName() + " refs/heads/one",
					two.toObjectId().getName() + " refs/heads/two"));
		assertThat(pckIn.readString(), theInstance(PacketLineIn.DELIM));
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);

		assertTrue(client.getObjectDatabase().has(one.toObjectId()));
		assertTrue(client.getObjectDatabase().has(two.toObjectId()));
		assertFalse(client.getObjectDatabase().has(three.toObjectId()));
	}

	@Test
	public void testV2FetchBadWantRef() throws Exception {
		RevCommit one = remote.commit().message("1").create();
		remote.update("one", one);

		server.getConfig().setBoolean("uploadpack", null, "allowrefinwant", true);

		try {
			uploadPackV2(
				"command=fetch\n",
				PacketLineIn.DELIM,
				"want-ref refs/heads/one\n",
				"want-ref refs/heads/nonExistentRef\n",
				"done\n",
				PacketLineIn.END);
		} catch (PackProtocolException e) {
			assertThat(
				e.getMessage(),
				containsString("Invalid ref name: refs/heads/nonExistentRef"));
			return;
		}
		fail("expected PackProtocolException");
	}

	@Test
	public void testV2FetchMixedWantRef() throws Exception {
		RevCommit one = remote.commit().message("1").create();
		RevCommit two = remote.commit().message("2").create();
		RevCommit three = remote.commit().message("3").create();
		remote.update("one", one);
		remote.update("two", two);
		remote.update("three", three);

		server.getConfig().setBoolean("uploadpack", null, "allowrefinwant", true);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want-ref refs/heads/one\n",
			"want " + two.toObjectId().getName() + "\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);
		assertThat(pckIn.readString(), is("wanted-refs"));
		assertThat(
				pckIn.readString(),
				is(one.toObjectId().getName() + " refs/heads/one"));
		assertThat(pckIn.readString(), theInstance(PacketLineIn.DELIM));
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);

		assertTrue(client.getObjectDatabase().has(one.toObjectId()));
		assertTrue(client.getObjectDatabase().has(two.toObjectId()));
		assertFalse(client.getObjectDatabase().has(three.toObjectId()));
	}

	@Test
	public void testV2FetchWantRefWeAlreadyHave() throws Exception {
		RevCommit one = remote.commit().message("1").create();
		remote.update("one", one);

		server.getConfig().setBoolean("uploadpack", null, "allowrefinwant", true);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want-ref refs/heads/one\n",
			"have " + one.toObjectId().getName(),
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		// The client still needs to know the hash of the object that
		// refs/heads/one points to, even though it already has the
		// object ...
		assertThat(pckIn.readString(), is("wanted-refs"));
		assertThat(
				pckIn.readString(),
				is(one.toObjectId().getName() + " refs/heads/one"));
		assertThat(pckIn.readString(), theInstance(PacketLineIn.DELIM));

		// ... but the client does not need the object itself.
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);
		assertFalse(client.getObjectDatabase().has(one.toObjectId()));
	}

	@Test
	public void testV2FetchWantRefAndDeepen() throws Exception {
		RevCommit parent = remote.commit().message("parent").create();
		RevCommit child = remote.commit().message("x").parent(parent).create();
		remote.update("branch1", child);

		server.getConfig().setBoolean("uploadpack", null, "allowrefinwant", true);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"want-ref refs/heads/branch1\n",
			"deepen 1\n",
			"done\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		// shallow-info appears first, then wanted-refs.
		assertThat(pckIn.readString(), is("shallow-info"));
		assertThat(pckIn.readString(), is("shallow " + child.toObjectId().getName()));
		assertThat(pckIn.readString(), theInstance(PacketLineIn.DELIM));
		assertThat(pckIn.readString(), is("wanted-refs"));
		assertThat(pckIn.readString(), is(child.toObjectId().getName() + " refs/heads/branch1"));
		assertThat(pckIn.readString(), theInstance(PacketLineIn.DELIM));
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);
		assertTrue(client.getObjectDatabase().has(child.toObjectId()));
		assertFalse(client.getObjectDatabase().has(parent.toObjectId()));
	}

	@Test
	public void testV2FetchMissingShallow() throws Exception {
		RevCommit one = remote.commit().message("1").create();
		RevCommit two = remote.commit().message("2").parent(one).create();
		RevCommit three = remote.commit().message("3").parent(two).create();
		remote.update("three", three);

		server.getConfig().setBoolean("uploadpack", null, "allowrefinwant",
				true);

		ByteArrayInputStream recvStream = uploadPackV2("command=fetch\n",
				PacketLineIn.DELIM,
				"want-ref refs/heads/three\n",
				"deepen 3",
				"shallow 0123012301230123012301230123012301230123",
				"shallow " + two.getName() + '\n',
				"done\n",
				PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is("shallow-info"));
		assertThat(pckIn.readString(),
				is("shallow " + one.toObjectId().getName()));
		assertThat(pckIn.readString(),
				is("unshallow " + two.toObjectId().getName()));
		assertThat(pckIn.readString(), theInstance(PacketLineIn.DELIM));
		assertThat(pckIn.readString(), is("wanted-refs"));
		assertThat(pckIn.readString(),
				is(three.toObjectId().getName() + " refs/heads/three"));
		assertThat(pckIn.readString(), theInstance(PacketLineIn.DELIM));
		assertThat(pckIn.readString(), is("packfile"));
		parsePack(recvStream);

		assertTrue(client.getObjectDatabase().has(one.toObjectId()));
		assertTrue(client.getObjectDatabase().has(two.toObjectId()));
		assertTrue(client.getObjectDatabase().has(three.toObjectId()));
	}

	@Test
	public void testV2FetchSidebandAllNoPackfile() throws Exception {
		RevCommit fooParent = remote.commit().message("x").create();
		RevCommit fooChild = remote.commit().message("x").parent(fooParent).create();
		RevCommit barParent = remote.commit().message("y").create();
		RevCommit barChild = remote.commit().message("y").parent(barParent).create();
		remote.update("branch1", fooChild);
		remote.update("branch2", barChild);

		server.getConfig().setBoolean("uploadpack", null, "allowsidebandall", true);

		ByteArrayInputStream recvStream = uploadPackV2(
			"command=fetch\n",
			PacketLineIn.DELIM,
			"sideband-all\n",
			"want " + fooChild.toObjectId().getName() + "\n",
			"want " + barChild.toObjectId().getName() + "\n",
			"have " + fooParent.toObjectId().getName() + "\n",
			PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is("\001acknowledgments"));
		assertThat(pckIn.readString(), is("\001ACK " + fooParent.getName()));
		assertThat(pckIn.readString(), theInstance(PacketLineIn.END));
	}

	@Test
	public void testV2FetchSidebandAllPackfile() throws Exception {
		RevCommit commit = remote.commit().message("x").create();
		remote.update("master", commit);

		server.getConfig().setBoolean("uploadpack", null, "allowsidebandall", true);

		ByteArrayInputStream recvStream = uploadPackV2("command=fetch\n",
				PacketLineIn.DELIM,
				"want " + commit.getName() + "\n",
				"sideband-all\n",
				"done\n",
				PacketLineIn.END);
		PacketLineIn pckIn = new PacketLineIn(recvStream);

		assertThat(pckIn.readString(), is("\001packfile"));
		parsePack(recvStream);
	}

	@Test
	public void testGetPeerAgentProtocolV0() throws Exception {
		RevCommit one = remote.commit().message("1").create();
		remote.update("one", one);

		UploadPack up = new UploadPack(server);
		ByteArrayInputStream send = linesAsInputStream(
				"want " + one.getName() + " agent=JGit-test/1.2.3\n",
				PacketLineIn.END,
				"have 11cedf1b796d44207da702f7d420684022fc0f09\n", "done\n");

		ByteArrayOutputStream recv = new ByteArrayOutputStream();
		up.upload(send, recv, null);

		assertEquals(up.getPeerUserAgent(), "JGit-test/1.2.3");
	}

	@Test
	public void testGetPeerAgentProtocolV2() throws Exception {
		server.getConfig().setString("protocol", null, "version", "2");

		RevCommit one = remote.commit().message("1").create();
		remote.update("one", one);

		UploadPack up = new UploadPack(server);
		up.setExtraParameters(Sets.of("version=2"));

		ByteArrayInputStream send = linesAsInputStream(
				"command=fetch\n", "agent=JGit-test/1.2.4\n",
				PacketLineIn.DELIM, "want " + one.getName() + "\n",
				"have 11cedf1b796d44207da702f7d420684022fc0f09\n", "done\n",
				PacketLineIn.END);

		ByteArrayOutputStream recv = new ByteArrayOutputStream();
		up.upload(send, recv, null);

		assertEquals(up.getPeerUserAgent(), "JGit-test/1.2.4");
	}

	private static class RejectAllRefFilter implements RefFilter {
		@Override
		public Map<String, Ref> filter(Map<String, Ref> refs) {
			return new HashMap<>();
		}
	}
}
