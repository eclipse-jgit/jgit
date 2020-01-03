/*
 * Copyright (C) 2018, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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

			if (PacketLineIn.isEnd(line)) {
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

				reqBuilder.setFilterSpec(FilterSpec.fromFilterLine(arg));
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
