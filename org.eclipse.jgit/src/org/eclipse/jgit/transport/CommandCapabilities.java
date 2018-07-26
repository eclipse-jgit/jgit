/*
 * Copyright (C) 2018, Google LLC.
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
package org.eclipse.jgit.transport;

import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_AGENT;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_SERVER_OPTION;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.annotations.Nullable;

/**
 * Container for parsed command capabilities (agent and server-option lines in
 * protocol v2)
 *
 * @since 5.1
 */
final public class CommandCapabilities {

	/** Arbitrary strings for the server */
	private List<String> serverOptions = new ArrayList<>();

	/** Who is sending the command */
	@Nullable
	private String agent;

	/**
	 * @return Content of the agent= line in the command capability (null if no
	 *         such line in the request)
	 */
	public String getAgent() {
		return agent;
	}

	/**
	 * @return content of server-option lines from the protocol request. Empty
	 *         list if not such lines in the message.
	 */
	public List<String> getServerOptions() {
		return serverOptions;
	}

	@Nullable
	private String safePayloadExtraction(String line, String key) {
		final String keyPrefix = key + "="; //$NON-NLS-1$
		if (line != null && line.length() > keyPrefix.length()
				&& line.startsWith(keyPrefix)) {
			return line.substring(keyPrefix.length());
		}

		return null;
	}

	/**
	 * @param line
	 *            Protocol line (key[=value]) from the capabilities list section
	 *            Unknown capabilities are ignored.
	 */
	public void addCmdCapability(String line) {
		String serverOption = safePayloadExtraction(line, OPTION_SERVER_OPTION);
		if (serverOption != null) {
			serverOptions.add(serverOption);
			return;
		}

		String agentPayload = safePayloadExtraction(line, OPTION_AGENT);
		if (agentPayload != null) {
			agent = agentPayload;
			return;
		}
	}
}
