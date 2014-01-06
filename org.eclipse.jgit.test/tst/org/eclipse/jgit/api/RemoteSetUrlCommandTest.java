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
package org.eclipse.jgit.api;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;

public class RemoteSetUrlCommandTest extends AbstractRemoteCommandTest {

	@Test
	public void testSetUrl() throws Exception {
		// setup an initial remote
		setupRemote();

		// execute the command to change the fetch url
		RemoteSetUrlCommand cmd = Git.wrap(db).remoteSetUrl();
		cmd.setName(REMOTE_NAME);
		URIish newUri = new URIish("git://test.com/test");
		cmd.setUri(newUri);
		RemoteConfig remote = cmd.call();

		// assert that the changed remote has the new fetch url
		assertEquals(REMOTE_NAME, remote.getName());
		assertArrayEquals(new URIish[] { newUri }, remote.getURIs().toArray());

		// assert that the changed remote is available in the git configuration
		assertRemoteConfigEquals(remote,
				new RemoteConfig(db.getConfig(), REMOTE_NAME));
	}

	@Test
	public void testSetPushUrl() throws Exception {
		// setup an initial remote
		RemoteConfig remoteConfig = setupRemote();

		// execute the command to change the push url
		RemoteSetUrlCommand cmd = Git.wrap(db).remoteSetUrl();
		cmd.setName(REMOTE_NAME);
		URIish newUri = new URIish("git://test.com/test");
		cmd.setUri(newUri);
		cmd.setPush(true);
		RemoteConfig remote = cmd.call();

		// assert that the changed remote has the old fetch url and the new push
		// url
		assertEquals(REMOTE_NAME, remote.getName());
		assertEquals(remoteConfig.getURIs(), remote.getURIs());
		assertArrayEquals(new URIish[] { newUri },
				remote.getPushURIs().toArray());

		// assert that the changed remote is available in the git configuration
		assertRemoteConfigEquals(remote,
				new RemoteConfig(db.getConfig(), REMOTE_NAME));
	}

}
