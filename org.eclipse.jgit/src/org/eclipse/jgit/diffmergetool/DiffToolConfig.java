/*
 * Copyright (C) 2018-2019, Andre Bossert <andre.bossert@siemens.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
 *
 * @since 5.7
 */
public class DiffToolConfig {

	/** Key for {@link Config#get(SectionParser)}. */
	public static final Config.SectionParser<DiffToolConfig> KEY = DiffToolConfig::new;

	private final String toolName;

	private final String guiToolName;

	private final boolean prompt;

	private final BooleanOption trustExitCode;

	private final Map<String, IDiffTool> tools;

	private DiffToolConfig(Config rc) {
		// get default diff tool name (diff.tool)
		toolName = rc.getString(ConfigConstants.CONFIG_DIFF_SECTION, null,
				ConfigConstants.CONFIG_KEY_TOOL);
		// get default gui diff tool name (diff.guitool)
		guiToolName = rc.getString(ConfigConstants.CONFIG_DIFF_SECTION, null,
				ConfigConstants.CONFIG_KEY_GUITOOL);
		// get prompt option (difftool.prompt, default = true)
		prompt = rc.getBoolean(ConfigConstants.CONFIG_DIFFTOOL_SECTION,
				ConfigConstants.CONFIG_KEY_PROMPT, true);
		// get trustExitCode option (difftool.trustExitCode, default = false)
		String trustStr = rc.getString(ConfigConstants.CONFIG_DIFFTOOL_SECTION,
				null, ConfigConstants.CONFIG_KEY_TRUST_EXIT_CODE);
		if (trustStr != null) {
			trustExitCode = BooleanOption
					.defined(Boolean.parseBoolean(trustStr));
		} else {
			trustExitCode = BooleanOption.notDefined(false);
		}
		// get all diff tools
		tools = new HashMap<>();
		Set<String> subsections = rc
				.getSubsections(ConfigConstants.CONFIG_DIFFTOOL_SECTION);
		for (String name : subsections) {
			// get the difftool command (difftool.<name>.cmd)
			String cmd = rc.getString(ConfigConstants.CONFIG_DIFFTOOL_SECTION,
					name, ConfigConstants.CONFIG_KEY_CMD);
			// get the difftool path (difftool.<name>.path)
			String path = rc.getString(ConfigConstants.CONFIG_DIFFTOOL_SECTION,
					name, ConfigConstants.CONFIG_KEY_PATH);
			if ((cmd != null) || (path != null)) {
				tools.put(name,
						new UserDefinedDiffTool(name, path, cmd));
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
		return trustExitCode.toBoolean();
	}

	/**
	 * @return the tools map
	 */
	public Map<String, IDiffTool> getTools() {
		return tools;
	}

	/**
	 * @return the tool names
	 */
	public Set<String> getToolNames() {
		return tools.keySet();
	}
}
