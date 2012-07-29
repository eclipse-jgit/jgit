/*
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
import java.util.List;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.SampleDataRepositoryTestCase;
import org.eclipse.jgit.storage.file.FileRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TransportTest extends SampleDataRepositoryTestCase {
	private Transport transport;

	private RemoteConfig remoteConfig;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		final Config config = db.getConfig();
		remoteConfig = new RemoteConfig(config, "test");
		remoteConfig.addURI(new URIish("http://everyones.loves.git/u/2"));
		transport = null;
	}

	@Override
	@After
	public void tearDown() throws Exception {
		if (transport != null) {
			transport.close();
			transport = null;
		}
		super.tearDown();
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
		transport = Transport.open(db, remoteConfig);
		final Collection<RemoteRefUpdate> result = transport
				.findRemoteRefUpdatesFor(Collections.nCopies(1, new RefSpec(
						"refs/heads/master:refs/heads/x")));

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
		transport = Transport.open(db, remoteConfig);
		final Collection<RemoteRefUpdate> result = transport
				.findRemoteRefUpdatesFor(Collections.nCopies(1, new RefSpec(
						"+refs/heads/master")));

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
		transport = Transport.open(db, remoteConfig);
		final Collection<RemoteRefUpdate> result = transport
				.findRemoteRefUpdatesFor(Collections.nCopies(1, new RefSpec(
						"+refs/heads/*:refs/heads/test/*")));

		assertEquals(12, result.size());
		boolean foundA = false;
		boolean foundB = false;
		for (final RemoteRefUpdate rru : result) {
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
		transport = Transport.open(db, remoteConfig);
		final RefSpec specA = new RefSpec("+refs/heads/a:refs/heads/b");
		final RefSpec specC = new RefSpec("+refs/heads/c:refs/heads/d");
		final Collection<RefSpec> specs = Arrays.asList(specA, specC);
		final Collection<RemoteRefUpdate> result = transport
				.findRemoteRefUpdatesFor(specs);

		assertEquals(2, result.size());
		boolean foundA = false;
		boolean foundC = false;
		for (final RemoteRefUpdate rru : result) {
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
		transport = Transport.open(db, remoteConfig);
		final Collection<RemoteRefUpdate> result = transport
				.findRemoteRefUpdatesFor(Collections.nCopies(1, new RefSpec(
						"+refs/heads/a:refs/heads/a")));

		assertEquals(1, result.size());
		final TrackingRefUpdate tru = result.iterator().next()
				.getTrackingRefUpdate();
		assertEquals("refs/remotes/test/a", tru.getLocalName());
		assertEquals("refs/heads/a", tru.getRemoteName());
		assertEquals(db.resolve("refs/heads/a"), tru.getNewObjectId());
		assertEquals(ObjectId.zeroId(), tru.getOldObjectId());
	}

	@Test
	public void testLocalTransportWithRelativePath() throws Exception {
		FileRepository other = createWorkRepository();
		String otherDir = other.getWorkTree().getName();

		RemoteConfig config = new RemoteConfig(db.getConfig(), "other");
		config.addURI(new URIish("../" + otherDir));

		// Should not throw NoRemoteRepositoryException
		transport = Transport.open(db, config);
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
