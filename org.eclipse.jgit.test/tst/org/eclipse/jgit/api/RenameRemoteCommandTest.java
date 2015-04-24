/*
 * Copyright (C) 2015, Richard Veach <rveach02@gmail.com>
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests of {@link RenameRemoteCommand}
 */
public class RenameRemoteCommandTest extends RepositoryTestCase {

	private static final String REMOTE_NAME = Constants.DEFAULT_REMOTE_NAME;

	// private static final String BRANCH_NAME = "testBranch";

	private Git git;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		git = new Git(db);

		final StoredConfig config = db.getConfig();
		final RemoteConfig remoteConfig = new RemoteConfig(config, REMOTE_NAME);
		remoteConfig.addURI(new URIish("in-memory://"));
		remoteConfig.addFetchRefSpec(getFetchRefSpec(REMOTE_NAME));
		remoteConfig.update(config);
		config.save();
	}

	@Test
	public void renameRemoteNonExistantRemote() throws Exception {
		final String nonExistantRemoteName = "bad";

		try {
			git.remoteRename().setOldName(nonExistantRemoteName)
					.setNewName(nonExistantRemoteName).call();
			fail("Rename Remote with non-existant remote name should fail");
		} catch (RefNotFoundException e) {
			// expected
		}
	}

	@Test
	public void renameRemoteAlreadyExistsRemote() throws Exception {
		try {
			git.remoteRename().setOldName(REMOTE_NAME).setNewName(REMOTE_NAME)
					.call();
			fail("Rename Remote with existing ref name should fail");
		} catch (RefAlreadyExistsException e) {
			// expected
		}
	}

	@Test
	public void renameRemoteSuccess() throws Exception {
		final String newRemoteName = "newName";

		final RemoteConfig newRemote = git.remoteRename()
				.setOldName(REMOTE_NAME).setNewName(newRemoteName).call();

		assertNotNull(newRemote);

		final StoredConfig config = db.getConfig();

		// new remote exists
		assertTrue(!config.getNames(ConfigConstants.CONFIG_REMOTE_SECTION,
				newRemoteName).isEmpty());
		// old remote doesn't exist
		assertTrue(config.getNames(ConfigConstants.CONFIG_REMOTE_SECTION,
				REMOTE_NAME).isEmpty());

		// fetch ref was updated
		final List<RefSpec> fetchRefs = newRemote.getFetchRefSpecs();
		assertTrue(fetchRefs.size() == 1);
		assertEquals(fetchRefs.get(0), getFetchRefSpec(newRemoteName));

		// branch remote was updated
		// TODO
	}

	private RefSpec getFetchRefSpec(String refName) {
		return new RefSpec("+" + Constants.R_HEADS + "*:" + Constants.R_REMOTES
				+ refName + RefSpec.WILDCARD_SUFFIX);
	}
}
