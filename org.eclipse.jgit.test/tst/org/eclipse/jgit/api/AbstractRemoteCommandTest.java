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

import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

public class AbstractRemoteCommandTest extends RepositoryTestCase {

	protected static final String REMOTE_NAME = "test";

	protected RemoteConfig setupRemote()
			throws IOException, URISyntaxException {
		// create another repository
		Repository remoteRepository = createWorkRepository();

		// set it up as a remote to this repository
		final StoredConfig config = db.getConfig();
		RemoteConfig remoteConfig = new RemoteConfig(config, REMOTE_NAME);

		RefSpec refSpec = new RefSpec();
		refSpec = refSpec.setForceUpdate(true);
		refSpec = refSpec.setSourceDestination(Constants.R_HEADS + "*",
				Constants.R_REMOTES + REMOTE_NAME + "/*");
		remoteConfig.addFetchRefSpec(refSpec);

		URIish uri = new URIish(
				remoteRepository.getDirectory().toURI().toURL());
		remoteConfig.addURI(uri);

		remoteConfig.update(config);
		config.save();

		return remoteConfig;
	}

	protected void assertRemoteConfigEquals(RemoteConfig expected,
			RemoteConfig actual) {
		assertEquals(expected.getName(), actual.getName());
		assertEquals(expected.getURIs(), actual.getURIs());
		assertEquals(expected.getPushURIs(), actual.getPushURIs());
		assertEquals(expected.getFetchRefSpecs(), actual.getFetchRefSpecs());
		assertEquals(expected.getPushRefSpecs(), actual.getPushRefSpecs());
	}

}
