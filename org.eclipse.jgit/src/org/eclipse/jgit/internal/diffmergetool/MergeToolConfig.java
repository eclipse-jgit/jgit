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

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_CMD;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_GUITOOL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_KEEP_BACKUP;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_KEEP_TEMPORARIES;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PATH;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PROMPT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_TOOL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_TRUST_EXIT_CODE;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_WRITE_TO_TEMP;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_MERGETOOL_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_MERGE_SECTION;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.lib.internal.BooleanTriState;

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

	private final Map<String, ExternalMergeTool> tools;

	private MergeToolConfig(Config rc) {
		toolName = rc.getString(CONFIG_MERGE_SECTION, null, CONFIG_KEY_TOOL);
		guiToolName = rc.getString(CONFIG_MERGE_SECTION, null,
				CONFIG_KEY_GUITOOL);
		prompt = rc.getBoolean(CONFIG_MERGETOOL_SECTION, toolName,
				CONFIG_KEY_PROMPT, true);
		keepBackup = rc.getBoolean(CONFIG_MERGETOOL_SECTION,
				CONFIG_KEY_KEEP_BACKUP, true);
		keepTemporaries = rc.getBoolean(CONFIG_MERGETOOL_SECTION,
				CONFIG_KEY_KEEP_TEMPORARIES, false);
		writeToTemp = rc.getBoolean(CONFIG_MERGETOOL_SECTION,
				CONFIG_KEY_WRITE_TO_TEMP, false);
		tools = new HashMap<>();
		Set<String> subsections = rc.getSubsections(CONFIG_MERGETOOL_SECTION);
		for (String name : subsections) {
			String cmd = rc.getString(CONFIG_MERGETOOL_SECTION, name,
					CONFIG_KEY_CMD);
			String path = rc.getString(CONFIG_MERGETOOL_SECTION, name,
					CONFIG_KEY_PATH);
			BooleanTriState trustExitCode = BooleanTriState.FALSE;
			String trustStr = rc.getString(CONFIG_MERGETOOL_SECTION, name,
					CONFIG_KEY_TRUST_EXIT_CODE);
			if (trustStr != null) {
				trustExitCode = Boolean.valueOf(trustStr).booleanValue()
						? BooleanTriState.TRUE
						: BooleanTriState.FALSE;
			} else {
				trustExitCode = BooleanTriState.UNSET;
			}
			if ((cmd != null) || (path != null)) {
				tools.put(name, new UserDefinedMergeTool(name, path, cmd,
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
