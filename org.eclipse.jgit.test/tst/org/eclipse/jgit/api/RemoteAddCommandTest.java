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

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;

public class RemoteAddCommandTest extends AbstractRemoteCommandTest {

	@Test
	public void testAdd() throws Exception {
		// create another repository
		Repository remoteRepository = createWorkRepository();
		URIish uri = new URIish(
				remoteRepository.getDirectory().toURI().toURL());

		// execute the command to add a new remote
		RemoteAddCommand cmd = Git.wrap(db).remoteAdd();
		cmd.setName(REMOTE_NAME);
		cmd.setUri(uri);
		RemoteConfig remote = cmd.call();

		// assert that the added remote represents the remote repository
		assertEquals(REMOTE_NAME, remote.getName());
		assertArrayEquals(new URIish[] { uri }, remote.getURIs().toArray());
		assertEquals(1, remote.getFetchRefSpecs().size());
		assertEquals(
				String.format("+refs/heads/*:refs/remotes/%s/*", REMOTE_NAME),
				remote.getFetchRefSpecs().get(0).toString());

		// assert that the added remote is available in the git configuration
		assertRemoteConfigEquals(remote,
				new RemoteConfig(db.getConfig(), REMOTE_NAME));
	}

}
