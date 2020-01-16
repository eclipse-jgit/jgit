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

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.eclipse.jgit.transport.RemoteConfig;
import org.junit.Test;

public class RemoteListCommandTest extends AbstractRemoteCommandTest {

	@Test
	public void testList() throws Exception {
		// setup an initial remote
		RemoteConfig remoteConfig = setupRemote();

		// execute the command to list the remotes
		List<RemoteConfig> remotes = Git.wrap(db).remoteList().call();

		// assert that there is only one remote
		assertEquals(1, remotes.size());
		// assert that the available remote is the initial remote
		assertRemoteConfigEquals(remoteConfig, remotes.get(0));
	}

}
