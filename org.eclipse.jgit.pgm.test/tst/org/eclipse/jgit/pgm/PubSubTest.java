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

package org.eclipse.jgit.pgm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.MessageFormat;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.PubSubConfig;
import org.eclipse.jgit.transport.PubSubConfig.Publisher;
import org.eclipse.jgit.transport.PubSubConfig.Subscriber;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Tests for Subscribe, Unsubscribe and SubscribeDaemon. */
public class PubSubTest extends CLIRepositoryTestCase {
	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();
		SubscribeDaemon.GLOBAL_PUBSUB_FILE = ".gitpubsubtest";
		new Git(db).commit().setMessage("initial commit").call();
	}

	@Test
	public void testSubscribeOutput() throws Exception {
		StoredConfig dbconfig = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(dbconfig, "origin");
		remoteConfig.addURI(
				new URIish("http://android.googlesource.com/android"));
		remoteConfig.update(dbconfig);
		dbconfig.save();

		String[] output = execute("git subscribe origin");
		assertArrayEquals("expected successful subscribe", //
				new String[] { MessageFormat.format(
						CLIText.get().didSubscribe, "origin",
						"http://android.googlesource.com/"), //
						"" /* ends with LF (last line empty) */}, output);
	}

	@Test
	public void testNoUriSubscribeOutput() throws Exception {
		StoredConfig dbconfig = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(dbconfig, "origin");
		remoteConfig.setTimeout(0); // Just to populate the config section
		remoteConfig.update(dbconfig);
		dbconfig.save();

		String[] output = execute("git subscribe origin");
		assertArrayEquals("expected failed subscribe", //
				new String[] { MessageFormat.format(
						CLIText.get().noRemoteUriSubscribe, "origin"), //
						"" /* ends with LF (last line empty) */}, output);
	}

	@Test
	public void testConfigWrite() throws Exception {
		StoredConfig dbconfig = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(dbconfig, "origin");
		remoteConfig.addURI(
				new URIish("http://android.googlesource.com/android"));
		remoteConfig.addFetchRefSpec(
				new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
		remoteConfig.update(dbconfig);
		dbconfig.save();

		String[] output = execute("git subscribe origin");
		assertArrayEquals("expected successful subscribe", //
				new String[] { MessageFormat.format(
						CLIText.get().didSubscribe, "origin",
						"http://android.googlesource.com/"), //
						"" /* ends with LF (last line empty) */}, output);
		PubSubConfig config = SubscribeDaemon.getConfig();
		assertEquals(1, config.getPublishers().size());
		Publisher pub = config.getPublisher("http://android.googlesource.com/");
		assertTrue(pub != null);
		assertEquals(
				new URIish("http://android.googlesource.com/"), pub.getUri());
		assertEquals(1, pub.getSubscribers().size());
		Subscriber sub = pub.getSubscribers().iterator().next();
		assertEquals("android", sub.getName());
		assertEquals("origin", sub.getRemote());
		assertEquals(1, sub.getSubscribeSpecs().size());
		RefSpec spec = sub.getSubscribeSpecs().get(0);
		assertEquals("refs/heads/*", spec.getSource());
	}

	@Test
	public void testUnsubscribeOutput() throws Exception {
		StoredConfig dbconfig = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(dbconfig, "origin");
		remoteConfig.addURI(
				new URIish("http://android.googlesource.com/android"));
		remoteConfig.update(dbconfig);
		dbconfig.save();

		String[] output = execute("git subscribe origin");
		output = execute("git unsubscribe origin");
		assertArrayEquals("expected successful unsubscribe", //
				new String[] { MessageFormat.format(
						CLIText.get().didUnsubscribe, "origin",
						"http://android.googlesource.com/"), //
						"" /* ends with LF (last line empty) */}, output);
	}

	@Test
	public void testNoUriUnsubscribeOutput() throws Exception {
		StoredConfig dbconfig = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(dbconfig, "origin");
		remoteConfig.setTimeout(0); // Just to populate the config section
		remoteConfig.update(dbconfig);
		dbconfig.save();

		String[] output = execute("git unsubscribe origin");
		assertArrayEquals("expected unsuccessful unsubscribe", //
				new String[] { MessageFormat.format(
						CLIText.get().noRemoteUriUnsubscribe, "origin"), //
						"" /* ends with LF (last line empty) */}, output);
	}

	@Test
	public void testNotFoundUnsubscribeOutput() throws Exception {
		StoredConfig dbconfig = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(dbconfig, "origin");
		remoteConfig.addURI(
				new URIish("http://android.googlesource.com/android"));
		remoteConfig.update(dbconfig);
		dbconfig.save();

		String[] output = execute("git unsubscribe origin");
		assertArrayEquals("expected unsuccessful unsubscribe", //
				new String[] { MessageFormat.format(
						CLIText.get().subscriptionDoesNotExist, "origin"), //
						"" /* ends with LF (last line empty) */}, output);
	}

	@Test
	public void testUnsubscribeConfig() throws Exception {
		StoredConfig dbconfig = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(dbconfig, "origin");
		remoteConfig.addURI(
				new URIish("http://android.googlesource.com/android"));
		remoteConfig.addFetchRefSpec(
				new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
		remoteConfig.update(dbconfig);
		dbconfig.save();

		String[] output = execute("git subscribe origin");
		output = execute("git unsubscribe origin");
		assertArrayEquals("expected successful unsubscribe", //
				new String[] { MessageFormat.format(
						CLIText.get().didUnsubscribe, "origin",
						"http://android.googlesource.com/"), //
						"" /* ends with LF (last line empty) */}, output);
		PubSubConfig config = SubscribeDaemon.getConfig();
		assertEquals(0, config.getPublishers().size());
	}

	@After
	@Override
	public void tearDown() throws Exception {
		SubscribeDaemon.getConfigFile().delete();
		super.tearDown();
	}
}