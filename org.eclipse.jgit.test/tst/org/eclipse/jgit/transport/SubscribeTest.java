/*
 * Copyright (C) 2012, Google Inc.
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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.SampleDataRepositoryTestCase;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.SubscribeCommand.Command;
import org.junit.Before;
import org.junit.Test;

/** Subscriber tests. */
public class SubscribeTest extends SampleDataRepositoryTestCase {

	PubSubConfig.Publisher publisherConfig;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		String directory = db.getDirectory().getAbsolutePath();
		FileBasedConfig fc = db.getConfig();
		fc.load();
		RemoteConfig rc = new RemoteConfig(fc, "origin");
		rc.addFetchRefSpec(new RefSpec("refs/heads/master"));
		rc.addFetchRefSpec(new RefSpec("refs/tags/*:refs/doesntmatter/*"));
		rc.addURI(new URIish("http://example.com/testrepository"));
		rc.update(fc);
		fc.save();
		publisherConfig = new PubSubConfig.Publisher("http://example.com/");
		PubSubConfig.Subscriber subscribeConfig = new PubSubConfig.Subscriber(
				publisherConfig, "origin", directory);
		publisherConfig.addSubscriber(subscribeConfig);
	}

	@Test
	public void testSetup() throws Exception {
		Collection<PubSubConfig.Subscriber> subscribers = publisherConfig
				.getSubscribers();
		assertEquals(1, subscribers.size());
		PubSubConfig.Subscriber s1 = subscribers.iterator().next();
		assertEquals("origin", s1.getRemote());
		assertEquals(publisherConfig, s1.getPublisher());
		assertEquals(db.getDirectory().getAbsolutePath(), s1.getDirectory());
		assertEquals("testrepository", s1.getName());
	}

	@Test
	public void testSync() throws Exception {
		Subscriber s = new Subscriber(publisherConfig.getUri());
		Map<String, List<SubscribeCommand>> cmds = s.sync(publisherConfig);
		assertTrue(cmds.containsKey("testrepository"));
		assertEquals(2, cmds.get("testrepository").size());

		List<SubscribeCommand> expected = new ArrayList<SubscribeCommand>();
		expected.add(
				new SubscribeCommand(Command.SUBSCRIBE, "refs/heads/master"));
		expected.add(new SubscribeCommand(Command.SUBSCRIBE, "refs/tags/*"));
		expected.removeAll(cmds.get("testrepository"));
		assertEquals(0, expected.size());
	}

	@Test
	public void testResync() throws Exception {
		Subscriber s = new Subscriber(publisherConfig.getUri());

		s.sync(publisherConfig);

		String directory = db.getDirectory().getAbsolutePath();
		FileBasedConfig fc = db.getConfig();
		fc.load();
		RemoteConfig rc = new RemoteConfig(fc, "origin");
		rc.removeFetchRefSpec(new RefSpec("refs/tags/*:refs/doesntmatter/*"));
		rc.addFetchRefSpec(new RefSpec("refs/heads/branch"));
		rc.update(fc);
		fc.save();
		publisherConfig = new PubSubConfig.Publisher("http://example.com/");
		PubSubConfig.Subscriber subscribeConfig = new PubSubConfig.Subscriber(
				publisherConfig, "origin", directory);
		publisherConfig.addSubscriber(subscribeConfig);

		Map<String, List<SubscribeCommand>> after = s.sync(publisherConfig);

		assertTrue(after.containsKey("testrepository"));
		assertEquals(2, after.get("testrepository").size());
		List<SubscribeCommand> expected = new ArrayList<SubscribeCommand>();
		expected.add(new SubscribeCommand(Command.UNSUBSCRIBE, "refs/tags/*"));
		expected.add(
				new SubscribeCommand(Command.SUBSCRIBE, "refs/heads/branch"));
		expected.removeAll(after.get("testrepository"));
		assertEquals(0, expected.size());
	}

	@Test
	public void testRefSetup() throws Exception {
		SubscribedRepository r = new SubscribedRepository(publisherConfig
				.getSubscriber("origin", db.getDirectory().getAbsolutePath()));
		assertTrue(null != db.getRef(SubscribedRepository.PUBSUB_REF_PREFIX
				+ "origin/heads/master"));
		assertTrue(null != db.getRef(
				SubscribedRepository.PUBSUB_REF_PREFIX + "origin/tags/A"));
		assertTrue(null != db.getRef(
				SubscribedRepository.PUBSUB_REF_PREFIX + "origin/tags/B"));
	}

	@Test
	public void testRefFilter() throws Exception {
		SubscribedRepository r = new SubscribedRepository(publisherConfig
				.getSubscriber("origin", db.getDirectory().getAbsolutePath()));
		Map<String, Ref> refs = r.getMatchingRefs();
		assertTrue(refs.containsKey("refs/heads/master"));
		assertFalse(refs.containsKey("refs/heads/foo"));
	}
}
