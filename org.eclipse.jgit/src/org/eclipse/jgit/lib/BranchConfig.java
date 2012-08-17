/*
 * Copyright (C) 2011, Robin Stocker <robin@nibor.org>
 * Copyright (C) 2012, Matthias Sohn <matthias.sohn@sap.com>
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

package org.eclipse.jgit.lib;

import java.net.URISyntaxException;

import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;

/**
 * Branch section of a Git configuration file.
 */
public class BranchConfig {

	private final Config config;
	private final String branchName;

	/**
	 * Create a new branch config, which will read configuration from config
	 * about specified branch.
	 *
	 * @param config
	 *            the config to read from
	 * @param branchName
	 *            the short branch name of the section to read
	 */
	public BranchConfig(final Config config, String branchName) {
		this.config = config;
		this.branchName = branchName;
	}

	/**
	 * @return the full tracking branch name or <code>null</code> if it could
	 *         not be determined
	 */
	public String getTrackingBranch() {
		String remote = getRemote();
		String mergeRef = getMergeBranch();
		if (remote == null || mergeRef == null)
			return null;

		if (remote.equals("."))
			return mergeRef;

		return findRemoteTrackingBranch(remote, mergeRef);
	}

	/**
	 * @return the full remote-tracking branch name or {@code null} if it could
	 *         not be determined. If you also want local tracked branches use
	 *         {@link #getTrackingBranch()} instead.
	 */
	public String getRemoteTrackingBranch() {
		String remote = getRemote();
		String mergeRef = getMergeBranch();
		if (remote == null || mergeRef == null)
			return null;

		return findRemoteTrackingBranch(remote, mergeRef);
	}

	/**
	 * Finds the tracked remote tracking branch
	 *
	 * @param remote
	 *            Remote name
	 * @param mergeRef
	 *            merge Ref of the local branch tracking the remote tracking
	 *            branch
	 * @return full remote tracking branch name or null
	 */
	private String findRemoteTrackingBranch(String remote, String mergeRef) {
		RemoteConfig remoteConfig;
		try {
			remoteConfig = new RemoteConfig(config, remote);
		} catch (URISyntaxException e) {
			return null;
		}
		for (RefSpec refSpec : remoteConfig.getFetchRefSpecs()) {
			if (refSpec.matchSource(mergeRef)) {
				RefSpec expanded = refSpec.expandFromSource(mergeRef);
				return expanded.getDestination();
			}
		}
		return null;
	}

	private String getRemote() {
		String remoteName = config.getString(
				ConfigConstants.CONFIG_BRANCH_SECTION, branchName,
				ConfigConstants.CONFIG_KEY_REMOTE);
		if (remoteName == null)
			return Constants.DEFAULT_REMOTE_NAME;
		else
			return remoteName;
	}

	private String getMergeBranch() {
		String mergeRef = config.getString(
				ConfigConstants.CONFIG_BRANCH_SECTION, branchName,
				ConfigConstants.CONFIG_KEY_MERGE);
		return mergeRef;
	}
}
