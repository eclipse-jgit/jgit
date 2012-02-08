/*
 * Copyright (C) 2012, Google Inc.
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand.Result;

/**
 * Interface for handling a session's worth of received data.
 * <p>
 * This comprises everything used by {@link ReceivePack} that does not involve
 * talking to the wire directly.
 */
public interface ReceiveSession {
	/** @return the repository this receive completes into. */
	public Repository getRepository();

	/** @return the RevWalk instance used by this connection. */
	public RevWalk getRevWalk();

	/** @return all refs which were advertised to the client. */
	public Map<String, Ref> getAdvertisedRefs();

	/** @return the set of objects advertised as present in this repository. */
	public Set<ObjectId> getAdvertisedObjects();

	/**
	 * Explicitly set advertised refs.
	 * <p>
	 * May be called by the configured {@link #getAdvertiseRefsHook()}.
	 *
	 * @param allRefs
	 *            explicit set of references to claim as advertised by this
	 *            ReceivePack instance. If null, all refs are advertised.
	 * @param additionalHaves
	 *            explicit set of additional haves to claim as advertised by this
	 *            ReceivePack instance. If null, the
	 *            {@link Repository#getAdditionalHaves()} are used.
	 */
	public void setAdvertisedRefs(Map<String, Ref> allRefs,
			Set<ObjectId> additionalHaves);

	/**
	 * @return true if this instance will validate all referenced, but not
	 *         supplied by the client, objects are reachable from another
	 *         reference.
	 */
	public boolean isCheckReferencedObjectsAreReachable();

	/**
	 * @return true if this class expects a bi-directional pipe opened between
	 *         the client and itself. The default is true.
	 */
	public boolean isBiDirectionalPipe();

	/**
	 * @return true if this instance will verify received objects are formatted
	 *         correctly. Validating objects requires more CPU time on this side
	 *         of the connection.
	 */
	public boolean isCheckReceivedObjects();

	/** @return true if the client can request refs to be created. */
	public boolean isAllowCreates();

	/** @return true if the client can request refs to be deleted. */
	public boolean isAllowDeletes();

	/**
	 * @return true if the client can request non-fast-forward updates of a ref,
	 *         possibly making objects unreachable.
	 */
	public boolean isAllowNonFastForwards();

	/** @return identity of the user making the changes in the reflog. */
	public PersonIdent getRefLogIdent();

	/** @return the hook used while advertising the refs to the client */
	public AdvertiseRefsHook getAdvertiseRefsHook();

	/** @return get the hook invoked before updates occur. */
	public PreReceiveHook getPreReceiveHook();

	/** @return get the hook invoked after updates occur. */
	public PostReceiveHook getPostReceiveHook();

	/** @return timeout (in seconds) before aborting an IO operation. */
	public int getTimeout();

	/** @return all of the command received by the current request. */
	public List<ReceiveCommand> getAllCommands();

	/**
	 * Send an error message to the client.
	 * <p>
	 * If any error messages are sent before the references are advertised to
	 * the client, the errors will be sent instead of the advertisement and the
	 * receive operation will be aborted. All clients should receive and display
	 * such early stage errors.
	 * <p>
	 * If the reference advertisements have already been sent, messages are sent
	 * in a side channel. If the client doesn't support receiving messages, the
	 * message will be discarded, with no other indication to the caller or to
	 * the client.
	 * <p>
	 * {@link PreReceiveHook}s should always try to use
	 * {@link ReceiveCommand#setResult(Result, String)} with a result status of
	 * {@link Result#REJECTED_OTHER_REASON} to indicate any reasons for
	 * rejecting an update. Messages attached to a command are much more likely
	 * to be returned to the client.
	 *
	 * @param what
	 *            string describing the problem identified by the hook. The
	 *            string must not end with an LF, and must not contain an LF.
	 */
	public void sendError(final String what);

	/**
	 * Send a message to the client, if it supports receiving them.
	 * <p>
	 * If the client doesn't support receiving messages, the message will be
	 * discarded, with no other indication to the caller or to the client.
	 *
	 * @param what
	 *            string describing the problem identified by the hook. The
	 *            string must not end with an LF, and must not contain an LF.
	 */
	public void sendMessage(final String what);

	/** Execute the pre-receive hook. */
	public void onPreReceive();

	/** Execute the post-receive hook. */
	public void onPostReceive();
}
