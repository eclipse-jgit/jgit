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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * In the pack negotiation phase (protocol v1), the client sends a list of
 * wants. The first "want" line is special, as it (can) have a list of
 * capabilities appended. E.g. "want <oid> cap1 cap2 cap3"
 *
 * Do not confuse this line with the first one in the reference advertisement,
 * which is sent by the server, produced in
 * {@link org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser}
 * and looks like "refs/heads/master\0cap1 cap2 cap3"
 *
 * This class parses the input want line and holds the results: the actual want
 * line and the capabilities.
 */
public class FirstWant {
	private final String line;

	private final Set<String> capabilities;

	/**
	 * Parse the first want line in the protocol v1 pack negotiation.
	 *
	 * @param line
	 *            line from the client.
	 * @return an instance of FirstWantLine
	 */
	public static FirstWant fromLine(String line) {
		String realLine;
		Set<String> options;

		if (line.length() > 45) {
			final HashSet<String> opts = new HashSet<>();
			String opt = line.substring(45);
			if (opt.startsWith(" ")) //$NON-NLS-1$
				opt = opt.substring(1);
			for (String c : opt.split(" ")) //$NON-NLS-1$
				opts.add(c);
			realLine = line.substring(0, 45);
			options = Collections.unmodifiableSet(opts);
		} else {
			realLine = line;
			options = Collections.emptySet();
		}

		return new FirstWant(realLine, options);
	}

	private FirstWant(String line, Set<String> capabilities) {
		this.line = line;
		this.capabilities = capabilities;
	}

	/**
	 * Parse the first line of a receive-pack request.
	 *
	 * @param line
	 *            line from the client.
	 *
	 * @deprecated Use factory method {@link #fromLine(String)} instead
	 */
	@Deprecated
	protected FirstWant(String line) {
		if (line.length() > 45) {
			final HashSet<String> opts = new HashSet<>();
			String opt = line.substring(45);
			if (opt.startsWith(" ")) //$NON-NLS-1$
				opt = opt.substring(1);
			for (String c : opt.split(" ")) //$NON-NLS-1$
				opts.add(c);
			this.line = line.substring(0, 45);
			this.capabilities = Collections.unmodifiableSet(opts);
		} else {
			this.line = line;
			this.capabilities = Collections.emptySet();
		}
	}

	/** @return non-capabilities part of the line. */
	public String getLine() {
		return line;
	}

	/** @return capabilities parsed from the line. */
	public Set<String> getCapabilities() {
		return capabilities;
	}
}
