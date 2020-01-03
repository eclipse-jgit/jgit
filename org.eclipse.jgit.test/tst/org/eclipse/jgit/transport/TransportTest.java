/*
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.test.resources.SampleDataRepositoryTestCase;
import org.junit.Before;
import org.junit.Test;

public class TransportTest extends SampleDataRepositoryTestCase {
	private RemoteConfig remoteConfig;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		final Config config = db.getConfig();
		remoteConfig = new RemoteConfig(config, "test");
		remoteConfig.addURI(new URIish("http://everyones.loves.git/u/2"));
	}

	/**
	 * Test RefSpec to RemoteRefUpdate conversion with simple RefSpec - no
	 * wildcard, no tracking ref in repo configuration.
	 *
	 * @throws IOException
	 */
	@Test
	public void testFindRemoteRefUpdatesNoWildcardNoTracking()
			throws IOException {
		Collection<RemoteRefUpdate> result;
		try (Transport transport = Transport.open(db, remoteConfig)) {
			result = transport.findRemoteRefUpdatesFor(Collections.nCopies(1,
					new RefSpec("refs/heads/master:refs/heads/x")));
		}

		assertEquals(1, result.size());
		final RemoteRefUpdate rru = result.iterator().next();
		assertNull(rru.getExpectedOldObjectId());
		assertFalse(rru.isForceUpdate());
		assertEquals("refs/heads/master", rru.getSrcRef());
		assertEquals(db.resolve("refs/heads/master"), rru.getNewObjectId());
		assertEquals("refs/heads/x", rru.getRemoteName());
	}

	/**
	 * Test RefSpec to RemoteRefUpdate conversion with no-destination RefSpec
	 * (destination should be set up for the same name as source).
	 *
	 * @throws IOException
	 */
	@Test
	public void testFindRemoteRefUpdatesNoWildcardNoDestination()
			throws IOException {
		Collection<RemoteRefUpdate> result;
		try (Transport transport = Transport.open(db, remoteConfig)) {
			result = transport.findRemoteRefUpdatesFor(
					Collections.nCopies(1, new RefSpec("+refs/heads/master")));
		}

		assertEquals(1, result.size());
		final RemoteRefUpdate rru = result.iterator().next();
		assertNull(rru.getExpectedOldObjectId());
		assertTrue(rru.isForceUpdate());
		assertEquals("refs/heads/master", rru.getSrcRef());
		assertEquals(db.resolve("refs/heads/master"), rru.getNewObjectId());
		assertEquals("refs/heads/master", rru.getRemoteName());
	}

	/**
	 * Test RefSpec to RemoteRefUpdate conversion with wildcard RefSpec.
	 *
	 * @throws IOException
	 */
	@Test
	public void testFindRemoteRefUpdatesWildcardNoTracking() throws IOException {
		Collection<RemoteRefUpdate> result;
		try (Transport transport = Transport.open(db, remoteConfig)) {
			result = transport.findRemoteRefUpdatesFor(Collections.nCopies(1,
					new RefSpec("+refs/heads/*:refs/heads/test/*")));
		}

		assertEquals(12, result.size());
		boolean foundA = false;
		boolean foundB = false;
		for (RemoteRefUpdate rru : result) {
			if ("refs/heads/a".equals(rru.getSrcRef())
					&& "refs/heads/test/a".equals(rru.getRemoteName()))
				foundA = true;
			if ("refs/heads/b".equals(rru.getSrcRef())
					&& "refs/heads/test/b".equals(rru.getRemoteName()))
				foundB = true;
		}
		assertTrue(foundA);
		assertTrue(foundB);
	}

	/**
	 * Test RefSpec to RemoteRefUpdate conversion for more than one RefSpecs
	 * handling.
	 *
	 * @throws IOException
	 */
	@Test
	public void testFindRemoteRefUpdatesTwoRefSpecs() throws IOException {
		final RefSpec specA = new RefSpec("+refs/heads/a:refs/heads/b");
		final RefSpec specC = new RefSpec("+refs/heads/c:refs/heads/d");
		final Collection<RefSpec> specs = Arrays.asList(specA, specC);

		Collection<RemoteRefUpdate> result;
		try (Transport transport = Transport.open(db, remoteConfig)) {
			result = transport.findRemoteRefUpdatesFor(specs);
		}

		assertEquals(2, result.size());
		boolean foundA = false;
		boolean foundC = false;
		for (RemoteRefUpdate rru : result) {
			if ("refs/heads/a".equals(rru.getSrcRef())
					&& "refs/heads/b".equals(rru.getRemoteName()))
				foundA = true;
			if ("refs/heads/c".equals(rru.getSrcRef())
					&& "refs/heads/d".equals(rru.getRemoteName()))
				foundC = true;
		}
		assertTrue(foundA);
		assertTrue(foundC);
	}

	/**
	 * Test RefSpec to RemoteRefUpdate conversion for tracking ref search.
	 *
	 * @throws IOException
	 */
	@Test
	public void testFindRemoteRefUpdatesTrackingRef() throws IOException {
		remoteConfig.addFetchRefSpec(new RefSpec(
				"refs/heads/*:refs/remotes/test/*"));

		Collection<RemoteRefUpdate> result;
		try (Transport transport = Transport.open(db, remoteConfig)) {
			result = transport.findRemoteRefUpdatesFor(Collections.nCopies(1,
					new RefSpec("+refs/heads/a:refs/heads/a")));
		}

		assertEquals(1, result.size());
		final TrackingRefUpdate tru = result.iterator().next()
				.getTrackingRefUpdate();
		assertEquals("refs/remotes/test/a", tru.getLocalName());
		assertEquals("refs/heads/a", tru.getRemoteName());
		assertEquals(db.resolve("refs/heads/a"), tru.getNewObjectId());
		assertEquals(ObjectId.zeroId(), tru.getOldObjectId());
	}

	/**
	 * Test RefSpec to RemoteRefUpdate conversion with leases.
	 *
	 * @throws IOException
	 */
	@Test
	public void testFindRemoteRefUpdatesWithLeases() throws IOException {
		final RefSpec specA = new RefSpec("+refs/heads/a:refs/heads/b");
		final RefSpec specC = new RefSpec("+refs/heads/c:refs/heads/d");
		final Collection<RefSpec> specs = Arrays.asList(specA, specC);
		final Map<String, RefLeaseSpec> leases = new HashMap<>();
		leases.put("refs/heads/b",
				new RefLeaseSpec("refs/heads/b", "refs/heads/c"));

		Collection<RemoteRefUpdate> result;
		try (Transport transport = Transport.open(db, remoteConfig)) {
			result = transport.findRemoteRefUpdatesFor(specs, leases);
		}

		assertEquals(2, result.size());
		boolean foundA = false;
		boolean foundC = false;
		for (RemoteRefUpdate rru : result) {
			if ("refs/heads/a".equals(rru.getSrcRef())
					&& "refs/heads/b".equals(rru.getRemoteName())) {
				foundA = true;
				assertEquals(db.exactRef("refs/heads/c").getObjectId(),
						rru.getExpectedOldObjectId());
			}
			if ("refs/heads/c".equals(rru.getSrcRef())
					&& "refs/heads/d".equals(rru.getRemoteName())) {
				foundC = true;
				assertNull(rru.getExpectedOldObjectId());
			}
		}
		assertTrue(foundA);
		assertTrue(foundC);
	}

	@Test
	public void testLocalTransportWithRelativePath() throws Exception {
		Repository other = createWorkRepository();
		String otherDir = other.getWorkTree().getName();

		RemoteConfig config = new RemoteConfig(db.getConfig(), "other");
		config.addURI(new URIish("../" + otherDir));

		// Should not throw NoRemoteRepositoryException
		Transport.open(db, config).close();
	}

	@Test
	public void testLocalTransportFetchWithoutLocalRepository()
			throws Exception {
		URIish uri = new URIish("file://" + db.getWorkTree().getAbsolutePath());
		try (Transport transport = Transport.open(uri)) {
			try (FetchConnection fetchConnection = transport.openFetch()) {
				Ref head = fetchConnection.getRef(Constants.HEAD);
				assertNotNull(head);
			}
		}
	}

	@Test
	public void testSpi() {
		List<TransportProtocol> protocols = Transport.getTransportProtocols();
		assertNotNull(protocols);
		assertFalse(protocols.isEmpty());
		TransportProtocol found = null;
		for (TransportProtocol protocol : protocols)
			if (protocol.getSchemes().contains(SpiTransport.SCHEME)) {
				found = protocol;
				break;
			}
		assertEquals(SpiTransport.PROTO, found);
	}
}
