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

import java.util.TreeMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Repository;

/**
 * Manages diff tools.
 *
 */
public class DiffToolManager {

	private final DiffToolConfig config;

	private Map<String, ITool> predefinedTools;

	private Map<String, ITool> userDefinedTools;

	/**
	 * @param db the repository database
	 */
	public DiffToolManager(Repository db) {
		config = db.getConfig().get(DiffToolConfig.KEY);
		setupPredefinedTools();
		setupUserDefinedTools();
	}

	/**
	 * @param newPath
	 *            the new file path
	 * @param oldPath
	 *            the old file path
	 * @param newId
	 *            the new object ID
	 * @param oldId
	 *            the old object ID
	 * @param toolName
	 *            the selected tool name (can be null)
	 * @param prompt
	 *            the prompt option
	 * @param gui
	 *            the GUI option
	 * @param trustExitCode
	 *            the "trust exit code" option
	 * @return the return code from executed tool
	 */
	public int compare(String newPath, String oldPath, String newId,
			String oldId, String toolName, BooleanOption prompt,
			BooleanOption gui, BooleanOption trustExitCode) {
		return 0;
	}

	/**
	 * @return the tool names
	 */
	public Set<String> getToolNames() {
		return config.getToolNames();
	}

	/**
	 * @return the user defined tools
	 */
	public Map<String, ITool> getUserDefinedTools() {
		return userDefinedTools;
	}

	/**
	 * @return the available predefined tools
	 */
	public Map<String, ITool> getAvailableTools() {
		// TODO: change to return only available tools instead of all
		return predefinedTools;
	}

	/**
	 * @return the NOT available predefined tools
	 */
	public Map<String, ITool> getNotAvailableTools() {
		// TODO: return not available tools
		return new TreeMap<>();
	}

	private void setupPredefinedTools() {
		predefinedTools = new TreeMap<>();
		for (PreDefinedDiffTools tool : PreDefinedDiffTools.values()) {
			predefinedTools
					.put(tool.name(),
							new PreDefinedDiffTool(tool.name(), tool.getPath(),
							tool.getParameters(), tool.isExitCodeTrustable()));
		}
	}

	private void setupUserDefinedTools() {
		userDefinedTools = new TreeMap<>();
		Map<String, ITool> userTools = config.getTools();
		for (String name : userTools.keySet()) {
			ITool userTool = userTools.get(name);
			// if difftool.<name>.cmd is defined we have user defined tool
			if (userTool.getCommand() != null) {
				userDefinedTools.put(name, userTool);
			} else if (userTool.getPath() != null) {
				// if difftool.<name>.path is defined we just overload the path
				// of predefined tool
				PreDefinedDiffTool predefTool = (PreDefinedDiffTool) predefinedTools
						.get(name);
				if (predefTool != null) {
					predefTool.setPath(userTool.getPath());
				}
			}
		}
	}

}
