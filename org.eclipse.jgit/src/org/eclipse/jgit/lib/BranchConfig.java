/*
 * Copyright (C) 2011, Robin Stocker <robin@nibor.org>
 * Copyright (C) 2012, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import java.net.URISyntaxException;

import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;

/**
 * Branch section of a Git configuration file.
 */
public class BranchConfig {

	/**
	 * Config values for branch.[name].rebase (and pull.rebase).
	 *
	 * @since 4.5
	 */
	public enum BranchRebaseMode implements Config.ConfigEnum {

		/** Value for rebasing */
		REBASE("true"), //$NON-NLS-1$
		/** Value for rebasing preserving local merge commits */
		PRESERVE("preserve"), //$NON-NLS-1$
		/** Value for rebasing interactively */
		INTERACTIVE("interactive"), //$NON-NLS-1$
		/** Value for not rebasing at all but merging */
		NONE("false"); //$NON-NLS-1$

		private final String configValue;

		private BranchRebaseMode(String configValue) {
			this.configValue = configValue;
		}

		@Override
		public String toConfigValue() {
			return configValue;
		}

		@Override
		public boolean matchConfigValue(String s) {
			return configValue.equals(s);
		}
	}

	/**
	 * The value that means "local repository" for {@link #getRemote()}:
	 * {@value}
	 *
	 * @since 3.5
	 */
	public static final String LOCAL_REPOSITORY = "."; //$NON-NLS-1$

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
	public BranchConfig(Config config, String branchName) {
		this.config = config;
		this.branchName = branchName;
	}

	/**
	 * Get the full tracking branch name
	 *
	 * @return the full tracking branch name or <code>null</code> if it could
	 *         not be determined
	 */
	public String getTrackingBranch() {
		String remote = getRemoteOrDefault();
		String mergeRef = getMerge();
		if (remote == null || mergeRef == null)
			return null;

		if (isRemoteLocal())
			return mergeRef;

		return findRemoteTrackingBranch(remote, mergeRef);
	}

	/**
	 * Get the full remote-tracking branch name
	 *
	 * @return the full remote-tracking branch name or {@code null} if it could
	 *         not be determined. If you also want local tracked branches use
	 *         {@link #getTrackingBranch()} instead.
	 */
	public String getRemoteTrackingBranch() {
		String remote = getRemoteOrDefault();
		String mergeRef = getMerge();
		if (remote == null || mergeRef == null)
			return null;

		return findRemoteTrackingBranch(remote, mergeRef);
	}

	/**
	 * Whether the "remote" setting points to the local repository (with
	 * {@value #LOCAL_REPOSITORY})
	 *
	 * @return {@code true} if the "remote" setting points to the local
	 *         repository (with {@value #LOCAL_REPOSITORY}), false otherwise
	 * @since 3.5
	 */
	public boolean isRemoteLocal() {
		return LOCAL_REPOSITORY.equals(getRemote());
	}

	/**
	 * Get the remote this branch is configured to fetch from/push to
	 *
	 * @return the remote this branch is configured to fetch from/push to, or
	 *         {@code null} if not defined
	 * @since 3.5
	 */
	public String getRemote() {
		return config.getString(ConfigConstants.CONFIG_BRANCH_SECTION,
				branchName, ConfigConstants.CONFIG_KEY_REMOTE);
	}

	/**
	 * Get the name of the upstream branch as it is called on the remote
	 *
	 * @return the name of the upstream branch as it is called on the remote, or
	 *         {@code null} if not defined
	 * @since 3.5
	 */
	public String getMerge() {
		return config.getString(ConfigConstants.CONFIG_BRANCH_SECTION,
				branchName, ConfigConstants.CONFIG_KEY_MERGE);
	}

	/**
	 * Whether the branch is configured to be rebased
	 *
	 * @return {@code true} if the branch is configured to be rebased
	 * @since 3.5
	 */
	public boolean isRebase() {
		return getRebaseMode() != BranchRebaseMode.NONE;
	}

	/**
	 * Retrieves the config value of branch.[name].rebase.
	 *
	 * @return the {@link org.eclipse.jgit.lib.BranchConfig.BranchRebaseMode}
	 * @since 4.5
	 */
	public BranchRebaseMode getRebaseMode() {
		return config.getEnum(BranchRebaseMode.values(),
				ConfigConstants.CONFIG_BRANCH_SECTION, branchName,
				ConfigConstants.CONFIG_KEY_REBASE, BranchRebaseMode.NONE);
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

	private String getRemoteOrDefault() {
		String remote = getRemote();
		if (remote == null) {
			return Constants.DEFAULT_REMOTE_NAME;
		}
		return remote;
	}
}
