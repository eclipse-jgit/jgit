/*
 * Copyright (C) 2018-2019, Andre Bossert <andre.bossert@siemens.com>
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

package org.eclipse.jgit.diffmergetool;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Config.SectionParser;

/**
 * Keeps track of difftool related configuration options.
 */
public class MergeToolConfig {

	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<MergeToolConfig> KEY = MergeToolConfig::new;

	private final String toolName;

	private final String guiToolName;

	private final boolean prompt;

	private final boolean keepBackup;

	private final boolean keepTemporaries;

	private final boolean writeToTemp;

	private final Map<String, IMergeTool> tools;

	private MergeToolConfig(Config rc) {
		// get default merge tool name (merge.tool)
		toolName = rc.getString(ConfigConstants.CONFIG_MERGE_SECTION, null,
				ConfigConstants.CONFIG_KEY_TOOL);
		// get default gui merge tool name (merge.guitool)
		guiToolName = rc.getString(ConfigConstants.CONFIG_MERGE_SECTION, null,
				ConfigConstants.CONFIG_KEY_GUITOOL);
		// get prompt option (mergetool.prompt, default = true)
		prompt = rc.getBoolean(ConfigConstants.CONFIG_MERGETOOL_SECTION,
				ConfigConstants.CONFIG_KEY_PROMPT, true);
		// get "keep backup" option (mergetool.keepBackup, default = true)
		keepBackup = rc.getBoolean(ConfigConstants.CONFIG_MERGETOOL_SECTION,
				ConfigConstants.CONFIG_KEY_KEEP_BACKUP, true);
		// get "keep temporaries" option (mergetool.keepTemporaries, default =
		// false)
		keepTemporaries = rc.getBoolean(
				ConfigConstants.CONFIG_MERGETOOL_SECTION,
				ConfigConstants.CONFIG_KEY_KEEP_TEMPORARIES, false);
		// get "write to temp" option (mergetool.writeToTemp, default = false)
		writeToTemp = rc.getBoolean(ConfigConstants.CONFIG_MERGETOOL_SECTION,
				ConfigConstants.CONFIG_KEY_WRITE_TO_TEMP, false);
		// get all merge tools
		tools = new HashMap<>();
		Set<String> subsections = rc
				.getSubsections(ConfigConstants.CONFIG_MERGETOOL_SECTION);
		for (String name : subsections) {
			// get the mergetool command (mergetool.<name>.cmd)
			String cmd = rc.getString(ConfigConstants.CONFIG_MERGETOOL_SECTION,
					name, ConfigConstants.CONFIG_KEY_CMD);
			// get the mergetool path (mergetool.<name>.path)
			String path = rc.getString(ConfigConstants.CONFIG_MERGETOOL_SECTION,
					name, ConfigConstants.CONFIG_KEY_PATH);
			// get trustExitCode option (mergetool.<name>.trustExitCode, default
			// = true)
			BooleanOption trustExitCode = BooleanOption.notDefined(true);
			String trustStr = rc.getString(
					ConfigConstants.CONFIG_MERGETOOL_SECTION, name,
					ConfigConstants.CONFIG_KEY_TRUST_EXIT_CODE);
			if (trustStr != null) {
				trustExitCode = BooleanOption
						.defined(Boolean.parseBoolean(trustStr));
			}
			if ((cmd != null) || (path != null)) {
				tools.put(name,
						new UserDefinedMergeTool(name, path, cmd,
								trustExitCode));
			}
		}
	}

	/**
	 * @return the default merge tool name (merge.tool)
	 */
	public String getDefaultToolName() {
		return toolName;
	}

	/**
	 * @return the default GUI merge tool name (merge.guitool)
	 */
	public String getDefaultGuiToolName() {
		return guiToolName;
	}

	/**
	 * @return the merge tool "prompt" option (mergetool.prompt)
	 */
	public boolean isPrompt() {
		return prompt;
	}

	/**
	 * @return the tool "keep backup" option
	 */
	public boolean isKeepBackup() {
		return keepBackup;
	}

	/**
	 * @return the tool "keepTemporaries" option
	 */
	public boolean isKeepTemporaries() {
		return keepTemporaries;
	}

	/**
	 * @return the tool "write to temp" option
	 */
	public boolean isWriteToTemp() {
		return writeToTemp;
	}

	/**
	 * @return the tools map
	 */
	public Map<String, IMergeTool> getTools() {
		return tools;
	}

	/**
	 * @return the tool names
	 */
	public Set<String> getToolNames() {
		return tools.keySet();
	}

}
