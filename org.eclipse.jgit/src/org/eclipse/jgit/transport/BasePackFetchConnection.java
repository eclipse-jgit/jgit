/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.internal.storage.file.PackLock;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevCommitList;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.GitProtocolConstants.MultiAck;
import org.eclipse.jgit.transport.PacketLineIn.AckNackResult;
import org.eclipse.jgit.util.TemporaryBuffer;

/**
 * Fetch implementation using the native Git pack transfer service.
 * <p>
 * This is the canonical implementation for transferring objects from the remote
 * repository to the local repository by talking to the 'git-upload-pack'
 * service. Objects are packed on the remote side into a pack file and then sent
 * down the pipe to us.
 * <p>
 * This connection requires only a bi-directional pipe or socket, and thus is
 * easily wrapped up into a local process pipe, anonymous TCP socket, or a
 * command executed through an SSH tunnel.
 * <p>
 * If {@link BasePackConnection#statelessRPC} is {@code true}, this connection
 * can be tunneled over a request-response style RPC system like HTTP.  The RPC
 * call boundary is determined by this class switching from writing to the
 * OutputStream to reading from the InputStream.
 * <p>
 * Concrete implementations should just call
 * {@link #init(java.io.InputStream, java.io.OutputStream)} and
 * {@link #readAdvertisedRefs()} methods in constructor or before any use. They
 * should also handle resources releasing in {@link #close()} method if needed.
 */
public abstract class BasePackFetchConnection extends BasePackConnection
		implements FetchConnection {
	/**
	 * Maximum number of 'have' lines to send before giving up.
	 * <p>
	 * During {@link #negotiate(ProgressMonitor)} we send at most this many
	 * commits to the remote peer as 'have' lines without an ACK response before
	 * we give up.
	 */
	private static final int MAX_HAVES = 256;

	/**
	 * Amount of data the client sends before starting to read.
	 * <p>
	 * Any output stream given to the client must be able to buffer this many
	 * bytes before the client will stop writing and start reading from the
	 * input stream. If the output stream blocks before this many bytes are in
	 * the send queue, the system will deadlock.
	 */
	protected static final int MIN_CLIENT_BUFFER = 2 * 32 * 46 + 8;

	/**
	 * Include tags if we are also including the referenced objects.
	 * @since 2.0
	 */
	public static final String OPTION_INCLUDE_TAG = GitProtocolConstants.OPTION_INCLUDE_TAG;

	/**
	 * Mutli-ACK support for improved negotiation.
	 * @since 2.0
	 */
	public static final String OPTION_MULTI_ACK = GitProtocolConstants.OPTION_MULTI_ACK;

	/**
	 * Mutli-ACK detailed support for improved negotiation.
	 * @since 2.0
	 */
	public static final String OPTION_MULTI_ACK_DETAILED = GitProtocolConstants.OPTION_MULTI_ACK_DETAILED;

	/**
	 * The client supports packs with deltas but not their bases.
	 * @since 2.0
	 */
	public static final String OPTION_THIN_PACK = GitProtocolConstants.OPTION_THIN_PACK;

	/**
	 * The client supports using the side-band for progress messages.
	 * @since 2.0
	 */
	public static final String OPTION_SIDE_BAND = GitProtocolConstants.OPTION_SIDE_BAND;

	/**
	 * The client supports using the 64K side-band for progress messages.
	 * @since 2.0
	 */
	public static final String OPTION_SIDE_BAND_64K = GitProtocolConstants.OPTION_SIDE_BAND_64K;

	/**
	 * The client supports packs with OFS deltas.
	 * @since 2.0
	 */
	public static final String OPTION_OFS_DELTA = GitProtocolConstants.OPTION_OFS_DELTA;

	/**
	 * The client supports shallow fetches.
	 * @since 2.0
	 */
	public static final String OPTION_SHALLOW = GitProtocolConstants.OPTION_SHALLOW;

	/**
	 * The client does not want progress messages and will ignore them.
	 * @since 2.0
	 */
	public static final String OPTION_NO_PROGRESS = GitProtocolConstants.OPTION_NO_PROGRESS;

	/**
	 * The client supports receiving a pack before it has sent "done".
	 * @since 2.0
	 */
	public static final String OPTION_NO_DONE = GitProtocolConstants.OPTION_NO_DONE;

	/**
	 * The client supports fetching objects at the tip of any ref, even if not
	 * advertised.
	 * @since 3.1
	 */
	public static final String OPTION_ALLOW_TIP_SHA1_IN_WANT = GitProtocolConstants.OPTION_ALLOW_TIP_SHA1_IN_WANT;

	/**
	 * The client supports fetching objects that are reachable from a tip of a
	 * ref that is allowed to fetch.
	 * @since 4.1
	 */
	public static final String OPTION_ALLOW_REACHABLE_SHA1_IN_WANT = GitProtocolConstants.OPTION_ALLOW_REACHABLE_SHA1_IN_WANT;

	private final RevWalk walk;

	/** All commits that are immediately reachable by a local ref. */
	private RevCommitList<RevCommit> reachableCommits;

	/** Marks an object as having all its dependencies. */
	final RevFlag REACHABLE;

	/** Marks a commit known to both sides of the connection. */
	final RevFlag COMMON;

	/** Like {@link #COMMON} but means its also in {@link #pckState}. */
	private final RevFlag STATE;

	/** Marks a commit listed in the advertised refs. */
	final RevFlag ADVERTISED;

	private MultiAck multiAck = MultiAck.OFF;

	private boolean thinPack;

	private boolean sideband;

	private boolean includeTags;

	private int depth;

	private boolean allowOfsDelta;

	private boolean noDone;

	private boolean noProgress;

	private String lockMessage;

	private PackLock packLock;

	/** RPC state, if {@link BasePackConnection#statelessRPC} is true. */
	private TemporaryBuffer.Heap state;

	private PacketLineOut pckState;

	/**
	 * Create a new connection to fetch using the native git transport.
	 *
	 * @param packTransport
	 *            the transport.
	 */
	public BasePackFetchConnection(final PackTransport packTransport) {
		super(packTransport);

		if (local != null) {
			final FetchConfig cfg = local.getConfig().get(FetchConfig::new);
			allowOfsDelta = cfg.allowOfsDelta;
		} else {
			allowOfsDelta = true;
		}
		includeTags = transport.getTagOpt() != TagOpt.NO_TAGS;
		depth = transport.getDepth();
		thinPack = transport.isFetchThin();

		if (local != null) {
			walk = new RevWalk(local);
			reachableCommits = new RevCommitList<>();
			REACHABLE = walk.newFlag("REACHABLE"); //$NON-NLS-1$
			COMMON = walk.newFlag("COMMON"); //$NON-NLS-1$
			STATE = walk.newFlag("STATE"); //$NON-NLS-1$
			ADVERTISED = walk.newFlag("ADVERTISED"); //$NON-NLS-1$

			walk.carry(COMMON);
			walk.carry(REACHABLE);
			walk.carry(ADVERTISED);
		} else {
			walk = null;
			REACHABLE = null;
			COMMON = null;
			STATE = null;
			ADVERTISED = null;
		}
	}

	private static class FetchConfig {
		final boolean allowOfsDelta;

		FetchConfig(final Config c) {
			allowOfsDelta = c.getBoolean("repack", "usedeltabaseoffset", true); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	@Override
	public final void fetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have)
			throws TransportException {
		fetch(monitor, want, have, null);
	}

	/**
	 * @since 3.0
	 */
	@Override
	public final void fetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have,
			OutputStream outputStream) throws TransportException {
		markStartedOperation();
		doFetch(monitor, want, have, outputStream);
	}

	@Override
	public boolean didFetchIncludeTags() {
		return false;
	}

	@Override
	public boolean didFetchTestConnectivity() {
		return false;
	}

	@Override
	public void setPackLockMessage(final String message) {
		lockMessage = message;
	}

	@Override
	public Collection<PackLock> getPackLocks() {
		if (packLock != null)
			return Collections.singleton(packLock);
		return Collections.<PackLock> emptyList();
	}

	/**
	 * Execute common ancestor negotiation and fetch the objects.
	 *
	 * @param monitor
	 *            progress monitor to receive status updates. If the monitor is
	 *            the {@link NullProgressMonitor#INSTANCE}, then the no-progress
	 *            option enabled.
	 * @param want
	 *            the advertised remote references the caller wants to fetch.
	 * @param have
	 *            additional objects to assume that already exist locally. This
	 *            will be added to the set of objects reachable from the
	 *            destination repository's references.
	 * @param outputStream
	 *            ouputStream to write sideband messages to
	 * @throws TransportException
	 *             if any exception occurs.
	 * @since 3.0
	 */
	protected void doFetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have,
			OutputStream outputStream) throws TransportException {
		try {
			noProgress = monitor == NullProgressMonitor.INSTANCE;

			markRefsAdvertised();
			markReachable(have, maxTimeWanted(want));

			if (statelessRPC) {
				state = new TemporaryBuffer.Heap(Integer.MAX_VALUE);
				pckState = new PacketLineOut(state);
			}

			if (sendWants(want)) {
				try {
				negotiate(monitor);
				} catch (Exception ex) {
					ex.printStackTrace();
					throw ex;
				}

				walk.dispose();
				reachableCommits = null;
				state = null;
				pckState = null;

				receivePack(monitor, outputStream);
			}
		} catch (CancelledException ce) {
			close();
			return; // Caller should test (or just know) this themselves.
		} catch (IOException err) {
			close();
			throw new TransportException(err.getMessage(), err);
		} catch (RuntimeException err) {
			close();
			throw new TransportException(err.getMessage(), err);
		}
	}

	@Override
	public void close() {
		if (walk != null)
			walk.close();
		super.close();
	}

	private int maxTimeWanted(final Collection<Ref> wants) {
		int maxTime = 0;
		for (final Ref r : wants) {
			try {
				final RevObject obj = walk.parseAny(r.getObjectId());
				if (obj instanceof RevCommit) {
					final int cTime = ((RevCommit) obj).getCommitTime();
					if (maxTime < cTime)
						maxTime = cTime;
				}
			} catch (IOException error) {
				// We don't have it, but we want to fetch (thus fixing error).
			}
		}
		return maxTime;
	}

	private void markReachable(final Set<ObjectId> have, final int maxTime)
			throws IOException {
		Map<String, Ref> refs = local.getRefDatabase().getRefs(ALL);
		System.out.println(Thread.currentThread().getName() + ":\t"
				+ "BasePackFetchConnection.markReachable.refs.size()='"
				+ refs.size() + "'");
		for (final Ref r : refs.values()) {
			ObjectId id = r.getPeeledObjectId();
			System.out.println(Thread.currentThread().getName() + ":\t" +
					"BasePackFetchConnection.markReachable.#1.id='" + id + "'");
			if (id == null)
				id = r.getObjectId();
			if (id == null)
				continue;
			parseReachable(id);
		}

		for (ObjectId id : local.getAdditionalHaves()) {
			System.out.println(Thread.currentThread().getName() + ":\t" +
					"BasePackFetchConnection.markReachable.#2.id='" + id + "'");
			parseReachable(id);
		}

		for (ObjectId id : have) {
			System.out.println(Thread.currentThread().getName() + ":\t" +
					"BasePackFetchConnection.markReachable.#2.id='" + id + "'");
			parseReachable(id);
		}

		if (maxTime > 0) {
			System.out
					.println(Thread.currentThread().getName() + ":\t"
							+ "BasePackFetchConnection.markReachable.maxTime>0");
			// Mark reachable commits until we reach maxTime. These may
			// wind up later matching up against things we want and we
			// can avoid asking for something we already happen to have.
			//
			final Date maxWhen = new Date(maxTime * 1000L);
			walk.sort(RevSort.COMMIT_TIME_DESC);
			walk.markStart(reachableCommits);
			walk.setRevFilter(CommitTimeRevFilter.after(maxWhen));
			for (;;) {
				final RevCommit c = walk.next();
				if (c == null)
					break;
				if (c.has(ADVERTISED) && !c.has(COMMON)) {
					// This is actually going to be a common commit, but
					// our peer doesn't know that fact yet.
					//
					c.add(COMMON);
					c.carry(COMMON);
					reachableCommits.add(c);
				}
			}
		}
	}

	private void parseReachable(ObjectId id) {
		try {
			RevCommit o = walk.parseCommit(id);
			if (!o.has(REACHABLE)) {
				o.add(REACHABLE);
				reachableCommits.add(o);
			}
		} catch (IOException readError) {
			// If we cannot read the value of the ref skip it.
		}
	}

	private boolean sendWants(final Collection<Ref> want) throws IOException {
		final PacketLineOut p = statelessRPC ? pckState : pckOut;
		boolean first = true;
		for (final Ref r : want) {
			System.out
					.println(Thread.currentThread().getName() + ":\t"
							+ "BasePackFetchConnection.sendWants.r='" + r
							+ "'");
			ObjectId objectId = r.getObjectId();
			System.out.println(Thread.currentThread().getName() + ":\t"
					+ "BasePackFetchConnection.sendWants.objectId='"
					+ objectId + "'");
			if (objectId == null) {
				continue;
			}
			try {
				if (walk.parseAny(objectId).has(REACHABLE)) {
					// We already have this object. Asking for it is
					// not a very good idea.
					//
					continue;
				}
			} catch (IOException err) {
				// Its OK, we don't have it, but we want to fix that
				// by fetching the object from the other side.
			}

			final StringBuilder line = new StringBuilder(46);
			line.append("want "); //$NON-NLS-1$
			line.append(objectId.name());
			if (first) {
				line.append(enableCapabilities());
				first = false;
			}
			line.append('\n');
			// TODO: if commit is mentioned in $GITDIR/shallow we have to send a
			// shallow <commit-id>
			// see
			// https://github.com/git/git/blob/master/Documentation/technical/pack-protocol.txt#L220
			final StringBuilder builder = new StringBuilder(46);
			if (depth > 0 && depth < Transport.DEPTH_INFINITE) {
				builder.append("deepen "); //$NON-NLS-1$
				builder.append(depth);
				builder.append('\n');
			}
			System.out.println(Thread.currentThread().getName() + ":\t"
					+ "BasePackFetchConnection.sendWants.line='"
					+ line.toString() + "'");
			System.out.println(Thread.currentThread().getName() + ":\t"
					+ "BasePackFetchConnection.sendWants.line='"
					+ builder.toString() + "'");
			p.writeString(line.toString());
			if (depth != Transport.DEPTH_INFINITE) {
				p.writeString(builder.toString());
			}
		}
		if (first)
			return false;
		p.end();
		outNeedsEnd = false;
		return true;
	}

	private String enableCapabilities() throws TransportException {
		final StringBuilder line = new StringBuilder();
		if (noProgress)
			wantCapability(line, OPTION_NO_PROGRESS);
		if (includeTags)
			includeTags = wantCapability(line, OPTION_INCLUDE_TAG);
		if (allowOfsDelta)
			wantCapability(line, OPTION_OFS_DELTA);

		if (wantCapability(line, OPTION_MULTI_ACK_DETAILED)) {
			multiAck = MultiAck.DETAILED;
			if (statelessRPC)
				noDone = wantCapability(line, OPTION_NO_DONE);
		} else if (wantCapability(line, OPTION_MULTI_ACK))
			multiAck = MultiAck.CONTINUE;
		else
			multiAck = MultiAck.OFF;

		if (thinPack)
			thinPack = wantCapability(line, OPTION_THIN_PACK);
		if (wantCapability(line, OPTION_SIDE_BAND_64K))
			sideband = true;
		else if (wantCapability(line, OPTION_SIDE_BAND))
			sideband = true;

		if (statelessRPC && multiAck != MultiAck.DETAILED) {
			// Our stateless RPC implementation relies upon the detailed
			// ACK status to tell us common objects for reuse in future
			// requests.  If its not enabled, we can't talk to the peer.
			//
			throw new PackProtocolException(uri, MessageFormat.format(
					JGitText.get().statelessRPCRequiresOptionToBeEnabled,
					OPTION_MULTI_ACK_DETAILED));
		}

		addUserAgentCapability(line);
		return line.toString();
	}

	private void negotiate(final ProgressMonitor monitor) throws IOException,
			CancelledException {
		final MutableObjectId ackId = new MutableObjectId();
		int resultsPending = 0;
		int havesSent = 0;
		int havesSinceLastContinue = 0;
		boolean receivedContinue = false;
		boolean receivedAck = false;
		boolean receivedReady = false;
		System.out.println(Thread.currentThread().getName() + ":\t"
				+ "BasePackFetchConnection.negotiate.statelessRPC='"
				+ statelessRPC + "'");

		if (statelessRPC)
			state.writeTo(out, null);

		System.out.println(Thread.currentThread().getName() + ":\t" +
				"BasePackFetchConnection.negotiate.negotiateBegin - begin");

		negotiateBegin();
		System.out.println(Thread.currentThread().getName() + ":\t" +
				"BasePackFetchConnection.negotiate.negotiateBegin - end");

		if (depth > 0 && depth < Transport.DEPTH_INFINITE) {
			handleShallowUnshallowLines();
		}

		System.out
				.println(Thread.currentThread().getName() + ":\t"
						+ "BasePackFetchConnection.negotiate.sendHaves - begin");

		SEND_HAVES: for (;;) {
			System.out.println(Thread.currentThread().getName() + ":\t"
					+ "BasePackFetchConnection.negotiate.#walk.next");
			final RevCommit c = walk.next();
			System.out
					.println(Thread.currentThread().getName() + ":\t"
							+ "BasePackFetchConnection.negotiate.c='" + c
							+ "'");
			if (c == null)
				break SEND_HAVES;

			pckOut.writeString("have " + c.getId().name() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
			havesSent++;
			havesSinceLastContinue++;

			if ((31 & havesSent) != 0) {
				// We group the have lines into blocks of 32, each marked
				// with a flush (aka end). This one is within a block so
				// continue with another have line.
				//
				continue;
			}

			if (monitor.isCancelled())
				throw new CancelledException();

			pckOut.end();
			resultsPending++; // Each end will cause a result to come back.

			if (havesSent == 32 && !statelessRPC) {
				// On the first block we race ahead and try to send
				// more of the second block while waiting for the
				// remote to respond to our first block request.
				// This keeps us one block ahead of the peer.
				//
				continue;
			}

			READ_RESULT: for (;;) {
				System.out.println(Thread.currentThread().getName() + ":\t"
						+ "BasePackFetchConnection.negotiate.SEND_HAVES.READ_RESULT'");
				final AckNackResult anr = pckIn.readACK(ackId);
				switch (anr) {
				case NAK:
					// More have lines are necessary to compute the
					// pack on the remote side. Keep doing that.
					//
					resultsPending--;
					break READ_RESULT;

				case ACK:
					// The remote side is happy and knows exactly what
					// to send us. There is no further negotiation and
					// we can break out immediately.
					//
					multiAck = MultiAck.OFF;
					resultsPending = 0;
					receivedAck = true;
					if (statelessRPC)
						state.writeTo(out, null);
					break SEND_HAVES;

				case ACK_CONTINUE:
				case ACK_COMMON:
				case ACK_READY:
					// The server knows this commit (ackId). We don't
					// need to send any further along its ancestry, but
					// we need to continue to talk about other parts of
					// our local history.
					//
					markCommon(walk.parseAny(ackId), anr);
					receivedAck = true;
					receivedContinue = true;
					havesSinceLastContinue = 0;
					if (anr == AckNackResult.ACK_READY)
						receivedReady = true;
					break;
				}

				if (monitor.isCancelled())
					throw new CancelledException();
			}

			if (noDone & receivedReady)
				break SEND_HAVES;
			if (statelessRPC)
				state.writeTo(out, null);

			if (receivedContinue && havesSinceLastContinue > MAX_HAVES) {
				// Our history must be really different from the remote's.
				// We just sent a whole slew of have lines, and it did not
				// recognize any of them. Avoid sending our entire history
				// to them by giving up early.
				//
				break SEND_HAVES;
			}
		}
		System.out
				.println(Thread.currentThread().getName() + ":\t"
						+ "BasePackFetchConnection.negotiate.sendHaves - end");

		// Tell the remote side we have run out of things to talk about.
		//
		if (monitor.isCancelled())
			throw new CancelledException();

		if (!receivedReady || !noDone) {
			// When statelessRPC is true we should always leave SEND_HAVES
			// loop above while in the middle of a request. This allows us
			// to just write done immediately.
			//
			pckOut.writeString("done\n"); //$NON-NLS-1$
			pckOut.flush();
			System.out.println(Thread.currentThread().getName() + ":\t"
					+ "BasePackFetchConnection.negotiate write done!");
		}

		if (!receivedAck) {
			// Apparently if we have never received an ACK earlier
			// there is one more result expected from the done we
			// just sent to the remote.
			//
			multiAck = MultiAck.OFF;
			resultsPending++;
		}
		System.out.println(Thread.currentThread().getName() + ":\t" +
				"BasePackFetchConnection.negotiate.readResult - begin");

		READ_RESULT: while (resultsPending > 0 || multiAck != MultiAck.OFF) {
			System.out.println(Thread.currentThread().getName() + ":\t"
					+ "BasePackFetchConnection.negotiate.READ_RESULT");
			final AckNackResult anr = pckIn.readACK(ackId);
			resultsPending--;
			switch (anr) {
			case NAK:
				// A NAK is a response to an end we queued earlier
				// we eat it and look for another ACK/NAK message.
				//
				break;

			case ACK:
				// A solitary ACK at this point means the remote won't
				// speak anymore, but is going to send us a pack now.
				//
				break READ_RESULT;

			case ACK_CONTINUE:
			case ACK_COMMON:
			case ACK_READY:
				// We will expect a normal ACK to break out of the loop.
				//
				multiAck = MultiAck.CONTINUE;
				break;
			}

			if (monitor.isCancelled())
				throw new CancelledException();
		}
		System.out.println(Thread.currentThread().getName() + ":\t" +
				"BasePackFetchConnection.negotiate.readResult - end");
	}

	private void negotiateBegin() throws IOException {
		System.out.println(Thread.currentThread().getName() + ":\t"
				+ "BasePackFetchConnection.negotiateBegin");
		System.out.println(Thread.currentThread().getName() + ":\t"
				+ "BasePackFetchConnection.negotiateBegin.walk='"
				+ walk.getClass() + "'");
		System.out.println(Thread.currentThread().getName() + ":\t"
				+ "BasePackFetchConnection.negotiateBegin() #1");
		walk.resetRetain(REACHABLE, ADVERTISED);
		System.out.println(Thread.currentThread().getName() + ":\t"
				+ "BasePackFetchConnection.negotiateBegin() #2");
		walk.markStart(reachableCommits);
		System.out.println(Thread.currentThread().getName() + ":\t"
				+ "BasePackFetchConnection.negotiateBegin() #3");
		walk.sort(RevSort.COMMIT_TIME_DESC);
		System.out.println(Thread.currentThread().getName() + ":\t"
				+ "BasePackFetchConnection.negotiateBegin() #4");
		walk.setRevFilter(new RevFilter() {
			@Override
			public RevFilter clone() {
				return this;
			}

			@Override
			public boolean include(final RevWalk walker, final RevCommit c) {
				final boolean remoteKnowsIsCommon = c.has(COMMON);
				if (c.has(ADVERTISED)) {
					// Remote advertised this, and we have it, hence common.
					// Whether or not the remote knows that fact is tested
					// before we added the flag. If the remote doesn't know
					// we have to still send them this object.
					//
					c.add(COMMON);
				}
				return !remoteKnowsIsCommon;
			}

			@Override
			public boolean requiresCommitBody() {
				return false;
			}
		});
		System.out.println(Thread.currentThread().getName() + ":\t"
				+ "BasePackFetchConnection.negotiateBegin() #5");
	}

	private void markRefsAdvertised() {
		for (final Ref r : getRefs()) {
			System.out.println(Thread.currentThread().getName() + ":\t"
					+
					"BasePackFetchConnection.markRefsAdvertised.r.getObjectId='"
							+ r.getObjectId() + "'");
			markAdvertised(r.getObjectId());
			if (r.getPeeledObjectId() != null)
				markAdvertised(r.getPeeledObjectId());
		}
	}

	private void markAdvertised(final AnyObjectId id) {
		try {
			walk.parseAny(id).add(ADVERTISED);
		} catch (IOException readError) {
			// We probably just do not have this object locally.
		}
	}

	private void markCommon(final RevObject obj, final AckNackResult anr)
			throws IOException {
		if (statelessRPC && anr == AckNackResult.ACK_COMMON && !obj.has(STATE)) {
			StringBuilder s;

			s = new StringBuilder(6 + Constants.OBJECT_ID_STRING_LENGTH);
			s.append("have "); //$NON-NLS-1$
			s.append(obj.name());
			s.append('\n');
			pckState.writeString(s.toString());
			obj.add(STATE);
		}
		obj.add(COMMON);
		if (obj instanceof RevCommit)
			((RevCommit) obj).carry(COMMON);
	}

	private void receivePack(final ProgressMonitor monitor,
			OutputStream outputStream) throws IOException {
		onReceivePack();
		InputStream input = in;
		if (sideband)
			input = new SideBandInputStream(input, monitor, getMessageWriter(),
					outputStream);

		try (ObjectInserter ins = local.newObjectInserter()) {
			PackParser parser = ins.newPackParser(input);
			parser.setAllowThin(thinPack);
			parser.setObjectChecker(transport.getObjectChecker());
			parser.setLockMessage(lockMessage);
			packLock = parser.parse(monitor);
			ins.flush();
		}
	}

	/**
	 * Notification event delivered just before the pack is received from the
	 * network. This event can be used by RPC such as {@link TransportHttp} to
	 * disable its request magic and ensure the pack stream is read correctly.
	 *
	 * @since 2.0
	 */
	protected void onReceivePack() {
		// By default do nothing for TCP based protocols.
	}

	private static class CancelledException extends Exception {
		private static final long serialVersionUID = 1L;
	}

	private static final String SHALLOW = "shallow "; //$NON-NLS-1$

	private static final String UNSHALLOW = "unshallow "; //$NON-NLS-1$

	/***
	 * implements reading shallow/unshallow lines that are sent as server
	 * response to client.<br/>
	 *
	 * @see <a href=
	 *      "https://github.com/git/git/blob/master/Documentation/technical/pack-protocol.txt#L261"
	 *      >https://github.com/git/git/blob/master/Documentation/technical/pack-protocol.txt#L261</a>
	 *
	 * @throws IOException
	 */
	private void handleShallowUnshallowLines()
			throws IOException {
		System.out.println(
				"handleShallowUnshallowLines: begin!");
		final File parentDirectory = this.local.getDirectory();
		if (parentDirectory == null) {
			throw new IOException("Repository.getDirectory() is null (Could not obtain $GITDIR)!");
		}
		System.out.println("handleShallowUnshallowLines-shallowFile='"
				+ parentDirectory.getAbsolutePath() + "'");
		final File shallowFile = new File(parentDirectory, "shallow");
		System.out.println("handleShallowUnshallowLines-shallowFile='"
				+ shallowFile.getAbsolutePath() + "'");
		LockFile shallowLockFile = new LockFile(shallowFile);
		if (!shallowLockFile.lock()) {
			throw new IOException("Could not obtain lock for $GITDIR/");
		}
		final List<ObjectId> shallowIds = new ArrayList<>();
		// TODO: read shallowIds from shallowFile

		String line = null;
		int length = -1;
		do {
			line = pckIn.readString();
			System.out
					.println("handleShallowUnshallowLines.line='" + line + "'");
			length = line.length();
			System.out.println(
					"handleShallowUnshallowLines.length='" + length + "'");
			if (length > 0) {
				if (line.startsWith(SHALLOW)) {
					final String id = line.substring(SHALLOW.length(),
							SHALLOW.length() + 40);
					final ObjectId objId = ObjectId.fromString(id);
					shallowIds.add(objId);
					System.out.println(
							"handleShallowUnshallowLines.id='" + id + "'");

				} else if (line.startsWith(UNSHALLOW)) {
					final String id = line.substring(UNSHALLOW.length(),
							UNSHALLOW.length() + 40);
					final ObjectId objId = ObjectId.fromString(id);
					shallowIds.remove(objId);
					System.out.println(
							"handleShallowUnshallowLines.id='" + id + "'");
				} else {
					throw new PackProtocolException(MessageFormat.format(
							JGitText.get().expectedShallowUnshallowGot, line));
				}
			}

		} while (length != 0);

		for (ObjectId id : shallowIds) {
			shallowLockFile.write(id);
		}
		shallowLockFile.commit();
		System.out.println("handleShallowUnshallowLines: end!");
	}

}
