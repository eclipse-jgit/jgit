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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.SampleDataRepositoryTestCase;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.SubscribeCommand.Command;
import org.junit.Before;
import org.junit.Test;

/** SubscribeProcess tests for syncing config to Subscriber state. */
public class SubscribeProcessTest extends SampleDataRepositoryTestCase {
	PubSubConfig.Publisher publisherConfig;

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		// Set up refs/remotes/ by doing a local fetch
		FileBasedConfig fc = db.getConfig();
		fc.load();
		RemoteConfig rc = new RemoteConfig(fc, "self");
		rc.addURI(new URIish(db.getWorkTree().getAbsolutePath()));
		rc.addFetchRefSpec(new RefSpec("refs/heads/*:refs/remotes/origin/*"));
		rc.update(fc);
		fc.save();
		new Git(db).fetch().setRemote("self").call();

		// Add fetch specs to origin
		String directory = db.getDirectory().getAbsolutePath();
		fc = db.getConfig();
		fc.load();
		rc = new RemoteConfig(fc, "origin");
		rc.addFetchRefSpec(
				new RefSpec("refs/heads/master:refs/remotes/origin/master"));
		rc.addFetchRefSpec(new RefSpec("refs/tags/*:refs/tags/*"));
		rc.addURI(new URIish("http://example.com/testrepository"));
		rc.update(fc);
		fc.save();
		publisherConfig = new PubSubConfig.Publisher("http://example.com/");
		PubSubConfig.Subscriber subscribeConfig = new PubSubConfig.Subscriber(
				publisherConfig, "origin", directory);
		publisherConfig.addSubscriber(subscribeConfig);
	}

	@Test
	public void testSync() throws Exception {
		SubscribeProcess s = new SubscribeProcess(
				publisherConfig.getUri(), new SubscriptionState());
		Map<String, List<SubscribeCommand>> cmds = s.applyConfig(publisherConfig);
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
		SubscribeProcess s = new SubscribeProcess(
				publisherConfig.getUri(), new SubscriptionState());

		s.applyConfig(publisherConfig);

		String directory = db.getDirectory().getAbsolutePath();
		FileBasedConfig fc = db.getConfig();
		fc.load();
		RemoteConfig rc = new RemoteConfig(fc, "origin");
		rc.removeFetchRefSpec(new RefSpec("refs/tags/*:refs/tags/*"));
		rc.addFetchRefSpec(
				new RefSpec("refs/heads/branch:refs/remotes/origin/branch"));
		rc.update(fc);
		fc.save();
		publisherConfig = new PubSubConfig.Publisher("http://example.com/");
		PubSubConfig.Subscriber subscribeConfig = new PubSubConfig.Subscriber(
				publisherConfig, "origin", directory);
		publisherConfig.addSubscriber(subscribeConfig);

		Map<String, List<SubscribeCommand>> after = s.applyConfig(publisherConfig);

		assertTrue(after.containsKey("testrepository"));
		assertEquals(2, after.get("testrepository").size());
		List<SubscribeCommand> expected = new ArrayList<SubscribeCommand>();
		expected.add(new SubscribeCommand(Command.UNSUBSCRIBE, "refs/tags/*"));
		expected.add(
				new SubscribeCommand(Command.SUBSCRIBE, "refs/heads/branch"));
		expected.removeAll(after.get("testrepository"));
		assertEquals(0, expected.size());
	}
}
