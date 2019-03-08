/*
 * Copyright (C) 2018-2022, Andre Bossert <andre.bossert@siemens.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.diffmergetool;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.internal.BooleanTriState;

/**
 * Keeps track of difftool related configuration options.
 *
 * @since 5.13
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

	private final Map<String, ExternalMergeTool> tools;

	private MergeToolConfig(Config rc) {
		toolName = rc.getString(ConfigConstants.CONFIG_MERGE_SECTION, null,
				ConfigConstants.CONFIG_KEY_TOOL);
		guiToolName = rc.getString(ConfigConstants.CONFIG_MERGE_SECTION, null,
				ConfigConstants.CONFIG_KEY_GUITOOL);
		prompt = rc.getBoolean(ConfigConstants.CONFIG_MERGETOOL_SECTION,
				ConfigConstants.CONFIG_KEY_PROMPT, true);
		keepBackup = rc.getBoolean(ConfigConstants.CONFIG_MERGETOOL_SECTION,
				ConfigConstants.CONFIG_KEY_KEEP_BACKUP, true);
		keepTemporaries = rc.getBoolean(
				ConfigConstants.CONFIG_MERGETOOL_SECTION,
				ConfigConstants.CONFIG_KEY_KEEP_TEMPORARIES, false);
		writeToTemp = rc.getBoolean(ConfigConstants.CONFIG_MERGETOOL_SECTION,
				ConfigConstants.CONFIG_KEY_WRITE_TO_TEMP, false);
		tools = new HashMap<>();
		Set<String> subsections = rc
				.getSubsections(ConfigConstants.CONFIG_MERGETOOL_SECTION);
		for (String name : subsections) {
			String cmd = rc.getString(ConfigConstants.CONFIG_MERGETOOL_SECTION,
					name, ConfigConstants.CONFIG_KEY_CMD);
			String path = rc.getString(ConfigConstants.CONFIG_MERGETOOL_SECTION,
					name, ConfigConstants.CONFIG_KEY_PATH);
			BooleanTriState trustExitCode = BooleanTriState.FALSE;
			String trustStr = rc.getString(
					ConfigConstants.CONFIG_MERGETOOL_SECTION, name,
					ConfigConstants.CONFIG_KEY_TRUST_EXIT_CODE);
			if (trustStr != null) {
				trustExitCode = Boolean.valueOf(trustStr).booleanValue()
						? BooleanTriState.TRUE
						: BooleanTriState.FALSE;
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
	public Map<String, ExternalMergeTool> getTools() {
		return tools;
	}

	/**
	 * @return the tool names
	 */
	public Set<String> getToolNames() {
		return tools.keySet();
	}

}
