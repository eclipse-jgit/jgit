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

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.IO;

/** A git-fetch response seen from the server side. */
class UploadPackResponse {
	private final InputStream rawIn;
	private final PacketLineOut pckOut;
	private final boolean biDirectionalPipe;

	// If it's non-bidirectional pipe, we need to parse the request before
	// start writing a response. This buffers the response until the negotiate
	// request has been read.
	private final List<String> shallowResponseBuffer = new ArrayList<>();
	private boolean receivedNegotiateRequest;
	private boolean shouldFlushShallowResponse;

	UploadPackResponse(InputStream rawIn, PacketLineOut pckOut,
			boolean biDirectionalPipe) {
		this.rawIn = rawIn;
		this.pckOut = pckOut;
		this.biDirectionalPipe = biDirectionalPipe;
	}

	void writeShallow(RevCommit o) throws IOException {
		String line = "shallow " + o.name(); //$NON-NLS-1$
		if (biDirectionalPipe) {
			pckOut.writeString(line);
		} else {
			shallowResponseBuffer.add(line);
		}
	}

	void writeUnshallow(RevCommit o) throws IOException {
		String line = "unshallow " + o.name(); //$NON-NLS-1$
		if (biDirectionalPipe) {
			pckOut.writeString(line);
		} else {
			shallowResponseBuffer.add(line);
		}
	}

	void endShallowResponse() throws IOException {
		if (biDirectionalPipe) {
			pckOut.end();
		} else {
			shouldFlushShallowResponse = true;
		}
	}

	void onParseNegotiateRequestDone() throws IOException {
		if (receivedNegotiateRequest) {
			return;
		}
		receivedNegotiateRequest = true;
		if (biDirectionalPipe) {
			return;
		}

		// Ensure the request was fully consumed. Any remaining input must
		// be a protocol error. If we aren't at EOF the implementation is
		// broken.
		int eof = rawIn.read();
		if (0 <= eof)
			throw new CorruptObjectException(MessageFormat.format(
					JGitText.get().expectedEOFReceived,
					"\\x" + Integer.toHexString(eof))); //$NON-NLS-1$

		if (shouldFlushShallowResponse) {
			for (String line : shallowResponseBuffer) {
				pckOut.writeString(line);
			}
			pckOut.end();
			shouldFlushShallowResponse = false;
			shallowResponseBuffer.clear();
		}
	}

	void writeNak() throws IOException {
		pckOut.writeString("NAK\n"); //$NON-NLS-1$
	}

	void writeAck(ObjectId o) throws IOException {
		pckOut.writeString("ACK " + o.name() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	void writeAckContinue(ObjectId o) throws IOException {
		pckOut.writeString("ACK " + o.name() + " continue\n"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	void writeAckCommon(ObjectId o) throws IOException {
		pckOut.writeString("ACK " + o.name() + " common\n"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	void writeAckReady(ObjectId o) throws IOException {
		pckOut.writeString("ACK " + o.name() + " ready\n"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	void flushNegotiateResponse() throws IOException {
		pckOut.flush();
	}

	void writeError(String message) throws IOException {
		if (!biDirectionalPipe) {
			// Drain the request before start writing a response.
			IO.skipFully(rawIn);
		}
		pckOut.writeString("ERR " + message + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
