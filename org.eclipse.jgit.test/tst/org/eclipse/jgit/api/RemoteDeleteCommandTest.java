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

import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.transport.RemoteConfig;
import org.junit.Test;

public class RemoteDeleteCommandTest extends AbstractRemoteCommandTest {

	@Test
	public void testDelete() throws Exception {
		// setup an initial remote
		RemoteConfig remoteConfig = setupRemote();

		// execute the command to remove the remote
		RemoteRemoveCommand cmd = Git.wrap(db).remoteRemove();
		cmd.setRemoteName(REMOTE_NAME);
		RemoteConfig remote = cmd.call();

		// assert that the removed remote is the initial remote
		assertRemoteConfigEquals(remoteConfig, remote);
		// assert that there are no remotes left
		assertTrue(RemoteConfig.getAllRemoteConfigs(db.getConfig()).isEmpty());
	}

}
