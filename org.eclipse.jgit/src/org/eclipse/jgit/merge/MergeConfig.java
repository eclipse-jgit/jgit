/*******************************************************************************
 * Copyright (c) 2014 Konrad Kügler
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
 *******************************************************************************/
package org.eclipse.jgit.merge;

import java.io.IOException;

import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.lib.Repository;

/**
 * Holds configuration for merging into a given branch
 *
 * @since 3.3
 */
public class MergeConfig {

	/**
	 * @param repo
	 * @return merge configuration for the current branch of the repository
	 */
	public static MergeConfig getConfigForCurrentBranch(Repository repo) {
		try {
			String branch = repo.getBranch();
			if (branch != null)
				return repo.getConfig().get(getParser(branch));
		} catch (IOException e) {
			// ignore
		}
		// use defaults if branch can't be determined
		return new MergeConfig();
	}

	/**
	 * @param branch
	 *            short branch name to get the configuration for, as returned
	 *            e.g. by {@link Repository#getBranch()}
	 * @return a parser for use with {@link Config#get(SectionParser)}
	 */
	public static final SectionParser<MergeConfig> getParser(
			final String branch) {
		return new MergeConfigSectionParser(branch);
	}

	private final FastForwardMode fastForwardMode;

	private final boolean squash;

	private final boolean commit;

	private MergeConfig(String branch, Config config) {
		String[] mergeOptions = getMergeOptions(branch, config);
		fastForwardMode = getFastForwardMode(config, mergeOptions);
		squash = isMergeConfigOptionSet("--squash", mergeOptions); //$NON-NLS-1$
		commit = !isMergeConfigOptionSet("--no-commit", mergeOptions); //$NON-NLS-1$
	}

	private MergeConfig() {
		fastForwardMode = FastForwardMode.FF;
		squash = false;
		commit = true;
	}

	/**
	 * @return the fast forward mode configured for this branch
	 */
	public FastForwardMode getFastForwardMode() {
		return fastForwardMode;
	}

	/**
	 * @return true if merges into this branch are configured to be squash
	 *         merges, false otherwise
	 */
	public boolean isSquash() {
		return squash;
	}

	/**
	 * @return false if --no-commit is configured for this branch, true
	 *         otherwise (event if --squash is configured)
	 */
	public boolean isCommit() {
		return commit;
	}

	private static FastForwardMode getFastForwardMode(Config config,
			String[] mergeOptions) {
		for (String option : mergeOptions) {
			for (FastForwardMode mode : FastForwardMode.values())
				if (mode.matchConfigValue(option))
					return mode;
		}
		FastForwardMode ffmode = FastForwardMode.valueOf(config.getEnum(
				ConfigConstants.CONFIG_KEY_MERGE, null,
				ConfigConstants.CONFIG_KEY_FF, FastForwardMode.Merge.TRUE));
		return ffmode;
	}

	private static boolean isMergeConfigOptionSet(String optionToLookFor,
			String[] mergeOptions) {
		for (String option : mergeOptions) {
			if (optionToLookFor.equals(option))
				return true;
		}
		return false;
	}

	private static String[] getMergeOptions(String branch, Config config) {
		String mergeOptions = config.getString(
				ConfigConstants.CONFIG_BRANCH_SECTION, branch,
				ConfigConstants.CONFIG_KEY_MERGEOPTIONS);
		if (mergeOptions != null)
			return mergeOptions.split("\\s"); //$NON-NLS-1$
		else
			return new String[0];
	}

	private static class MergeConfigSectionParser implements
			SectionParser<MergeConfig> {

		private final String branch;

		public MergeConfigSectionParser(String branch) {
			this.branch = branch;
		}

		public MergeConfig parse(Config cfg) {
			return new MergeConfig(branch, cfg);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof MergeConfigSectionParser)
				return branch.equals(((MergeConfigSectionParser) obj).branch);
			else
				return false;
		}

		@Override
		public int hashCode() {
			return branch.hashCode();
		}

	}

}
