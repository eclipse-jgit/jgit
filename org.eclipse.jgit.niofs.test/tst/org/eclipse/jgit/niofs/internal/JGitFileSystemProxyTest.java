/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_DAEMON_ENABLED;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_DAEMON_PORT;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

import org.junit.Test;

public class JGitFileSystemProxyTest extends BaseTest {

	private int gitDaemonPort;

	@Override
	public Map<String, String> getGitPreferences() {
		Map<String, String> gitPrefs = super.getGitPreferences();
		gitPrefs.put(GIT_DAEMON_ENABLED, "true");
		// use different port for every test -> easy to run tests in parallel
		gitDaemonPort = findFreePort();
		gitPrefs.put(GIT_DAEMON_PORT, String.valueOf(gitDaemonPort));
		return gitPrefs;
	}

	@Test
	public void proxyTest() throws IOException {
		final URI originRepo = URI.create("git://encoding-origin-name");

		final JGitFileSystem origin = (JGitFileSystem) provider.newFileSystem(originRepo, Collections.emptyMap());

		assertTrue(origin instanceof JGitFileSystemProxy);
		JGitFileSystemProxy proxy = (JGitFileSystemProxy) origin;
		JGitFileSystem realJGitFileSystem = proxy.getRealJGitFileSystem();
		assertTrue(realJGitFileSystem instanceof JGitFileSystemImpl);

		assertTrue(proxy.equals(realJGitFileSystem));
		assertTrue(realJGitFileSystem.equals(proxy));
	}
}