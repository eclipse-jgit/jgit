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

import java.io.IOException;
import java.util.Map;

import org.apache.sshd.server.SshServer;
import org.junit.Test;

import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_SSH_ENABLED;
import static org.eclipse.jgit.niofs.internal.JGitFileSystemProviderConfiguration.GIT_SSH_IDLE_TIMEOUT;
import static org.junit.Assert.assertEquals;

public class JGitFileSystemImplProviderSSHBadConfigTest extends AbstractTestInfra {

	@Override
	public Map<String, String> getGitPreferences() {
		Map<String, String> gitPrefs = super.getGitPreferences();

		gitPrefs.put(GIT_SSH_ENABLED, "true");
		gitPrefs.put(GIT_SSH_IDLE_TIMEOUT, "bz");

		return gitPrefs;
	}

	@Test
	public void testCheckDefaultSSHIdleWithInvalidArg() throws IOException {
		assertEquals(JGitFileSystemProviderConfiguration.DEFAULT_SSH_IDLE_TIMEOUT,
				provider.getGitSSHService().getProperties().get(SshServer.IDLE_TIMEOUT));
	}
}
