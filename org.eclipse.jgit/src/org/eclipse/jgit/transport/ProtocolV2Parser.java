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

import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_DEEPEN_RELATIVE;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_FILTER;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_INCLUDE_TAG;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_NO_PROGRESS;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_OFS_DELTA;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_SIDE_BAND_64K;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_THIN_PACK;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_WANT_REF;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;

/**
 * Parse the incoming git protocol lines from the wire and translate them into a
 * Request object.
 *
 * It requires a transferConfig object to know what the server supports (e.g.
 * ref-in-want and/or filters).
 */
final class ProtocolV2Parser {

	private final TransferConfig transferConfig;

	ProtocolV2Parser(TransferConfig transferConfig) {
		this.transferConfig = transferConfig;
	}

	/**
	 * Parse the incoming fetch request arguments from the wire. The caller must
	 * be sure that what is comings is a fetch request before coming here.
	 *
	 * This operation requires the reference database to validate incoming
	 * references.
	 *
	 * @param pckIn
	 *            incoming lines
	 * @param refdb
	 *            reference database (to validate that received references exist
	 *            and point to valid objects)
	 * @return A FetchV2Request populated with information received from the
	 *         wire.
	 * @throws PackProtocolException
	 *             incompatible options, wrong type of arguments or other issues
	 *             where the request breaks the protocol.
	 * @throws IOException
	 *             an IO error prevented reading the incoming message or
	 *             accessing the ref database.
	 */
	FetchV2Request parseFetchRequest(PacketLineIn pckIn, RefDatabase refdb)
			throws PackProtocolException, IOException {
		FetchV2Request.Builder reqBuilder = FetchV2Request.builder();

		// Packs are always sent multiplexed and using full 64K
		// lengths.
		reqBuilder.addOption(OPTION_SIDE_BAND_64K);

		String line;

		// Currently, we do not support any capabilities, so the next
		// line is DELIM.
		if ((line = pckIn.readString()) != PacketLineIn.DELIM) {
			throw new PackProtocolException(MessageFormat
					.format(JGitText.get().unexpectedPacketLine, line));
		}

		boolean filterReceived = false;
		while ((line = pckIn.readString()) != PacketLineIn.END) {
			if (line.startsWith("want ")) { //$NON-NLS-1$
				reqBuilder.addWantsIds(ObjectId.fromString(line.substring(5)));
			} else if (transferConfig.isAllowRefInWant()
					&& line.startsWith(OPTION_WANT_REF + " ")) { //$NON-NLS-1$
				String refName = line.substring(OPTION_WANT_REF.length() + 1);
				// TODO(ifrade): This validation should be done after the
				// protocol parsing. It is not a protocol problem asking for an
				// unexisting ref and we wouldn't need the ref database here
				Ref ref = refdb.exactRef(refName);
				if (ref == null) {
					throw new PackProtocolException(MessageFormat
							.format(JGitText.get().invalidRefName, refName));
				}
				ObjectId oid = ref.getObjectId();
				if (oid == null) {
					throw new PackProtocolException(MessageFormat
							.format(JGitText.get().invalidRefName, refName));
				}
				reqBuilder.addWantedRef(refName, oid);
				reqBuilder.addWantsIds(oid);
			} else if (line.startsWith("have ")) { //$NON-NLS-1$
				reqBuilder.addPeerHas(ObjectId.fromString(line.substring(5)));
			} else if (line.equals("done")) { //$NON-NLS-1$
				reqBuilder.setDoneReceived();
			} else if (line.equals(OPTION_THIN_PACK)) {
				reqBuilder.addOption(OPTION_THIN_PACK);
			} else if (line.equals(OPTION_NO_PROGRESS)) {
				reqBuilder.addOption(OPTION_NO_PROGRESS);
			} else if (line.equals(OPTION_INCLUDE_TAG)) {
				reqBuilder.addOption(OPTION_INCLUDE_TAG);
			} else if (line.equals(OPTION_OFS_DELTA)) {
				reqBuilder.addOption(OPTION_OFS_DELTA);
			} else if (line.startsWith("shallow ")) { //$NON-NLS-1$
				reqBuilder.addClientShallowCommit(
						ObjectId.fromString(line.substring(8)));
			} else if (line.startsWith("deepen ")) { //$NON-NLS-1$
				int parsedDepth = Integer.parseInt(line.substring(7));
				if (parsedDepth <= 0) {
					throw new PackProtocolException(
							MessageFormat.format(JGitText.get().invalidDepth,
									Integer.valueOf(parsedDepth)));
				}
				if (reqBuilder.getDeepenSince() != 0) {
					throw new PackProtocolException(
							JGitText.get().deepenSinceWithDeepen);
				}
				if (reqBuilder.hasDeepenNotRefs()) {
					throw new PackProtocolException(
							JGitText.get().deepenNotWithDeepen);
				}
				reqBuilder.setDepth(parsedDepth);
			} else if (line.startsWith("deepen-not ")) { //$NON-NLS-1$
				reqBuilder.addDeepenNotRef(line.substring(11));
				if (reqBuilder.getDepth() != 0) {
					throw new PackProtocolException(
							JGitText.get().deepenNotWithDeepen);
				}
			} else if (line.equals(OPTION_DEEPEN_RELATIVE)) {
				reqBuilder.addOption(OPTION_DEEPEN_RELATIVE);
			} else if (line.startsWith("deepen-since ")) { //$NON-NLS-1$
				int ts = Integer.parseInt(line.substring(13));
				if (ts <= 0) {
					throw new PackProtocolException(MessageFormat
							.format(JGitText.get().invalidTimestamp, line));
				}
				if (reqBuilder.getDepth() != 0) {
					throw new PackProtocolException(
							JGitText.get().deepenSinceWithDeepen);
				}
				reqBuilder.setDeepenSince(ts);
			} else if (transferConfig.isAllowFilter()
					&& line.startsWith(OPTION_FILTER + ' ')) {
				if (filterReceived) {
					throw new PackProtocolException(
							JGitText.get().tooManyFilters);
				}
				filterReceived = true;
				reqBuilder.setFilterBlobLimit(filterLine(
						line.substring(OPTION_FILTER.length() + 1)));
			} else {
				throw new PackProtocolException(MessageFormat
						.format(JGitText.get().unexpectedPacketLine, line));
			}
		}

		return reqBuilder.build();
	}

	/**
	 * Process the content of "filter" line from the protocol. It has a shape
	 * like "blob:none" or "blob:limit=N", with limit a positive number.
	 *
	 * @param blobLine
	 *            the content of the "filter" line in the protocol
	 * @return N, the limit, defaulting to 0 if "none"
	 * @throws PackProtocolException
	 *             invalid filter because due to unrecognized format or
	 *             negative/non-numeric filter.
	 */
	static long filterLine(String blobLine) throws PackProtocolException {
		long blobLimit = -1;

		if (blobLine.equals("blob:none")) { //$NON-NLS-1$
			blobLimit = 0;
		} else if (blobLine.startsWith("blob:limit=")) { //$NON-NLS-1$
			try {
				blobLimit = Long
						.parseLong(blobLine.substring("blob:limit=".length())); //$NON-NLS-1$
			} catch (NumberFormatException e) {
				throw new PackProtocolException(MessageFormat
						.format(JGitText.get().invalidFilter, blobLine));
			}
		}
		/*
		 * We must have (1) either "blob:none" or "blob:limit=" set (because we
		 * only support blob size limits for now), and (2) if the latter, then
		 * it must be nonnegative. Throw if (1) or (2) is not met.
		 */
		if (blobLimit < 0) {
			throw new PackProtocolException(
					MessageFormat.format(JGitText.get().invalidFilter, blobLine));
		}

		return blobLimit;
	}

}
