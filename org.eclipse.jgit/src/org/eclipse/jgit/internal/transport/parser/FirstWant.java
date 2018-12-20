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
package org.eclipse.jgit.internal.transport.parser;

import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_AGENT;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.internal.JGitText;

/**
 * In the pack negotiation phase (protocol v0/v1), the client sends a list of
 * wants. The first "want" line is special, as it (can) have a list of
 * capabilities appended.
 *
 * E.g. "want oid cap1 cap2 cap3"
 *
 * Do not confuse this line with the first one in the reference advertisement,
 * which is sent by the server, looks like
 * "b8f7c471373b8583ced0025cfad8c9916c484b76 HEAD\0 cap1 cap2 cap3" and is
 * parsed by the BasePackConnection.readAdvertisedRefs method.
 *
 * This class parses the input want line and holds the results: the actual want
 * line and the capabilities.
 *
 */
public class FirstWant {
	private final String line;

	private final Set<String> capabilities;

	@Nullable
	private final String agent;

	private static final String AGENT_PREFIX = OPTION_AGENT + '=';

	/**
	 * Parse the first want line in the protocol v0/v1 pack negotiation.
	 *
	 * @param line
	 *            line from the client.
	 * @return an instance of FirstWant
	 * @throws PackProtocolException
	 *             if the line doesn't follow the protocol format.
	 */
	public static FirstWant fromLine(String line) throws PackProtocolException {
		String wantLine;
		Set<String> capabilities;
		String agent = null;

		if (line.length() > 45) {
			String opt = line.substring(45);
			if (!opt.startsWith(" ")) { //$NON-NLS-1$
				throw new PackProtocolException(JGitText.get().wantNoSpaceWithCapabilities);
			}
			opt = opt.substring(1);

			HashSet<String> opts = new HashSet<>();
			for (String clientCapability : opt.split(" ")) { //$NON-NLS-1$
				if (clientCapability.startsWith(AGENT_PREFIX)) {
					agent = clientCapability.substring(AGENT_PREFIX.length());
				} else {
					opts.add(clientCapability);
				}
			}
			wantLine = line.substring(0, 45);
			capabilities = Collections.unmodifiableSet(opts);
		} else {
			wantLine = line;
			capabilities = Collections.emptySet();
		}

		return new FirstWant(wantLine, capabilities, agent);
	}

	private FirstWant(String line, Set<String> capabilities,
			@Nullable String agent) {
		this.line = line;
		this.capabilities = capabilities;
		this.agent = agent;
	}

	/** @return non-capabilities part of the line. */
	public String getLine() {
		return line;
	}

	/**
	 * @return capabilities parsed from the line as an immutable set (excluding
	 *         agent).
	 */
	public Set<String> getCapabilities() {
		return capabilities;
	}

	/** @return client user agent parsed from the line. */
	@Nullable
	public String getAgent() {
		return agent;
	}
}
