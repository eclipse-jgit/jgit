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

import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_FILTER;

import java.io.EOFException;
import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.transport.parser.FirstWant;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Parser for git protocol versions 0 and 1.
 *
 * It reads the lines coming through the {@link PacketLineIn} and builds a
 * {@link FetchV0Request} object.
 *
 * It requires a transferConfig object to know if the server supports filters.
 */
final class ProtocolV0Parser {

	private final TransferConfig transferConfig;

	ProtocolV0Parser(TransferConfig transferConfig) {
		this.transferConfig = transferConfig;
	}

	/**
	 * Parse an incoming protocol v1 upload request arguments from the wire.
	 *
	 * The incoming PacketLineIn is consumed until an END line, but the caller
	 * is responsible for closing it (if needed).
	 *
	 * @param pckIn
	 *            incoming lines. This method will read until an END line.
	 * @return a FetchV0Request with the data received in the wire.
	 * @throws PackProtocolException
	 * @throws IOException
	 */
	FetchV0Request recvWants(PacketLineIn pckIn)
			throws PackProtocolException, IOException {
		FetchV0Request.Builder reqBuilder = new FetchV0Request.Builder();

		boolean isFirst = true;
		boolean filterReceived = false;

		for (;;) {
			String line;
			try {
				line = pckIn.readString();
			} catch (EOFException eof) {
				if (isFirst) {
					break;
				}
				throw eof;
			}

			if (line == PacketLineIn.END) {
				break;
			}

			if (line.startsWith("deepen ")) { //$NON-NLS-1$
				int depth = Integer.parseInt(line.substring(7));
				if (depth <= 0) {
					throw new PackProtocolException(
							MessageFormat.format(JGitText.get().invalidDepth,
									Integer.valueOf(depth)));
				}
				reqBuilder.setDepth(depth);
				continue;
			}

			if (line.startsWith("shallow ")) { //$NON-NLS-1$
				reqBuilder.addClientShallowCommit(
						ObjectId.fromString(line.substring(8)));
				continue;
			}

			if (transferConfig.isAllowFilter()
					&& line.startsWith(OPTION_FILTER + " ")) { //$NON-NLS-1$
				String arg = line.substring(OPTION_FILTER.length() + 1);

				if (filterReceived) {
					throw new PackProtocolException(
							JGitText.get().tooManyFilters);
				}
				filterReceived = true;

				reqBuilder.setFilterBlobLimit(FilterSpec.parseFilterLine(arg));
				continue;
			}

			if (!line.startsWith("want ") || line.length() < 45) { //$NON-NLS-1$
				throw new PackProtocolException(MessageFormat
						.format(JGitText.get().expectedGot, "want", line)); //$NON-NLS-1$
			}

			if (isFirst) {
				if (line.length() > 45) {
					FirstWant firstLine = FirstWant.fromLine(line);
					reqBuilder.addClientCapabilities(firstLine.getCapabilities());
					reqBuilder.setAgent(firstLine.getAgent());
					line = firstLine.getLine();
				}
			}

			reqBuilder.addWantId(ObjectId.fromString(line.substring(5)));
			isFirst = false;
		}

		return reqBuilder.build();
	}

}
