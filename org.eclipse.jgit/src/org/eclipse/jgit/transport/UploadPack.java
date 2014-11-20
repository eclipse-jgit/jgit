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

import static org.eclipse.jgit.lib.RefDatabase.ALL;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_ALLOW_TIP_SHA1_IN_WANT;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_INCLUDE_TAG;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_MULTI_ACK;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_MULTI_ACK_DETAILED;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_NO_DONE;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_NO_PROGRESS;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_OFS_DELTA;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_SHALLOW;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_SIDE_BAND;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_SIDE_BAND_64K;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_THIN_PACK;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.AsyncRevObjectQueue;
import org.eclipse.jgit.revwalk.DepthWalk;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevFlagSet;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.GitProtocolConstants.MultiAck;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.util.io.InterruptTimer;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.eclipse.jgit.util.io.TimeoutInputStream;
import org.eclipse.jgit.util.io.TimeoutOutputStream;

/**
 * Implements the server side of a fetch connection, transmitting objects.
 */
public class UploadPack {
	/** Policy the server uses to validate client requests */
	public static enum RequestPolicy {
		/** Client may only ask for objects the server advertised a reference for. */
		ADVERTISED,

		/**
		 * Client may ask for any commit reachable from a reference advertised by
		 * the server.
		 */
		REACHABLE_COMMIT,

		/**
		 * Client may ask for objects that are the tip of any reference, even if not
		 * advertised.
		 * <p>
		 * This may happen, for example, when a custom {@link RefFilter} is set.
		 *
		 * @since 3.1
		 */
		TIP,

		/**
		 * Client may ask for any commit reachable from any reference, even if that
		 * reference wasn't advertised.
		 *
		 * @since 3.1
		 */
		REACHABLE_COMMIT_TIP,

		/** Client may ask for any SHA-1 in the repository. */
		ANY;
	}

	/**
	 * Validator for client requests.
	 *
	 * @since 3.1
	 */
	public interface RequestValidator {
		/**
		 * Check a list of client wants against the request policy.
		 *
		 * @param up
		 *            {@link UploadPack} instance.
		 * @param wants
		 *            objects the client requested that were not advertised.
		 *
		 * @throws PackProtocolException
		 *            if one or more wants is not valid.
		 * @throws IOException
		 *            if a low-level exception occurred.
		 * @since 3.1
		 */
		void checkWants(UploadPack up, List<ObjectId> wants)
				throws PackProtocolException, IOException;
	}

	/** Data in the first line of a request, the line itself plus options. */
	public static class FirstLine {
		private final String line;
		private final Set<String> options;

		/**
		 * Parse the first line of a receive-pack request.
		 *
		 * @param line
		 *            line from the client.
		 */
		public FirstLine(String line) {
			if (line.length() > 45) {
				final HashSet<String> opts = new HashSet<String>();
				String opt = line.substring(45);
				if (opt.startsWith(" ")) //$NON-NLS-1$
					opt = opt.substring(1);
				for (String c : opt.split(" ")) //$NON-NLS-1$
					opts.add(c);
				this.line = line.substring(0, 45);
				this.options = Collections.unmodifiableSet(opts);
			} else {
				this.line = line;
				this.options = Collections.emptySet();
			}
		}

		/** @return non-capabilities part of the line. */
		public String getLine() {
			return line;
		}

		/** @return options parsed from the line. */
		public Set<String> getOptions() {
			return options;
		}
	}

	/** Database we read the objects from. */
	private final Repository db;

	/** Revision traversal support over {@link #db}. */
	private final RevWalk walk;

	/** Configuration to pass into the PackWriter. */
	private PackConfig packConfig;

	/** Configuration for various transfer options. */
	private TransferConfig transferConfig;

	/** Timeout in seconds to wait for client interaction. */
	private int timeout;

	/**
	 * Is the client connection a bi-directional socket or pipe?
	 * <p>
	 * If true, this class assumes it can perform multiple read and write cycles
	 * with the client over the input and output streams. This matches the
	 * functionality available with a standard TCP/IP connection, or a local
	 * operating system or in-memory pipe.
	 * <p>
	 * If false, this class runs in a read everything then output results mode,
	 * making it suitable for single round-trip systems RPCs such as HTTP.
	 */
	private boolean biDirectionalPipe = true;

	/** Timer to manage {@link #timeout}. */
	private InterruptTimer timer;

	private InputStream rawIn;

	private OutputStream rawOut;

	private PacketLineIn pckIn;

	private PacketLineOut pckOut;

	private OutputStream msgOut = NullOutputStream.INSTANCE;

	/** The refs we advertised as existing at the start of the connection. */
	private Map<String, Ref> refs;

	/** Hook used while advertising the refs to the client. */
	private AdvertiseRefsHook advertiseRefsHook = AdvertiseRefsHook.DEFAULT;

	/** Filter used while advertising the refs to the client. */
	private RefFilter refFilter = RefFilter.DEFAULT;

	/** Hook handling the various upload phases. */
	private PreUploadHook preUploadHook = PreUploadHook.NULL;

	/** Capabilities requested by the client. */
	private Set<String> options;

	/** Raw ObjectIds the client has asked for, before validating them. */
	private final Set<ObjectId> wantIds = new HashSet<ObjectId>();

	/** Objects the client wants to obtain. */
	private final Set<RevObject> wantAll = new HashSet<RevObject>();

	/** Objects on both sides, these don't have to be sent. */
	private final Set<RevObject> commonBase = new HashSet<RevObject>();

	/** Shallow commits the client already has. */
	private final Set<ObjectId> clientShallowCommits = new HashSet<ObjectId>();

	/** Shallow commits on the client which are now becoming unshallow */
	private final List<ObjectId> unshallowCommits = new ArrayList<ObjectId>();

	/** Desired depth from the client on a shallow request. */
	private int depth;

	/** Commit time of the oldest common commit, in seconds. */
	private int oldestTime;

	/** null if {@link #commonBase} should be examined again. */
	private Boolean okToGiveUp;

	private boolean sentReady;

	/** Objects we sent in our advertisement list, clients can ask for these. */
	private Set<ObjectId> advertised;

	/** Marked on objects the client has asked us to give them. */
	private final RevFlag WANT;

	/** Marked on objects both we and the client have. */
	private final RevFlag PEER_HAS;

	/** Marked on objects in {@link #commonBase}. */
	private final RevFlag COMMON;

	/** Objects where we found a path from the want list to a common base. */
	private final RevFlag SATISFIED;

	private final RevFlagSet SAVE;

	private RequestValidator requestValidator = new AdvertisedRequestValidator();

	private MultiAck multiAck = MultiAck.OFF;

	private boolean noDone;

	private PackWriter.Statistics statistics;

	private UploadPackLogger logger = UploadPackLogger.NULL;

	/**
	 * Create a new pack upload for an open repository.
	 *
	 * @param copyFrom
	 *            the source repository.
	 */
	public UploadPack(final Repository copyFrom) {
		db = copyFrom;
		walk = new RevWalk(db);
		walk.setRetainBody(false);

		WANT = walk.newFlag("WANT"); //$NON-NLS-1$
		PEER_HAS = walk.newFlag("PEER_HAS"); //$NON-NLS-1$
		COMMON = walk.newFlag("COMMON"); //$NON-NLS-1$
		SATISFIED = walk.newFlag("SATISFIED"); //$NON-NLS-1$
		walk.carry(PEER_HAS);

		SAVE = new RevFlagSet();
		SAVE.add(WANT);
		SAVE.add(PEER_HAS);
		SAVE.add(COMMON);
		SAVE.add(SATISFIED);

		setTransferConfig(null);
	}

	/** @return the repository this upload is reading from. */
	public final Repository getRepository() {
		return db;
	}

	/** @return the RevWalk instance used by this connection. */
	public final RevWalk getRevWalk() {
		return walk;
	}

	/**
	 * Get refs which were advertised to the client.
	 *
	 * @return all refs which were advertised to the client, or null if
	 *         {@link #setAdvertisedRefs(Map)} has not been called yet.
	 */
	public final Map<String, Ref> getAdvertisedRefs() {
		return refs;
	}

	/**
	 * Set the refs advertised by this UploadPack.
	 * <p>
	 * Intended to be called from a {@link PreUploadHook}.
	 *
	 * @param allRefs
	 *            explicit set of references to claim as advertised by this
	 *            UploadPack instance. This overrides any references that
	 *            may exist in the source repository. The map is passed
	 *            to the configured {@link #getRefFilter()}. If null, assumes
	 *            all refs were advertised.
	 */
	public void setAdvertisedRefs(Map<String, Ref> allRefs) {
		if (allRefs != null)
			refs = allRefs;
		else
			refs = db.getAllRefs();
		if (refFilter == RefFilter.DEFAULT)
			refs = transferConfig.getRefFilter().filter(refs);
		else
			refs = refFilter.filter(refs);
	}

	/** @return timeout (in seconds) before aborting an IO operation. */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * Set the timeout before willing to abort an IO call.
	 *
	 * @param seconds
	 *            number of seconds to wait (with no data transfer occurring)
	 *            before aborting an IO read or write operation with the
	 *            connected client.
	 */
	public void setTimeout(final int seconds) {
		timeout = seconds;
	}

	/**
	 * @return true if this class expects a bi-directional pipe opened between
	 *         the client and itself. The default is true.
	 */
	public boolean isBiDirectionalPipe() {
		return biDirectionalPipe;
	}

	/**
	 * @param twoWay
	 *            if true, this class will assume the socket is a fully
	 *            bidirectional pipe between the two peers and takes advantage
	 *            of that by first transmitting the known refs, then waiting to
	 *            read commands. If false, this class assumes it must read the
	 *            commands before writing output and does not perform the
	 *            initial advertising.
	 */
	public void setBiDirectionalPipe(final boolean twoWay) {
		biDirectionalPipe = twoWay;
	}

	/**
	 * @return policy used by the service to validate client requests, or null for
	 *         a custom request validator.
	 */
	public RequestPolicy getRequestPolicy() {
		if (requestValidator instanceof AdvertisedRequestValidator)
			return RequestPolicy.ADVERTISED;
		if (requestValidator instanceof ReachableCommitRequestValidator)
			return RequestPolicy.REACHABLE_COMMIT;
		if (requestValidator instanceof TipRequestValidator)
			return RequestPolicy.TIP;
		if (requestValidator instanceof ReachableCommitTipRequestValidator)
			return RequestPolicy.REACHABLE_COMMIT_TIP;
		if (requestValidator instanceof AnyRequestValidator)
			return RequestPolicy.ANY;
		return null;
	}

	/**
	 * @param policy
	 *            the policy used to enforce validation of a client's want list.
	 *            By default the policy is {@link RequestPolicy#ADVERTISED},
	 *            which is the Git default requiring clients to only ask for an
	 *            object that a reference directly points to. This may be relaxed
	 *            to {@link RequestPolicy#REACHABLE_COMMIT} or
	 *            {@link RequestPolicy#REACHABLE_COMMIT_TIP} when callers have
	 *            {@link #setBiDirectionalPipe(boolean)} set to false.
	 *            Overrides any policy specified in a {@link TransferConfig}.
	 */
	public void setRequestPolicy(RequestPolicy policy) {
		switch (policy) {
			case ADVERTISED:
			default:
				requestValidator = new AdvertisedRequestValidator();
				break;
			case REACHABLE_COMMIT:
				requestValidator = new ReachableCommitRequestValidator();
				break;
			case TIP:
				requestValidator = new TipRequestValidator();
				break;
			case REACHABLE_COMMIT_TIP:
				requestValidator = new ReachableCommitTipRequestValidator();
				break;
			case ANY:
				requestValidator = new AnyRequestValidator();
				break;
		}
	}

	/**
	 * @param validator
	 *            custom validator for client want list.
	 * @since 3.1
	 */
	public void setRequestValidator(RequestValidator validator) {
		requestValidator = validator != null ? validator
				: new AdvertisedRequestValidator();
	}

	/** @return the hook used while advertising the refs to the client */
	public AdvertiseRefsHook getAdvertiseRefsHook() {
		return advertiseRefsHook;
	}

	/** @return the filter used while advertising the refs to the client */
	public RefFilter getRefFilter() {
		return refFilter;
	}

	/**
	 * Set the hook used while advertising the refs to the client.
	 * <p>
	 * If the {@link AdvertiseRefsHook} chooses to call
	 * {@link #setAdvertisedRefs(Map)}, only refs set by this hook <em>and</em>
	 * selected by the {@link RefFilter} will be shown to the client.
	 *
	 * @param advertiseRefsHook
	 *            the hook; may be null to show all refs.
	 */
	public void setAdvertiseRefsHook(final AdvertiseRefsHook advertiseRefsHook) {
		if (advertiseRefsHook != null)
			this.advertiseRefsHook = advertiseRefsHook;
		else
			this.advertiseRefsHook = AdvertiseRefsHook.DEFAULT;
	}

	/**
	 * Set the filter used while advertising the refs to the client.
	 * <p>
	 * Only refs allowed by this filter will be sent to the client.
	 * The filter is run against the refs specified by the
	 * {@link AdvertiseRefsHook} (if applicable). If null or not set, uses the
	 * filter implied by the {@link TransferConfig}.
	 *
	 * @param refFilter
	 *            the filter; may be null to show all refs.
	 */
	public void setRefFilter(final RefFilter refFilter) {
		this.refFilter = refFilter != null ? refFilter : RefFilter.DEFAULT;
	}

	/** @return the configured upload hook. */
	public PreUploadHook getPreUploadHook() {
		return preUploadHook;
	}

	/**
	 * Set the hook that controls how this instance will behave.
	 *
	 * @param hook
	 *            the hook; if null no special actions are taken.
	 */
	public void setPreUploadHook(PreUploadHook hook) {
		preUploadHook = hook != null ? hook : PreUploadHook.NULL;
	}

	/**
	 * Set the configuration used by the pack generator.
	 *
	 * @param pc
	 *            configuration controlling packing parameters. If null the
	 *            source repository's settings will be used.
	 */
	public void setPackConfig(PackConfig pc) {
		this.packConfig = pc;
	}

	/**
	 * @param tc
	 *            configuration controlling transfer options. If null the source
	 *            repository's settings will be used.
	 * @since 3.1
	 */
	public void setTransferConfig(TransferConfig tc) {
		this.transferConfig = tc != null ? tc : new TransferConfig(db);
		setRequestPolicy(transferConfig.isAllowTipSha1InWant()
				? RequestPolicy.TIP : RequestPolicy.ADVERTISED);
	}

	/** @return the configured logger. */
	public UploadPackLogger getLogger() {
		return logger;
	}

	/**
	 * Set the logger.
	 *
	 * @param logger
	 *            the logger instance. If null, no logging occurs.
	 */
	public void setLogger(UploadPackLogger logger) {
		this.logger = logger;
	}

	/**
	 * Check whether the client expects a side-band stream.
	 *
	 * @return true if the client has advertised a side-band capability, false
	 *     otherwise.
	 * @throws RequestNotYetReadException
	 *             if the client's request has not yet been read from the wire, so
	 *             we do not know if they expect side-band. Note that the client
	 *             may have already written the request, it just has not been
	 *             read.
	 */
	public boolean isSideBand() throws RequestNotYetReadException {
		if (options == null)
			throw new RequestNotYetReadException();
		return (options.contains(OPTION_SIDE_BAND)
				|| options.contains(OPTION_SIDE_BAND_64K));
	}

	/**
	 * Execute the upload task on the socket.
	 *
	 * @param input
	 *            raw input to read client commands from. Caller must ensure the
	 *            input is buffered, otherwise read performance may suffer.
	 * @param output
	 *            response back to the Git network client, to write the pack
	 *            data onto. Caller must ensure the output is buffered,
	 *            otherwise write performance may suffer.
	 * @param messages
	 *            secondary "notice" channel to send additional messages out
	 *            through. When run over SSH this should be tied back to the
	 *            standard error channel of the command execution. For most
	 *            other network connections this should be null.
	 * @throws IOException
	 */
	public void upload(final InputStream input, final OutputStream output,
			final OutputStream messages) throws IOException {
		try {
			rawIn = input;
			rawOut = output;
			if (messages != null)
				msgOut = messages;

			if (timeout > 0) {
				final Thread caller = Thread.currentThread();
				timer = new InterruptTimer(caller.getName() + "-Timer"); //$NON-NLS-1$
				TimeoutInputStream i = new TimeoutInputStream(rawIn, timer);
				TimeoutOutputStream o = new TimeoutOutputStream(rawOut, timer);
				i.setTimeout(timeout * 1000);
				o.setTimeout(timeout * 1000);
				rawIn = i;
				rawOut = o;
			}

			pckIn = new PacketLineIn(rawIn);
			pckOut = new PacketLineOut(rawOut);
			service();
		} finally {
			msgOut = NullOutputStream.INSTANCE;
			walk.release();
			if (timer != null) {
				try {
					timer.terminate();
				} finally {
					timer = null;
				}
			}
		}
	}

	/**
	 * Get the PackWriter's statistics if a pack was sent to the client.
	 *
	 * @return statistics about pack output, if a pack was sent. Null if no pack
	 *         was sent, such as during the negotation phase of a smart HTTP
	 *         connection, or if the client was already up-to-date.
	 * @since 3.0
	 */
	public PackWriter.Statistics getPackStatistics() {
		return statistics;
	}

	private Map<String, Ref> getAdvertisedOrDefaultRefs() {
		if (refs == null)
			setAdvertisedRefs(null);
		return refs;
	}

	private void service() throws IOException {
		if (biDirectionalPipe)
			sendAdvertisedRefs(new PacketLineOutRefAdvertiser(pckOut));
		else if (requestValidator instanceof AnyRequestValidator)
			advertised = Collections.emptySet();
		else
			advertised = refIdSet(getAdvertisedOrDefaultRefs().values());

		boolean sendPack;
		try {
			recvWants();
			if (wantIds.isEmpty()) {
				preUploadHook.onBeginNegotiateRound(this, wantIds, 0);
				preUploadHook.onEndNegotiateRound(this, wantIds, 0, 0, false);
				return;
			}

			if (options.contains(OPTION_MULTI_ACK_DETAILED)) {
				multiAck = MultiAck.DETAILED;
				noDone = options.contains(OPTION_NO_DONE);
			} else if (options.contains(OPTION_MULTI_ACK))
				multiAck = MultiAck.CONTINUE;
			else
				multiAck = MultiAck.OFF;

			if (depth != 0)
				processShallow();
			if (!clientShallowCommits.isEmpty())
				walk.assumeShallow(clientShallowCommits);
			sendPack = negotiate();
		} catch (PackProtocolException err) {
			reportErrorDuringNegotiate(err.getMessage());
			throw err;

		} catch (ServiceMayNotContinueException err) {
			if (!err.isOutput() && err.getMessage() != null) {
				try {
					pckOut.writeString("ERR " + err.getMessage() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
					err.setOutput();
				} catch (Throwable err2) {
					// Ignore this secondary failure (and not mark output).
				}
			}
			throw err;

		} catch (IOException err) {
			reportErrorDuringNegotiate(JGitText.get().internalServerError);
			throw err;
		} catch (RuntimeException err) {
			reportErrorDuringNegotiate(JGitText.get().internalServerError);
			throw err;
		} catch (Error err) {
			reportErrorDuringNegotiate(JGitText.get().internalServerError);
			throw err;
		}

		if (sendPack)
			sendPack();
	}

	private static Set<ObjectId> refIdSet(Collection<Ref> refs) {
		Set<ObjectId> ids = new HashSet<ObjectId>(refs.size());
		for (Ref ref : refs) {
			if (ref.getObjectId() != null)
				ids.add(ref.getObjectId());
		}
		return ids;
	}

	private void reportErrorDuringNegotiate(String msg) {
		try {
			pckOut.writeString("ERR " + msg + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (Throwable err) {
			// Ignore this secondary failure.
		}
	}

	private void processShallow() throws IOException {
		DepthWalk.RevWalk depthWalk =
			new DepthWalk.RevWalk(walk.getObjectReader(), depth);

		// Find all the commits which will be shallow
		for (ObjectId o : wantIds) {
			try {
				depthWalk.markRoot(depthWalk.parseCommit(o));
			} catch (IncorrectObjectTypeException notCommit) {
				// Ignore non-commits in this loop.
			}
		}

		RevCommit o;
		while ((o = depthWalk.next()) != null) {
			DepthWalk.Commit c = (DepthWalk.Commit) o;

			// Commits at the boundary which aren't already shallow in
			// the client need to be marked as such
			if (c.getDepth() == depth && !clientShallowCommits.contains(c))
				pckOut.writeString("shallow " + o.name()); //$NON-NLS-1$

			// Commits not on the boundary which are shallow in the client
			// need to become unshallowed
			if (c.getDepth() < depth && clientShallowCommits.remove(c)) {
				unshallowCommits.add(c.copy());
				pckOut.writeString("unshallow " + c.name()); //$NON-NLS-1$
			}
		}

		pckOut.end();
	}

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
	public void sendAdvertisedRefs(final RefAdvertiser adv) throws IOException,
			ServiceMayNotContinueException {
		try {
			advertiseRefsHook.advertiseRefs(this);
		} catch (ServiceMayNotContinueException fail) {
			if (fail.getMessage() != null) {
				adv.writeOne("ERR " + fail.getMessage()); //$NON-NLS-1$
				fail.setOutput();
			}
			throw fail;
		}

		adv.init(db);
		adv.advertiseCapability(OPTION_INCLUDE_TAG);
		adv.advertiseCapability(OPTION_MULTI_ACK_DETAILED);
		adv.advertiseCapability(OPTION_MULTI_ACK);
		adv.advertiseCapability(OPTION_OFS_DELTA);
		adv.advertiseCapability(OPTION_SIDE_BAND);
		adv.advertiseCapability(OPTION_SIDE_BAND_64K);
		adv.advertiseCapability(OPTION_THIN_PACK);
		adv.advertiseCapability(OPTION_NO_PROGRESS);
		adv.advertiseCapability(OPTION_SHALLOW);
		if (!biDirectionalPipe)
			adv.advertiseCapability(OPTION_NO_DONE);
		RequestPolicy policy = getRequestPolicy();
		if (policy == RequestPolicy.TIP
				|| policy == RequestPolicy.REACHABLE_COMMIT_TIP
				|| policy == null)
			adv.advertiseCapability(OPTION_ALLOW_TIP_SHA1_IN_WANT);
		adv.setDerefTags(true);
		Map<String, Ref> refs = getAdvertisedOrDefaultRefs();
		findSymrefs(adv, refs);
		advertised = adv.send(refs);
		if (adv.isEmpty())
			adv.advertiseId(ObjectId.zeroId(), "capabilities^{}"); //$NON-NLS-1$
		adv.end();
	}

	/**
	 * Send a message to the client, if it supports receiving them.
	 * <p>
	 * If the client doesn't support receiving messages, the message will be
	 * discarded, with no other indication to the caller or to the client.
	 *
	 * @param what
	 *            string describing the problem identified by the hook. The
	 *            string must not end with an LF, and must not contain an LF.
	 * @since 3.1
	 */
	public void sendMessage(String what) {
		try {
			msgOut.write(Constants.encode(what + "\n")); //$NON-NLS-1$
		} catch (IOException e) {
			// Ignore write failures.
		}
	}

	/**
	 * @return an underlying stream for sending messages to the client, or null.
	 * @since 3.1
	 */
	public OutputStream getMessageOutputStream() {
		return msgOut;
	}

	private void recvWants() throws IOException {
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
				continue;
			}

			if (line.startsWith("shallow ")) { //$NON-NLS-1$
				clientShallowCommits.add(ObjectId.fromString(line.substring(8)));
				continue;
			}

			if (!line.startsWith("want ") || line.length() < 45) //$NON-NLS-1$
				throw new PackProtocolException(MessageFormat.format(JGitText.get().expectedGot, "want", line)); //$NON-NLS-1$

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
	}

	private boolean negotiate() throws IOException {
		okToGiveUp = Boolean.FALSE;

		ObjectId last = ObjectId.zeroId();
		List<ObjectId> peerHas = new ArrayList<ObjectId>(64);
		for (;;) {
			String line;
			try {
				line = pckIn.readString();
			} catch (EOFException eof) {
				// EOF on stateless RPC (aka smart HTTP) and non-shallow request
				// means the client asked for the updated shallow/unshallow data,
				// disconnected, and will try another request with actual want/have.
				// Don't report the EOF here, its a bug in the protocol that the client
				// just disconnects without sending an END.
				if (!biDirectionalPipe && depth > 0)
					return false;
				throw eof;
			}

			if (line == PacketLineIn.END) {
				last = processHaveLines(peerHas, last);
				if (commonBase.isEmpty() || multiAck != MultiAck.OFF)
					pckOut.writeString("NAK\n"); //$NON-NLS-1$
				if (noDone && sentReady) {
					pckOut.writeString("ACK " + last.name() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
					return true;
				}
				if (!biDirectionalPipe)
					return false;
				pckOut.flush();

			} else if (line.startsWith("have ") && line.length() == 45) { //$NON-NLS-1$
				peerHas.add(ObjectId.fromString(line.substring(5)));

			} else if (line.equals("done")) { //$NON-NLS-1$
				last = processHaveLines(peerHas, last);

				if (commonBase.isEmpty())
					pckOut.writeString("NAK\n"); //$NON-NLS-1$

				else if (multiAck != MultiAck.OFF)
					pckOut.writeString("ACK " + last.name() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$

				return true;

			} else {
				throw new PackProtocolException(MessageFormat.format(JGitText.get().expectedGot, "have", line)); //$NON-NLS-1$
			}
		}
	}

	private ObjectId processHaveLines(List<ObjectId> peerHas, ObjectId last)
			throws IOException {
		preUploadHook.onBeginNegotiateRound(this, wantIds, peerHas.size());
		if (wantAll.isEmpty() && !wantIds.isEmpty())
			parseWants();
		if (peerHas.isEmpty())
			return last;

		sentReady = false;
		int haveCnt = 0;
		walk.getObjectReader().setAvoidUnreachableObjects(true);
		AsyncRevObjectQueue q = walk.parseAny(peerHas, false);
		try {
			for (;;) {
				RevObject obj;
				try {
					obj = q.next();
				} catch (MissingObjectException notFound) {
					continue;
				}
				if (obj == null)
					break;

				last = obj;
				haveCnt++;

				if (obj instanceof RevCommit) {
					RevCommit c = (RevCommit) obj;
					if (oldestTime == 0 || c.getCommitTime() < oldestTime)
						oldestTime = c.getCommitTime();
				}

				if (obj.has(PEER_HAS))
					continue;

				obj.add(PEER_HAS);
				if (obj instanceof RevCommit)
					((RevCommit) obj).carry(PEER_HAS);
				addCommonBase(obj);

				// If both sides have the same object; let the client know.
				//
				switch (multiAck) {
				case OFF:
					if (commonBase.size() == 1)
						pckOut.writeString("ACK " + obj.name() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
					break;
				case CONTINUE:
					pckOut.writeString("ACK " + obj.name() + " continue\n"); //$NON-NLS-1$ //$NON-NLS-2$
					break;
				case DETAILED:
					pckOut.writeString("ACK " + obj.name() + " common\n"); //$NON-NLS-1$ //$NON-NLS-2$
					break;
				}
			}
		} finally {
			q.release();
			walk.getObjectReader().setAvoidUnreachableObjects(false);
		}

		int missCnt = peerHas.size() - haveCnt;

		// If we don't have one of the objects but we're also willing to
		// create a pack at this point, let the client know so it stops
		// telling us about its history.
		//
		boolean didOkToGiveUp = false;
		if (0 < missCnt) {
			for (int i = peerHas.size() - 1; i >= 0; i--) {
				ObjectId id = peerHas.get(i);
				if (walk.lookupOrNull(id) == null) {
					didOkToGiveUp = true;
					if (okToGiveUp()) {
						switch (multiAck) {
						case OFF:
							break;
						case CONTINUE:
							pckOut.writeString("ACK " + id.name() + " continue\n"); //$NON-NLS-1$ //$NON-NLS-2$
							break;
						case DETAILED:
							pckOut.writeString("ACK " + id.name() + " ready\n"); //$NON-NLS-1$ //$NON-NLS-2$
							sentReady = true;
							break;
						}
					}
					break;
				}
			}
		}

		if (multiAck == MultiAck.DETAILED && !didOkToGiveUp && okToGiveUp()) {
			ObjectId id = peerHas.get(peerHas.size() - 1);
			sentReady = true;
			pckOut.writeString("ACK " + id.name() + " ready\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sentReady = true;
		}

		preUploadHook.onEndNegotiateRound(this, wantAll, haveCnt, missCnt, sentReady);
		peerHas.clear();
		return last;
	}

	private void parseWants() throws IOException {
		List<ObjectId> notAdvertisedWants = null;
		for (ObjectId obj : wantIds) {
			if (!advertised.contains(obj)) {
				if (notAdvertisedWants == null)
					notAdvertisedWants = new ArrayList<ObjectId>();
				notAdvertisedWants.add(obj);
			}
		}
		if (notAdvertisedWants != null)
			requestValidator.checkWants(this, notAdvertisedWants);

		AsyncRevObjectQueue q = walk.parseAny(wantIds, true);
		try {
			RevObject obj;
			while ((obj = q.next()) != null) {
				want(obj);

				if (!(obj instanceof RevCommit))
					obj.add(SATISFIED);
				if (obj instanceof RevTag) {
					obj = walk.peel(obj);
					if (obj instanceof RevCommit)
						want(obj);
				}
			}
			wantIds.clear();
		} catch (MissingObjectException notFound) {
			ObjectId id = notFound.getObjectId();
			throw new PackProtocolException(MessageFormat.format(
					JGitText.get().wantNotValid, id.name()), notFound);
		} finally {
			q.release();
		}
	}

	private void want(RevObject obj) {
		if (!obj.has(WANT)) {
			obj.add(WANT);
			wantAll.add(obj);
		}
	}

	/**
	 * Validator corresponding to {@link RequestPolicy#ADVERTISED}.
	 *
	 * @since 3.1
	 */
	public static final class AdvertisedRequestValidator
			implements RequestValidator {
		public void checkWants(UploadPack up, List<ObjectId> wants)
				throws PackProtocolException, IOException {
			if (!up.isBiDirectionalPipe())
				new ReachableCommitRequestValidator().checkWants(up, wants);
			else if (!wants.isEmpty())
				throw new PackProtocolException(MessageFormat.format(
						JGitText.get().wantNotValid, wants.iterator().next().name()));
		}
	}

	/**
	 * Validator corresponding to {@link RequestPolicy#REACHABLE_COMMIT}.
	 *
	 * @since 3.1
	 */
	public static final class ReachableCommitRequestValidator
			implements RequestValidator {
		public void checkWants(UploadPack up, List<ObjectId> wants)
				throws PackProtocolException, IOException {
			checkNotAdvertisedWants(up.getRevWalk(), wants,
					refIdSet(up.getAdvertisedRefs().values()));
		}
	}

	/**
	 * Validator corresponding to {@link RequestPolicy#TIP}.
	 *
	 * @since 3.1
	 */
	public static final class TipRequestValidator implements RequestValidator {
		public void checkWants(UploadPack up, List<ObjectId> wants)
				throws PackProtocolException, IOException {
			if (!up.isBiDirectionalPipe())
				new ReachableCommitTipRequestValidator().checkWants(up, wants);
			else if (!wants.isEmpty()) {
				Set<ObjectId> refIds =
					refIdSet(up.getRepository().getRefDatabase().getRefs(ALL).values());
				for (ObjectId obj : wants) {
					if (!refIds.contains(obj))
						throw new PackProtocolException(MessageFormat.format(
								JGitText.get().wantNotValid, obj.name()));
				}
			}
		}
	}

	/**
	 * Validator corresponding to {@link RequestPolicy#REACHABLE_COMMIT_TIP}.
	 *
	 * @since 3.1
	 */
	public static final class ReachableCommitTipRequestValidator
			implements RequestValidator {
		public void checkWants(UploadPack up, List<ObjectId> wants)
				throws PackProtocolException, IOException {
			checkNotAdvertisedWants(up.getRevWalk(), wants,
					refIdSet(up.getRepository().getRefDatabase().getRefs(ALL).values()));
		}
	}

	/**
	 * Validator corresponding to {@link RequestPolicy#ANY}.
	 *
	 * @since 3.1
	 */
	public static final class AnyRequestValidator implements RequestValidator {
		public void checkWants(UploadPack up, List<ObjectId> wants)
				throws PackProtocolException, IOException {
			// All requests are valid.
		}
	}

	private static void checkNotAdvertisedWants(RevWalk walk,
			List<ObjectId> notAdvertisedWants, Set<ObjectId> reachableFrom)
			throws MissingObjectException, IncorrectObjectTypeException, IOException {
		// Walk the requested commits back to the provided set of commits. If any
		// commit exists, a branch was deleted or rewound and the repository owner
		// no longer exports that requested item. If the requested commit is merged
		// into an advertised branch it will be marked UNINTERESTING and no commits
		// return.

		AsyncRevObjectQueue q = walk.parseAny(notAdvertisedWants, true);
		try {
			RevObject obj;
			while ((obj = q.next()) != null) {
				if (!(obj instanceof RevCommit))
					throw new PackProtocolException(MessageFormat.format(
						JGitText.get().wantNotValid, obj.name()));
				walk.markStart((RevCommit) obj);
			}
		} catch (MissingObjectException notFound) {
			ObjectId id = notFound.getObjectId();
			throw new PackProtocolException(MessageFormat.format(
					JGitText.get().wantNotValid, id.name()), notFound);
		} finally {
			q.release();
		}
		for (ObjectId id : reachableFrom) {
			try {
				walk.markUninteresting(walk.parseCommit(id));
			} catch (IncorrectObjectTypeException notCommit) {
				continue;
			}
		}

		RevCommit bad = walk.next();
		if (bad != null) {
			throw new PackProtocolException(MessageFormat.format(
					JGitText.get().wantNotValid,
					bad.name()));
		}
		walk.reset();
	}

	private void addCommonBase(final RevObject o) {
		if (!o.has(COMMON)) {
			o.add(COMMON);
			commonBase.add(o);
			okToGiveUp = null;
		}
	}

	private boolean okToGiveUp() throws PackProtocolException {
		if (okToGiveUp == null)
			okToGiveUp = Boolean.valueOf(okToGiveUpImp());
		return okToGiveUp.booleanValue();
	}

	private boolean okToGiveUpImp() throws PackProtocolException {
		if (commonBase.isEmpty())
			return false;

		try {
			for (RevObject obj : wantAll) {
				if (!wantSatisfied(obj))
					return false;
			}
			return true;
		} catch (IOException e) {
			throw new PackProtocolException(JGitText.get().internalRevisionError, e);
		}
	}

	private boolean wantSatisfied(final RevObject want) throws IOException {
		if (want.has(SATISFIED))
			return true;

		walk.resetRetain(SAVE);
		walk.markStart((RevCommit) want);
		if (oldestTime != 0)
			walk.setRevFilter(CommitTimeRevFilter.after(oldestTime * 1000L));
		for (;;) {
			final RevCommit c = walk.next();
			if (c == null)
				break;
			if (c.has(PEER_HAS)) {
				addCommonBase(c);
				want.add(SATISFIED);
				return true;
			}
		}
		return false;
	}

	private void sendPack() throws IOException {
		final boolean sideband = options.contains(OPTION_SIDE_BAND)
				|| options.contains(OPTION_SIDE_BAND_64K);

		if (!biDirectionalPipe) {
			// Ensure the request was fully consumed. Any remaining input must
			// be a protocol error. If we aren't at EOF the implementation is broken.
			int eof = rawIn.read();
			if (0 <= eof)
				throw new CorruptObjectException(MessageFormat.format(
						JGitText.get().expectedEOFReceived,
						"\\x" + Integer.toHexString(eof))); //$NON-NLS-1$
		}

		if (sideband) {
			try {
				sendPack(true);
			} catch (ServiceMayNotContinueException noPack) {
				// This was already reported on (below).
				throw noPack;
			} catch (IOException err) {
				if (reportInternalServerErrorOverSideband())
					throw new UploadPackInternalServerErrorException(err);
				else
					throw err;
			} catch (RuntimeException err) {
				if (reportInternalServerErrorOverSideband())
					throw new UploadPackInternalServerErrorException(err);
				else
					throw err;
			} catch (Error err) {
				if (reportInternalServerErrorOverSideband())
					throw new UploadPackInternalServerErrorException(err);
				else
					throw err;
			}
		} else {
			sendPack(false);
		}
	}

	private boolean reportInternalServerErrorOverSideband() {
		try {
			@SuppressWarnings("resource" /* java 7 */)
			SideBandOutputStream err = new SideBandOutputStream(
					SideBandOutputStream.CH_ERROR,
					SideBandOutputStream.SMALL_BUF,
					rawOut);
			err.write(Constants.encode(JGitText.get().internalServerError));
			err.flush();
			return true;
		} catch (Throwable cannotReport) {
			// Ignore the reason. This is a secondary failure.
			return false;
		}
	}

	private void sendPack(final boolean sideband) throws IOException {
		ProgressMonitor pm = NullProgressMonitor.INSTANCE;
		OutputStream packOut = rawOut;

		if (sideband) {
			int bufsz = SideBandOutputStream.SMALL_BUF;
			if (options.contains(OPTION_SIDE_BAND_64K))
				bufsz = SideBandOutputStream.MAX_BUF;

			packOut = new SideBandOutputStream(SideBandOutputStream.CH_DATA,
					bufsz, rawOut);
			if (!options.contains(OPTION_NO_PROGRESS)) {
				msgOut = new SideBandOutputStream(
						SideBandOutputStream.CH_PROGRESS, bufsz, rawOut);
				pm = new SideBandProgressMonitor(msgOut);
			}
		}

		try {
			if (wantAll.isEmpty()) {
				preUploadHook.onSendPack(this, wantIds, commonBase);
			} else {
				preUploadHook.onSendPack(this, wantAll, commonBase);
			}
			msgOut.flush();
		} catch (ServiceMayNotContinueException noPack) {
			if (sideband && noPack.getMessage() != null) {
				noPack.setOutput();
				@SuppressWarnings("resource" /* java 7 */)
				SideBandOutputStream err = new SideBandOutputStream(
						SideBandOutputStream.CH_ERROR,
						SideBandOutputStream.SMALL_BUF, rawOut);
				err.write(Constants.encode(noPack.getMessage()));
				err.flush();
			}
			throw noPack;
		}

		PackConfig cfg = packConfig;
		if (cfg == null)
			cfg = new PackConfig(db);
		final PackWriter pw = new PackWriter(cfg, walk.getObjectReader());
		try {
			pw.setIndexDisabled(true);
			pw.setUseCachedPacks(true);
			pw.setUseBitmaps(depth == 0 && clientShallowCommits.isEmpty());
			pw.setReuseDeltaCommits(true);
			pw.setDeltaBaseAsOffset(options.contains(OPTION_OFS_DELTA));
			pw.setThin(options.contains(OPTION_THIN_PACK));
			pw.setReuseValidatingObjects(false);

			if (commonBase.isEmpty() && refs != null) {
				Set<ObjectId> tagTargets = new HashSet<ObjectId>();
				for (Ref ref : refs.values()) {
					if (ref.getPeeledObjectId() != null)
						tagTargets.add(ref.getPeeledObjectId());
					else if (ref.getObjectId() == null)
						continue;
					else if (ref.getName().startsWith(Constants.R_HEADS))
						tagTargets.add(ref.getObjectId());
				}
				pw.setTagTargets(tagTargets);
			}

			if (depth > 0)
				pw.setShallowPack(depth, unshallowCommits);

			RevWalk rw = walk;
			if (wantAll.isEmpty()) {
				pw.preparePack(pm, wantIds, commonBase);
			} else {
				walk.reset();

				ObjectWalk ow = walk.toObjectWalkWithSameObjects();
				pw.preparePack(pm, ow, wantAll, commonBase);
				rw = ow;
			}

			if (options.contains(OPTION_INCLUDE_TAG) && refs != null) {
				for (Ref ref : refs.values()) {
					ObjectId objectId = ref.getObjectId();

					// If the object was already requested, skip it.
					if (wantAll.isEmpty()) {
						if (wantIds.contains(objectId))
							continue;
					} else {
						RevObject obj = rw.lookupOrNull(objectId);
						if (obj != null && obj.has(WANT))
							continue;
					}

					if (!ref.isPeeled())
						ref = db.peel(ref);

					ObjectId peeledId = ref.getPeeledObjectId();
					if (peeledId == null)
						continue;

					objectId = ref.getObjectId();
					if (pw.willInclude(peeledId) && !pw.willInclude(objectId))
						pw.addObject(rw.parseAny(objectId));
				}
			}

			pw.writePack(pm, NullProgressMonitor.INSTANCE, packOut);

			if (msgOut != NullOutputStream.INSTANCE) {
				String msg = pw.getStatistics().getMessage() + '\n';
				msgOut.write(Constants.encode(msg));
				msgOut.flush();
			}

		} finally {
			statistics = pw.getStatistics();
			if (statistics != null)
				logger.onPackStatistics(statistics);
			pw.release();
		}

		if (sideband)
			pckOut.end();
	}

	private void findSymrefs(
			final RefAdvertiser adv, final Map<String, Ref> refs) {
		Ref head = refs.get(Constants.HEAD);
		if (head != null && head.isSymbolic()) {
			adv.addSymref(Constants.HEAD, head.getLeaf().getName());
		}
	}
}
