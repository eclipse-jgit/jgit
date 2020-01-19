/*
 * Copyright (C) 2018-2021, Andre Bossert <andre.bossert@siemens.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.diffmergetool;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_DIFFTOOL_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_DIFF_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_CMD;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_GUITOOL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PATH;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PROMPT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_TOOL;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_TRUST_EXIT_CODE;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.lib.internal.BooleanTriState;

/**
 * Keeps track of difftool related configuration options.
 */
public class DiffToolConfig {

	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<DiffToolConfig> KEY = DiffToolConfig::new;

	private final String toolName;

	private final String guiToolName;

	private final boolean prompt;

	private final BooleanTriState trustExitCode;

	private final Map<String, ExternalDiffTool> tools;

	private DiffToolConfig(Config rc) {
		toolName = rc.getString(CONFIG_DIFF_SECTION, null, CONFIG_KEY_TOOL);
		guiToolName = rc.getString(CONFIG_DIFF_SECTION, null,
				CONFIG_KEY_GUITOOL);
		prompt = rc.getBoolean(CONFIG_DIFFTOOL_SECTION, toolName,
				CONFIG_KEY_PROMPT,
				true);
		String trustStr = rc.getString(CONFIG_DIFFTOOL_SECTION, toolName,
				CONFIG_KEY_TRUST_EXIT_CODE);
		if (trustStr != null) {
			trustExitCode = Boolean.parseBoolean(trustStr)
					? BooleanTriState.TRUE
					: BooleanTriState.FALSE;
		} else {
			trustExitCode = BooleanTriState.UNSET;
		}
		tools = new HashMap<>();
		Set<String> subsections = rc.getSubsections(CONFIG_DIFFTOOL_SECTION);
		for (String name : subsections) {
			String cmd = rc.getString(CONFIG_DIFFTOOL_SECTION, name,
					CONFIG_KEY_CMD);
			String path = rc.getString(CONFIG_DIFFTOOL_SECTION, name,
					CONFIG_KEY_PATH);
			if ((cmd != null) || (path != null)) {
				tools.put(name, new UserDefinedDiffTool(name, path, cmd));
			}
		}
	}

	/**
	 * @return the default diff tool name (diff.tool)
	 */
	public String getDefaultToolName() {
		return toolName;
	}

	/**
	 * @return the default GUI diff tool name (diff.guitool)
	 */
	public String getDefaultGuiToolName() {
		return guiToolName;
	}

	/**
	 * @return the diff tool "prompt" option (difftool.prompt)
	 */
	public boolean isPrompt() {
		return prompt;
	}

	/**
	 * @return the diff tool "trust exit code" option (difftool.trustExitCode)
	 */
	public boolean isTrustExitCode() {
		return trustExitCode == BooleanTriState.TRUE;
	}

	/**
	 * @return the tools map
	 */
	public Map<String, ExternalDiffTool> getTools() {
		return tools;
	}

	/**
	 * @return the tool names
	 */
	public Set<String> getToolNames() {
		return tools.keySet();
	}
}
