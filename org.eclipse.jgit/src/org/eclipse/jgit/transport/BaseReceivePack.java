/*
 * Copyright (C) 2008-2010, Google Inc.
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
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand.Result;

/**
 * Obsolete base class for {@link ReceivePack}.
 *
 * @deprecated use {@link ReceivePack} instead
 */
public abstract class BaseReceivePack {
	/**
	 * Data in the first line of a request, the line itself plus capabilities.
	 *
	 * @deprecated use {@link ReceivePack.FirstLine} instead
	 */
	public static class FirstLine extends ReceivePack.FirstLine {
		/**
		 * Parse the first line of a receive-pack request.
		 *
		 * @param line
		 *            line from the client.
		 */
		public FirstLine(String line) {
			super(line);
		}
	}

	/**
	 * Get the push certificate used to verify the pusher's identity.
	 * <p>
	 * Only valid after commands are read from the wire.
	 *
	 * @return the parsed certificate, or null if push certificates are disabled
	 *         or no cert was presented by the client.
	 * @since 4.1
	 */
	public abstract PushCertificate getPushCertificate();

	/**
	 * Set the push certificate used to verify the pusher's identity.
	 *
	 * @param cert
	 *            the push certificate to set.
	 * @since 4.1
	 */
	public abstract void setPushCertificate(PushCertificate cert);

	/** @return the repository this receive completes into. */
	public abstract Repository getRepository();

	/** @return the RevWalk instance used by this connection. */
	public abstract RevWalk getRevWalk();

	/**
	 * Get refs which were advertised to the client.
	 *
	 * @return all refs which were advertised to the client, or null if
	 *         {@link #setAdvertisedRefs(Map, Set)} has not been called yet.
	 */
	public abstract Map<String, Ref> getAdvertisedRefs();

	/**
	 * Set the refs advertised by this ReceivePack.
	 * <p>
	 * Intended to be called from a {@link PreReceiveHook}.
	 *
	 * @param allRefs
	 *            explicit set of references to claim as advertised by this
	 *            ReceivePack instance. This overrides any references that
	 *            may exist in the source repository. The map is passed
	 *            to the configured {@link #getRefFilter()}. If null, assumes
	 *            all refs were advertised.
	 * @param additionalHaves
	 *            explicit set of additional haves to claim as advertised. If
	 *            null, assumes the default set of additional haves from the
	 *            repository.
	 */
	public abstract void setAdvertisedRefs(Map<String, Ref> allRefs,
			Set<ObjectId> additionalHaves);

	/**
	 * Get objects advertised to the client.
	 *
	 * @return the set of objects advertised to the as present in this repository,
	 *         or null if {@link #setAdvertisedRefs(Map, Set)} has not been called
	 *         yet.
	 */
	public abstract Set<ObjectId> getAdvertisedObjects();

	/**
	 * @return true if this instance will validate all referenced, but not
	 *         supplied by the client, objects are reachable from another
	 *         reference.
	 */
	public abstract boolean isCheckReferencedObjectsAreReachable();

	/**
	 * Validate all referenced but not supplied objects are reachable.
	 * <p>
	 * If enabled, this instance will verify that references to objects not
	 * contained within the received pack are already reachable through at least
	 * one other reference displayed as part of {@link #getAdvertisedRefs()}.
	 * <p>
	 * This feature is useful when the application doesn't trust the client to
	 * not provide a forged SHA-1 reference to an object, in an attempt to
	 * access parts of the DAG that they aren't allowed to see and which have
	 * been hidden from them via the configured {@link AdvertiseRefsHook} or
	 * {@link RefFilter}.
	 * <p>
	 * Enabling this feature may imply at least some, if not all, of the same
	 * functionality performed by {@link #setCheckReceivedObjects(boolean)}.
	 * Applications are encouraged to enable both features, if desired.
	 *
	 * @param b
	 *            {@code true} to enable the additional check.
	 */
	public abstract void setCheckReferencedObjectsAreReachable(boolean b);

	/**
	 * @return true if this class expects a bi-directional pipe opened between
	 *         the client and itself. The default is true.
	 */
	public abstract boolean isBiDirectionalPipe();

	/**
	 * @param twoWay
	 *            if true, this class will assume the socket is a fully
	 *            bidirectional pipe between the two peers and takes advantage
	 *            of that by first transmitting the known refs, then waiting to
	 *            read commands. If false, this class assumes it must read the
	 *            commands before writing output and does not perform the
	 *            initial advertising.
	 */
	public abstract void setBiDirectionalPipe(final boolean twoWay);

	/** @return true if there is data expected after the pack footer. */
	public abstract boolean isExpectDataAfterPackFooter();

	/**
	 * @param e
	 *            true if there is additional data in InputStream after pack.
	 */
	public abstract void setExpectDataAfterPackFooter(boolean e);

	/**
	 * @return true if this instance will verify received objects are formatted
	 *         correctly. Validating objects requires more CPU time on this side
	 *         of the connection.
	 */
	public abstract boolean isCheckReceivedObjects();

	/**
	 * @param check
	 *            true to enable checking received objects; false to assume all
	 *            received objects are valid.
	 * @see #setObjectChecker(ObjectChecker)
	 */
	public abstract void setCheckReceivedObjects(final boolean check);

	/**
	 * @param impl if non-null the object checking instance to verify each
	 *        received object with; null to disable object checking.
	 * @since 3.4
	 */
	public abstract void setObjectChecker(ObjectChecker impl);

	/** @return true if the client can request refs to be created. */
	public abstract boolean isAllowCreates();

	/**
	 * @param canCreate
	 *            true to permit create ref commands to be processed.
	 */
	public abstract void setAllowCreates(final boolean canCreate);

	/** @return true if the client can request refs to be deleted. */
	public abstract boolean isAllowDeletes();

	/**
	 * @param canDelete
	 *            true to permit delete ref commands to be processed.
	 */
	public abstract void setAllowDeletes(final boolean canDelete);

	/**
	 * @return true if the client can delete from {@code refs/heads/}.
	 * @since 3.6
	 */
	public abstract boolean isAllowBranchDeletes();

	/**
	 * @param canDelete
	 *            true to permit deletion of branches from the
	 *            {@code refs/heads/} namespace.
	 * @since 3.6
	 */
	public abstract void setAllowBranchDeletes(boolean canDelete);

	/**
	 * @return true if the client can request non-fast-forward updates of a ref,
	 *         possibly making objects unreachable.
	 */
	public abstract boolean isAllowNonFastForwards();

	/**
	 * @param canRewind
	 *            true to permit the client to ask for non-fast-forward updates
	 *            of an existing ref.
	 */
	public abstract void setAllowNonFastForwards(final boolean canRewind);

	/**
	 * @return true if the client's commands should be performed as a single
	 *         atomic transaction.
	 * @since 4.4
	 */
	public abstract boolean isAtomic();

	/**
	 * @param atomic
	 *            true to perform the client's commands as a single atomic
	 *            transaction.
	 * @since 4.4
	 */
	public abstract void setAtomic(boolean atomic);

	/** @return identity of the user making the changes in the reflog. */
	public abstract PersonIdent getRefLogIdent();

	/**
	 * Set the identity of the user appearing in the affected reflogs.
	 * <p>
	 * The timestamp portion of the identity is ignored. A new identity with the
	 * current timestamp will be created automatically when the updates occur
	 * and the log records are written.
	 *
	 * @param pi
	 *            identity of the user. If null the identity will be
	 *            automatically determined based on the repository
	 *            configuration.
	 */
	public abstract void setRefLogIdent(final PersonIdent pi);

	/** @return the hook used while advertising the refs to the client */
	public abstract AdvertiseRefsHook getAdvertiseRefsHook();

	/** @return the filter used while advertising the refs to the client */
	public abstract RefFilter getRefFilter();

	/**
	 * Set the hook used while advertising the refs to the client.
	 * <p>
	 * If the {@link AdvertiseRefsHook} chooses to call
	 * {@link #setAdvertisedRefs(Map,Set)}, only refs set by this hook
	 * <em>and</em> selected by the {@link RefFilter} will be shown to the
	 * client. Clients may still attempt to create or update a reference not
	 * advertised by the configured {@link AdvertiseRefsHook}. These attempts
	 * should be rejected by a matching {@link PreReceiveHook}.
	 *
	 * @param advertiseRefsHook
	 *            the hook; may be null to show all refs.
	 */
	public abstract void setAdvertiseRefsHook(final AdvertiseRefsHook advertiseRefsHook);

	/**
	 * Set the filter used while advertising the refs to the client.
	 * <p>
	 * Only refs allowed by this filter will be shown to the client. The filter
	 * is run against the refs specified by the {@link AdvertiseRefsHook} (if
	 * applicable).
	 *
	 * @param refFilter
	 *            the filter; may be null to show all refs.
	 */
	public abstract void setRefFilter(final RefFilter refFilter);

	/** @return timeout (in seconds) before aborting an IO operation. */
	public abstract int getTimeout();

	/**
	 * Set the timeout before willing to abort an IO call.
	 *
	 * @param seconds
	 *            number of seconds to wait (with no data transfer occurring)
	 *            before aborting an IO read or write operation with the
	 *            connected client.
	 */
	public abstract void setTimeout(final int seconds);

	/**
	 * Set the maximum allowed Git object size.
	 * <p>
	 * If an object is larger than the given size the pack-parsing will throw an
	 * exception aborting the receive-pack operation.
	 *
	 * @param limit
	 *            the Git object size limit. If zero then there is not limit.
	 */
	public abstract void setMaxObjectSizeLimit(final long limit);

	/**
	 * Set the maximum allowed pack size.
	 * <p>
	 * A pack exceeding this size will be rejected.
	 *
	 * @param limit
	 *            the pack size limit, in bytes
	 *
	 * @since 3.3
	 */
	public abstract void setMaxPackSizeLimit(final long limit);

	/**
	 * Check whether the client expects a side-band stream.
	 *
	 * @return true if the client has advertised a side-band capability, false
	 *         otherwise.
	 * @throws RequestNotYetReadException
	 *             if the client's request has not yet been read from the wire,
	 *             so we do not know if they expect side-band. Note that the
	 *             client may have already written the request, it just has not
	 *             been read.
	 */
	public abstract boolean isSideBand() throws RequestNotYetReadException;

	/**
	 * @return true if clients may request avoiding noisy progress messages.
	 * @since 4.0
	 */
	public abstract boolean isAllowQuiet();

	/**
	 * Configure if clients may request the server skip noisy messages.
	 *
	 * @param allow
	 *            true to allow clients to request quiet behavior; false to
	 *            refuse quiet behavior and send messages anyway. This may be
	 *            necessary if processing is slow and the client-server network
	 *            connection can timeout.
	 * @since 4.0
	 */
	public abstract void setAllowQuiet(boolean allow);

	/**
	 * @return true if the server supports the receiving of push options.
	 * @since 4.5
	 */
	public abstract boolean isAllowPushOptions();

	/**
	 * Configure if the server supports the receiving of push options.
	 *
	 * @param allow
	 *            true to permit option strings.
	 * @since 4.5
	 */
	public abstract void setAllowPushOptions(boolean allow);

	/**
	 * True if the client wants less verbose output.
	 *
	 * @return true if the client has requested the server to be less verbose.
	 * @throws RequestNotYetReadException
	 *             if the client's request has not yet been read from the wire,
	 *             so we do not know if they expect side-band. Note that the
	 *             client may have already written the request, it just has not
	 *             been read.
	 * @since 4.0
	 */
	public abstract boolean isQuiet() throws RequestNotYetReadException;

	/**
	 * Gets the list of string options associated with this push.
	 *
	 * @return pushOptions
	 * @throws RequestNotYetReadException
	 *             if the client's request has not yet been read from the wire,
	 *             so we do not know if they expect push options. Note that the
	 *             client may have already written the request, it just has not
	 *             been read.
	 * @since 4.5
	 */
	public abstract List<String> getPushOptions();

	/**
	 * Set the configuration for push certificate verification.
	 *
	 * @param cfg
	 *            new configuration; if this object is null or its
	 *            {@link SignedPushConfig#getCertNonceSeed()} is null, push
	 *            certificate verification will be disabled.
	 * @since 4.1
	 */
	public abstract void setSignedPushConfig(SignedPushConfig cfg);

	/**
	 * Get the user agent of the client.
	 * <p>
	 * If the client is new enough to use {@code agent=} capability that value
	 * will be returned. Older HTTP clients may also supply their version using
	 * the HTTP {@code User-Agent} header. The capability overrides the HTTP
	 * header if both are available.
	 * <p>
	 * When an HTTP request has been received this method returns the HTTP
	 * {@code User-Agent} header value until capabilities have been parsed.
	 *
	 * @return user agent supplied by the client. Available only if the client
	 *         is new enough to advertise its user agent.
	 * @since 4.0
	 */
	public abstract String getPeerUserAgent();

	/** @return all of the command received by the current request. */
	public abstract List<ReceiveCommand> getAllCommands();

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
	public abstract void sendError(final String what);

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
	public abstract void sendMessage(final String what);

	/** @return an underlying stream for sending messages to the client. */
	public abstract OutputStream getMessageOutputStream();

	/**
	 * Get the size of the received pack file including the index size.
	 *
	 * This can only be called if the pack is already received.
	 *
	 * @return the size of the received pack including index size
	 * @throws IllegalStateException
	 *             if called before the pack has been received
	 * @since 3.3
	 */
	public abstract long getPackSize();

	/**
	 * Generate an advertisement of available refs and capabilities.
	 *
	 * @param adv
	 *            the advertisement formatter.
	 * @throws IOException
	 *             the formatter failed to write an advertisement.
	 * @throws ServiceMayNotContinueException
	 *             the hook denied advertisement.
	 */
	public abstract void sendAdvertisedRefs(final RefAdvertiser adv)
			throws IOException, ServiceMayNotContinueException;

	/**
	 * Sets the client's intention regarding push options.
	 *
	 * @param usePushOptions
	 *            whether the client intends to use push options
	 * @since 4.5
	 */
	public abstract void setUsePushOptions(boolean usePushOptions);
}
