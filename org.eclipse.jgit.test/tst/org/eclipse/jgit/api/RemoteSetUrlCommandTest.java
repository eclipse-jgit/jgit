/*
 * Copyright (C) 2015, Kaloyan Raev <kaloyan.r@zend.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.eclipse.jgit.api.RemoteSetUrlCommand.UriType;
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
		cmd.setRemoteName(REMOTE_NAME);
		URIish newUri = new URIish("git://test.com/test");
		cmd.setRemoteUri(newUri);
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
		cmd.setRemoteName(REMOTE_NAME);
		URIish newUri = new URIish("git://test.com/test");
		cmd.setRemoteUri(newUri);
		cmd.setUriType(UriType.PUSH);
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
