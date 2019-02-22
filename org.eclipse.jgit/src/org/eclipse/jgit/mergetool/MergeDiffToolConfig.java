/*
 * Copyright (C) 2019, Tim Neumann <Tim.Neumann@advantest.com>
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

package org.eclipse.jgit.mergetool;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import static org.eclipse.jgit.lib.ConfigConstants.*;

/**
 * This class keeps the config for the mergetool and difftool.
 *
 * @since 5.3
 * @noextend This class is not intended to be subclassed by clients.
 */
public class MergeDiffToolConfig {
	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<MergeDiffToolConfig> KEY = MergeDiffToolConfig::new;

	/**
	 * The active mergetool.
	 * <p>
	 * Configured by merge.tool
	 * </p>
	 */
	private final MergeDiffTool mergeTool;

	/**
	 * The active gui mergetool.
	 * <p>
	 * Configured by merge.tool
	 * </p>
	 */
	private final MergeDiffTool mergeGuiTool;

	/**
	 * The active difftool.
	 * <p>
	 * Configured by merge.tool
	 * </p>
	 * <p>
	 * If this is null {@link #mergeTool} is used.
	 * </p>
	 */
	private final MergeDiffTool diffTool;

	/**
	 * The active gui difftool.
	 * <p>
	 * Configured by merge.tool
	 * </p>
	 * <p>
	 * If this is null {@link #mergeGuiTool} is used.
	 * </p>
	 */
	private final MergeDiffTool diffGuiTool;

	/**
	 * Whether to prompt for the mergetool
	 * <p>
	 * Configured by mergetool.prompt
	 * </p>
	 */
	private final boolean mergeToolPrompt;

	/**
	 * Whether to prompt for the diffool
	 * <p>
	 * Configured by difftool.prompt
	 * </p>
	 */
	private final boolean diffToolPrompt;

	/**
	 * Whether to always trust the merge tool exit code (override this setting
	 * of the tools)
	 * <p>
	 * Configured by mergetool.trustExitCode
	 * </p>
	 */
	private final boolean mergeToolAlwaysTrustExitCode;

	/**
	 * Whether to never trust the merge tool exit code (override this setting of
	 * the tools)
	 * <p>
	 * Configured by mergetool.trustExitCode
	 * </p>
	 */
	private final boolean mergeToolNeverTrustExitCode;

	/**
	 * Whether to always trust the diff tool exit code (override this setting of
	 * the tools)
	 * <p>
	 * Configured by difftool.trustExitCode
	 * </p>
	 */
	private final boolean diffToolAlwaysTrustExitCode;

	/**
	 * Whether to never trust the diff tool exit code (override this setting of
	 * the tools)
	 * <p>
	 * Configured by difftool.trustExitCode
	 * </p>
	 */
	private final boolean diffToolNeverTrustExitCode;


	/**
	 * The available merge tools.
	 */
	private final Map<String, MergeDiffTool> mergeTools;

	/**
	 * The available diff tools.
	 * <p>
	 * Note: Any mergetool may also be used as a difftool. But this list does
	 * not contain all items of {@link #mergeTools}.
	 * </p>
	 */
	private final Map<String, MergeDiffTool> diffTools;

	/**
	 * @param rc
	 *            The config
	 */
	public MergeDiffToolConfig(Config rc) {
		mergeTools = new HashMap<>();
		// TODO: Maybe add builtins? See:
		// https://github.com/git/git/tree/master/mergetools
		readTools(rc, CONFIG_MERGETOOL_SECTION, mergeTools);

		diffTools = new HashMap<>();
		readTools(rc, CONFIG_DIFFTOOL_SECTION, diffTools);

		mergeTool = getTool(rc, CONFIG_MERGE_SECTION, CONFIG_KEY_TOOL,
				getMergeTools());

		mergeGuiTool = getTool(rc, CONFIG_MERGE_SECTION, CONFIG_KEY_GUITOOL,
				getMergeTools());

		diffTool = getTool(rc, CONFIG_DIFF_SECTION, CONFIG_KEY_TOOL,
				getDiffTools());

		diffGuiTool = getTool(rc, CONFIG_DIFF_SECTION, CONFIG_KEY_GUITOOL,
				getDiffTools());

		mergeToolPrompt = rc.getBoolean(CONFIG_MERGETOOL_SECTION,
				CONFIG_KEY_PROMPT, true);

		diffToolPrompt = rc.getBoolean(CONFIG_DIFFTOOL_SECTION,
				CONFIG_KEY_PROMPT, true);

		mergeToolAlwaysTrustExitCode = rc.getBoolean(CONFIG_MERGETOOL_SECTION,
				CONFIG_KEY_TRUST_EXIT_CODE, false);

		mergeToolNeverTrustExitCode = !rc.getBoolean(CONFIG_MERGETOOL_SECTION,
				CONFIG_KEY_TRUST_EXIT_CODE, true);

		diffToolAlwaysTrustExitCode = rc.getBoolean(CONFIG_DIFFTOOL_SECTION,
				CONFIG_KEY_TRUST_EXIT_CODE, false);

		diffToolNeverTrustExitCode = !rc.getBoolean(CONFIG_DIFFTOOL_SECTION,
				CONFIG_KEY_TRUST_EXIT_CODE, true);
	}

	private void readTools(Config rc, String section,
			Map<String, MergeDiffTool> map) {
		Set<String> subsections = rc.getSubsections(section);

		for (String toolName : subsections) {
			String cmd = rc.getString(section, toolName, CONFIG_KEY_CMD);
			String path = rc.getString(section, toolName, CONFIG_KEY_PATH);
			boolean trust = rc.getBoolean(section, toolName,
					CONFIG_KEY_TRUST_EXIT_CODE, false);
			map.put(toolName, new MergeDiffTool(toolName, cmd, path, trust));
		}
	}

	private MergeDiffTool getTool(Config rc, String section, String key,
			Map<String, MergeDiffTool> map) {
		String name = rc.getString(section, null, key);
		if (name == null)
			return null;

		return map.get(name);
	}

	/**
	 * @return the mergeTool
	 */
	public MergeDiffTool getMergeTool() {
		return mergeTool;
	}

	/**
	 * @return the mergeGuiTool
	 */
	public MergeDiffTool getMergeGuiTool() {
		return mergeGuiTool;
	}

	/**
	 * @return the diffTool
	 */
	public MergeDiffTool getDiffTool() {
		return (diffTool != null ? diffTool : mergeTool);
	}

	/**
	 * @return the diffGuiTool
	 */
	public MergeDiffTool getDiffGuiTool() {
		return (diffGuiTool != null ? diffGuiTool : mergeGuiTool);
	}

	/**
	 * @return the mergeToolPrompt
	 */
	public boolean isMergeToolPrompt() {
		return mergeToolPrompt;
	}

	/**
	 * @return the diffToolPrompt
	 */
	public boolean isDiffToolPrompt() {
		return diffToolPrompt;
	}

	/**
	 * @return the mergeTools
	 */
	public Map<String, MergeDiffTool> getMergeTools() {
		return new HashMap<>(mergeTools);
	}

	/**
	 * @return the diffTools
	 */
	public Map<String, MergeDiffTool> getDiffTools() {
		Map<String, MergeDiffTool> result = new HashMap<>(mergeTools);
		result.putAll(diffTools);
		return result;
	}

	/**
	 * @return the mergeToolAlwaysTrustExitCode
	 */
	public boolean isMergeToolAlwaysTrustExitCode() {
		return mergeToolAlwaysTrustExitCode;
	}

	/**
	 * @return the mergeToolNeverTrustExitCode
	 */
	public boolean isMergeToolNeverTrustExitCode() {
		return mergeToolNeverTrustExitCode;
	}

	/**
	 * @return the diffToolAlwaysTrustExitCode
	 */
	public boolean isDiffToolAlwaysTrustExitCode() {
		return diffToolAlwaysTrustExitCode;
	}

	/**
	 * @return the diffToolNeverTrustExitCode
	 */
	public boolean isDiffToolNeverTrustExitCode() {
		return diffToolNeverTrustExitCode;
	}

}
