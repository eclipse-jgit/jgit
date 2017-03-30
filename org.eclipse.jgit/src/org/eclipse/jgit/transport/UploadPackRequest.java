/*
 * Copyright (C) 2017, Google Inc.
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

import java.io.EOFException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.UploadPack.FirstLine;

/** A git-fetch request seen from the server side. */
class UploadPackRequest {
	private final PacketLineIn pckIn;
	private final boolean biDirectionalPipe;
	private final UploadPackResponse response;

	/** Capabilities requested by the client. */
	final Set<String> options;

	/** Raw ObjectIds the client has asked for, before validating them. */
	final Set<ObjectId> wantIds;

	/** Shallow commits the client already has. */
	final Set<ObjectId> clientShallowCommits;

	/** Desired depth from the client on a shallow request. */
	final int depth;

	private UploadPackRequest(
			PacketLineIn pckIn, boolean biDirectionalPipe,
			UploadPackResponse response, Set<String> options,
			Set<ObjectId> wantIds, Set<ObjectId> clientShallowCommits,
			int depth) {
		this.pckIn = pckIn;
		this.biDirectionalPipe = biDirectionalPipe;
		this.response = response;
		this.options = options;
		this.wantIds = wantIds;
		this.clientShallowCommits = clientShallowCommits;
		this.depth = depth;
	}

	static UploadPackRequest parseWants(
			PacketLineIn pckIn, boolean biDirectionalPipe,
			UploadPackResponse response)
			throws IOException {
		Set<String> options = Collections.emptySet();
		Set<ObjectId> wantIds = new HashSet<>();
		Set<ObjectId> clientShallowCommits = new HashSet<>();
		int depth = 0;

		boolean isFirst = true;
		for (;;) {
			String line;
			try {
				line = pckIn.readString();
			} catch (EOFException eof) {
				if (isFirst)
					break;
				throw eof;
			}

			if (line == PacketLineIn.END)
				break;

			if (line.startsWith("deepen ")) { //$NON-NLS-1$
				depth = Integer.parseInt(line.substring(7));
				if (depth <= 0) {
					throw new PackProtocolException(MessageFormat.format(
							JGitText.get().invalidDepth,
							Integer.valueOf(depth)));
				}
				continue;
			}

			if (line.startsWith("shallow ")) { //$NON-NLS-1$
				clientShallowCommits.add(
						ObjectId.fromString(line.substring(8)));
				continue;
			}

			if (!line.startsWith("want ") || line.length() < 45) //$NON-NLS-1$
				throw new PackProtocolException(MessageFormat.format(
						JGitText.get().expectedGot, "want", line)); //$NON-NLS-1$

			if (isFirst) {
				if (line.length() > 45) {
					FirstLine firstLine = new FirstLine(line);
					options = firstLine.getOptions();
					line = firstLine.getLine();
				} else
					options = Collections.emptySet();
			}

			wantIds.add(ObjectId.fromString(line.substring(5)));
			isFirst = false;
		}

		return new UploadPackRequest(pckIn, biDirectionalPipe, response,
				options, wantIds, clientShallowCommits, depth);
	}

	enum NegotiateState {
		/** The negotiate request ends with a packet flush. */
		RECEIVE_END,
		/** The negotiate request ends with "done" */
		RECEIVE_DONE,
		/**
		 * There is no negotiate request. No pack file is requested.
		 * <p>
		 * If a client wants to know shallow/unshallow data in a non-bidi RPC
		 * (e.g. smart HTTP), it won't send any "have"s.
		 */
		NO_NEGOTIATION,
	}

	/**
	 * Parses which object the peer has.
	 *
	 * @param peerHas output the parsed {@link ObjectId}s.
	 * @return how the request ends
	 * @throws IOException
	 */
	NegotiateState parseNegotiateRequest(List<ObjectId> peerHas)
			throws IOException {
		for (;;) {
			String line;
			try {
				line = pckIn.readString();
			} catch (EOFException e) {
				if (!biDirectionalPipe && depth > 0)
					return NegotiateState.NO_NEGOTIATION;
				throw e;
			}

			if (line == PacketLineIn.END) {
				response.onParseNegotiateRequestDone();
				return NegotiateState.RECEIVE_END;

			} else if (line.startsWith("have ") && line.length() == 45) { //$NON-NLS-1$
				peerHas.add(ObjectId.fromString(line.substring(5)));

			} else if (line.equals("done")) { //$NON-NLS-1$
				response.onParseNegotiateRequestDone();
				return NegotiateState.RECEIVE_DONE;

			} else {
				throw new PackProtocolException(MessageFormat.format(
						JGitText.get().expectedGot, "have", line)); //$NON-NLS-1$
			}
		}
	}
}
