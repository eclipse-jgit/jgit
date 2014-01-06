/*
 * Copyright (C) 2015, Kaloyan Raev <kaloyan.r@zend.com>
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

import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CLIRepositoryTestCase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;

public class RemoteTest extends CLIRepositoryTestCase {

	private StoredConfig config;

	private RemoteConfig remote;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		// create another repository
		Repository remoteRepository = createWorkRepository();

		// set it up as a remote to this repository
		config = db.getConfig();
		remote = new RemoteConfig(config, "test");
		remote.addFetchRefSpec(
				new RefSpec("+refs/heads/*:refs/remotes/test/*"));
		URIish uri = new URIish(
				remoteRepository.getDirectory().toURI().toURL());
		remote.addURI(uri);
		remote.update(config);
		config.save();

		Git.wrap(remoteRepository).commit().setMessage("initial commit").call();
	}

	@Test
	public void testList() throws Exception {
		assertArrayEquals(new String[] { remote.getName(), "" },
				execute("git remote"));
	}

	@Test
	public void testVerboseList() throws Exception {
		assertArrayEquals(
				new String[] {
						String.format("%s\t%s (fetch)", remote.getName(),
								remote.getURIs().get(0)),
						String.format("%s\t%s (push)", remote.getName(),
								remote.getURIs().get(0)),
						"" },
				execute("git remote -v"));
	}

	@Test
	public void testAdd() throws Exception {
		assertArrayEquals(new String[] { "" },
				execute("git remote add second git://test.com/second"));

		List<RemoteConfig> remotes = RemoteConfig.getAllRemoteConfigs(config);
		assertEquals(2, remotes.size());
		assertEquals("second", remotes.get(0).getName());
		assertEquals("test", remotes.get(1).getName());
	}

	@Test
	public void testRemove() throws Exception {
		assertArrayEquals(new String[] { "" },
				execute("git remote remove test"));

		assertTrue(RemoteConfig.getAllRemoteConfigs(config).isEmpty());
	}

	@Test
	public void testSetUrl() throws Exception {
		assertArrayEquals(new String[] { "" },
				execute("git remote set-url test git://test.com/test"));

		RemoteConfig result = new RemoteConfig(config, "test");
		assertEquals("test", result.getName());
		assertArrayEquals(new URIish[] { new URIish("git://test.com/test") },
				result.getURIs().toArray());
		assertTrue(result.getPushURIs().isEmpty());
	}

	@Test
	public void testSetUrlPush() throws Exception {
		assertArrayEquals(new String[] { "" },
				execute("git remote set-url --push test git://test.com/test"));

		RemoteConfig result = new RemoteConfig(config, "test");
		assertEquals("test", result.getName());
		assertEquals(remote.getURIs(), result.getURIs());
		assertArrayEquals(new URIish[] { new URIish("git://test.com/test") },
				result.getPushURIs().toArray());
	}

	@Test
	public void testUpdate() throws Exception {
		assertArrayEquals(new String[] {
				"From " + remote.getURIs().get(0).toString(),
				" * [new branch]      master     -> test/master", "", "" },
				execute("git remote update test"));
	}

}
