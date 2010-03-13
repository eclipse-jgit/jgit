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

import static org.eclipse.jgit.transport.BasePackPushConnection.CAPABILITY_DELETE_REFS;
import static org.eclipse.jgit.transport.BasePackPushConnection.CAPABILITY_OFS_DELTA;
import static org.eclipse.jgit.transport.BasePackPushConnection.CAPABILITY_REPORT_STATUS;
import static org.eclipse.jgit.transport.BasePackPushConnection.CAPABILITY_SIDE_BAND_64K;
import static org.eclipse.jgit.transport.SideBandOutputStream.CH_DATA;
import static org.eclipse.jgit.transport.SideBandOutputStream.CH_PROGRESS;
import static org.eclipse.jgit.transport.SideBandOutputStream.MAX_BUF;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PackLock;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Config.SectionParser;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.util.io.InterruptTimer;
import org.eclipse.jgit.util.io.TimeoutInputStream;
import org.eclipse.jgit.util.io.TimeoutOutputStream;

/**
 * Implements the server side of a push connection, receiving objects.
 */
public class ReceivePack {
	/** Database we write the stored objects into. */
	private final Repository db;

	/** Revision traversal support over {@link #db}. */
	private final RevWalk walk;

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

	/** Should an incoming transfer validate objects? */
	private boolean checkReceivedObjects;

	/** Should an incoming transfer permit create requests? */
	private boolean allowCreates;

	/** Should an incoming transfer permit delete requests? */
	private boolean allowDeletes;

	/** Should an incoming transfer permit non-fast-forward requests? */
	private boolean allowNonFastForwards;

	private boolean allowOfsDelta;

	/** Identity to record action as within the reflog. */
	private PersonIdent refLogIdent;

	/** Filter used while advertising the refs to the client. */
	private RefFilter refFilter;

	/** Hook to validate the update commands before execution. */
	private PreReceiveHook preReceive;

	/** Hook to report on the commands after execution. */
	private PostReceiveHook postReceive;

	/** Timeout in seconds to wait for client interaction. */
	private int timeout;

	/** Timer to manage {@link #timeout}. */
	private InterruptTimer timer;

	private TimeoutInputStream timeoutIn;

	private InputStream rawIn;

	private OutputStream rawOut;

	private PacketLineIn pckIn;

	private PacketLineOut pckOut;

	private Writer msgs;

	private IndexPack ip;

	/** The refs we advertised as existing at the start of the connection. */
	private Map<String, Ref> refs;

	/** Capabilities requested by the client. */
	private Set<String> enabledCapablities;

	/** Commands to execute, as received by the client. */
	private List<ReceiveCommand> commands;

	/** Error to display instead of advertising the references. */
	private StringBuilder advertiseError;

	/** An exception caught while unpacking and fsck'ing the objects. */
	private Throwable unpackError;

	/** If {@link BasePackPushConnection#CAPABILITY_REPORT_STATUS} is enabled. */
	private boolean reportStatus;

	/** If {@link BasePackPushConnection#CAPABILITY_SIDE_BAND_64K} is enabled. */
	private boolean sideBand;

	/** Lock around the received pack file, while updating refs. */
	private PackLock packLock;

	private boolean needNewObjectIds;

	private boolean needBaseObjectIds;

	/**
	 * Create a new pack receive for an open repository.
	 *
	 * @param into
	 *            the destination repository.
	 */
	public ReceivePack(final Repository into) {
		db = into;
		walk = new RevWalk(db);

		final ReceiveConfig cfg = db.getConfig().get(ReceiveConfig.KEY);
		checkReceivedObjects = cfg.checkReceivedObjects;
		allowCreates = cfg.allowCreates;
		allowDeletes = cfg.allowDeletes;
		allowNonFastForwards = cfg.allowNonFastForwards;
		allowOfsDelta = cfg.allowOfsDelta;
		refFilter = RefFilter.DEFAULT;
		preReceive = PreReceiveHook.NULL;
		postReceive = PostReceiveHook.NULL;
	}

	private static class ReceiveConfig {
		static final SectionParser<ReceiveConfig> KEY = new SectionParser<ReceiveConfig>() {
			public ReceiveConfig parse(final Config cfg) {
				return new ReceiveConfig(cfg);
			}
		};

		final boolean checkReceivedObjects;

		final boolean allowCreates;

		final boolean allowDeletes;

		final boolean allowNonFastForwards;

		final boolean allowOfsDelta;

		ReceiveConfig(final Config config) {
			checkReceivedObjects = config.getBoolean("receive", "fsckobjects",
					false);
			allowCreates = true;
			allowDeletes = !config.getBoolean("receive", "denydeletes", false);
			allowNonFastForwards = !config.getBoolean("receive",
					"denynonfastforwards", false);
			allowOfsDelta = config.getBoolean("repack", "usedeltabaseoffset",
					true);
		}
	}

	/** @return the repository this receive completes into. */
	public final Repository getRepository() {
		return db;
	}

	/** @return the RevWalk instance used by this connection. */
	public final RevWalk getRevWalk() {
		return walk;
	}

	/** @return all refs which were advertised to the client. */
	public final Map<String, Ref> getAdvertisedRefs() {
		return refs;
	}

	/**
	 * Configure this receive pack instance to keep track of the objects assumed
	 * for delta bases.
	 * <p>
	 * By default a receive pack doesn't save the objects that were used as
	 * delta bases. Setting this flag to {@code true} will allow the caller to
	 * use {@link #getBaseObjectIds()} to retrieve that list.
	 *
	 * @param b {@code true} to enable keeping track of delta bases.
	 */
	public void setNeedBaseObjectIds(boolean b) {
		this.needBaseObjectIds = b;
	}

	/**
	 *  @return the set of objects the incoming pack assumed for delta purposes
	 */
	public final Set<ObjectId> getBaseObjectIds() {
		return ip.getBaseObjectIds();
	}

	/**
	 * Configure this receive pack instance to keep track of new objects.
	 * <p>
	 * By default a receive pack doesn't save the new objects that were created
	 * when it was instantiated. Setting this flag to {@code true} allows the
	 * caller to use {@link #getNewObjectIds()} to retrieve that list.
	 *
	 * @param b {@code true} to enable keeping track of new objects.
	 */
	public void setNeedNewObjectIds(boolean b) {
		this.needNewObjectIds = b;
	}

	/** @return the new objects that were sent by the user */
	public final Set<ObjectId> getNewObjectIds() {
		return ip.getNewObjectIds();
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
	 * @return true if this instance will verify received objects are formatted
	 *         correctly. Validating objects requires more CPU time on this side
	 *         of the connection.
	 */
	public boolean isCheckReceivedObjects() {
		return checkReceivedObjects;
	}

	/**
	 * @param check
	 *            true to enable checking received objects; false to assume all
	 *            received objects are valid.
	 */
	public void setCheckReceivedObjects(final boolean check) {
		checkReceivedObjects = check;
	}

	/** @return true if the client can request refs to be created. */
	public boolean isAllowCreates() {
		return allowCreates;
	}

	/**
	 * @param canCreate
	 *            true to permit create ref commands to be processed.
	 */
	public void setAllowCreates(final boolean canCreate) {
		allowCreates = canCreate;
	}

	/** @return true if the client can request refs to be deleted. */
	public boolean isAllowDeletes() {
		return allowDeletes;
	}

	/**
	 * @param canDelete
	 *            true to permit delete ref commands to be processed.
	 */
	public void setAllowDeletes(final boolean canDelete) {
		allowDeletes = canDelete;
	}

	/**
	 * @return true if the client can request non-fast-forward updates of a ref,
	 *         possibly making objects unreachable.
	 */
	public boolean isAllowNonFastForwards() {
		return allowNonFastForwards;
	}

	/**
	 * @param canRewind
	 *            true to permit the client to ask for non-fast-forward updates
	 *            of an existing ref.
	 */
	public void setAllowNonFastForwards(final boolean canRewind) {
		allowNonFastForwards = canRewind;
	}

	/** @return identity of the user making the changes in the reflog. */
	public PersonIdent getRefLogIdent() {
		return refLogIdent;
	}

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
	public void setRefLogIdent(final PersonIdent pi) {
		refLogIdent = pi;
	}

	/** @return the filter used while advertising the refs to the client */
	public RefFilter getRefFilter() {
		return refFilter;
	}

	/**
	 * Set the filter used while advertising the refs to the client.
	 * <p>
	 * Only refs allowed by this filter will be shown to the client.
	 * Clients may still attempt to create or update a reference hidden
	 * by the configured {@link RefFilter}. These attempts should be
	 * rejected by a matching {@link PreReceiveHook}.
	 *
	 * @param refFilter
	 *            the filter; may be null to show all refs.
	 */
	public void setRefFilter(final RefFilter refFilter) {
		this.refFilter = refFilter != null ? refFilter : RefFilter.DEFAULT;
	}

	/** @return get the hook invoked before updates occur. */
	public PreReceiveHook getPreReceiveHook() {
		return preReceive;
	}

	/**
	 * Set the hook which is invoked prior to commands being executed.
	 * <p>
	 * Only valid commands (those which have no obvious errors according to the
	 * received input and this instance's configuration) are passed into the
	 * hook. The hook may mark a command with a result of any value other than
	 * {@link Result#NOT_ATTEMPTED} to block its execution.
	 * <p>
	 * The hook may be called with an empty command collection if the current
	 * set is completely invalid.
	 *
	 * @param h
	 *            the hook instance; may be null to disable the hook.
	 */
	public void setPreReceiveHook(final PreReceiveHook h) {
		preReceive = h != null ? h : PreReceiveHook.NULL;
	}

	/** @return get the hook invoked after updates occur. */
	public PostReceiveHook getPostReceiveHook() {
		return postReceive;
	}

	/**
	 * Set the hook which is invoked after commands are executed.
	 * <p>
	 * Only successful commands (type is {@link Result#OK}) are passed into the
	 * hook. The hook may be called with an empty command collection if the
	 * current set all resulted in an error.
	 *
	 * @param h
	 *            the hook instance; may be null to disable the hook.
	 */
	public void setPostReceiveHook(final PostReceiveHook h) {
		postReceive = h != null ? h : PostReceiveHook.NULL;
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

	/** @return all of the command received by the current request. */
	public List<ReceiveCommand> getAllCommands() {
		return Collections.unmodifiableList(commands);
	}

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
	public void sendError(final String what) {
		if (refs == null) {
			if (advertiseError == null)
				advertiseError = new StringBuilder();
			advertiseError.append(what).append('\n');
		} else {
			try {
				if (msgs != null)
					msgs.write("error: " + what + "\n");
			} catch (IOException e) {
				// Ignore write failures.
			}
		}
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
	 */
	public void sendMessage(final String what) {
		try {
			if (msgs != null)
				msgs.write(what + "\n");
		} catch (IOException e) {
			// Ignore write failures.
		}
	}

	/**
	 * Execute the receive task on the socket.
	 *
	 * @param input
	 *            raw input to read client commands and pack data from. Caller
	 *            must ensure the input is buffered, otherwise read performance
	 *            may suffer.
	 * @param output
	 *            response back to the Git network client. Caller must ensure
	 *            the output is buffered, otherwise write performance may
	 *            suffer.
	 * @param messages
	 *            secondary "notice" channel to send additional messages out
	 *            through. When run over SSH this should be tied back to the
	 *            standard error channel of the command execution. For most
	 *            other network connections this should be null.
	 * @throws IOException
	 */
	public void receive(final InputStream input, final OutputStream output,
			final OutputStream messages) throws IOException {
		try {
			rawIn = input;
			rawOut = output;

			if (timeout > 0) {
				final Thread caller = Thread.currentThread();
				timer = new InterruptTimer(caller.getName() + "-Timer");
				timeoutIn = new TimeoutInputStream(rawIn, timer);
				TimeoutOutputStream o = new TimeoutOutputStream(rawOut, timer);
				timeoutIn.setTimeout(timeout * 1000);
				o.setTimeout(timeout * 1000);
				rawIn = timeoutIn;
				rawOut = o;
			}

			pckIn = new PacketLineIn(rawIn);
			pckOut = new PacketLineOut(rawOut);
			if (messages != null)
				msgs = new OutputStreamWriter(messages, Constants.CHARSET);

			enabledCapablities = new HashSet<String>();
			commands = new ArrayList<ReceiveCommand>();

			service();
		} finally {
			try {
				if (pckOut != null)
					pckOut.flush();
				if (msgs != null)
					msgs.flush();

				if (sideBand) {
					// If we are using side band, we need to send a final
					// flush-pkt to tell the remote peer the side band is
					// complete and it should stop decoding. We need to
					// use the original output stream as rawOut is now the
					// side band data channel.
					//
					new PacketLineOut(output).end();
				}
			} finally {
				unlockPack();
				timeoutIn = null;
				rawIn = null;
				rawOut = null;
				pckIn = null;
				pckOut = null;
				msgs = null;
				refs = null;
				enabledCapablities = null;
				commands = null;
				if (timer != null) {
					try {
						timer.terminate();
					} finally {
						timer = null;
					}
				}
			}
		}
	}

	private void service() throws IOException {
		if (biDirectionalPipe)
			sendAdvertisedRefs(new PacketLineOutRefAdvertiser(pckOut));
		else
			refs = refFilter.filter(db.getAllRefs());
		if (advertiseError != null)
			return;
		recvCommands();
		if (!commands.isEmpty()) {
			enableCapabilities();

			if (needPack()) {
				try {
					receivePack();
					if (isCheckReceivedObjects())
						checkConnectivity();
					unpackError = null;
				} catch (IOException err) {
					unpackError = err;
				} catch (RuntimeException err) {
					unpackError = err;
				} catch (Error err) {
					unpackError = err;
				}
			}

			if (unpackError == null) {
				validateCommands();
				executeCommands();
			}
			unlockPack();

			if (reportStatus) {
				sendStatusReport(true, new Reporter() {
					void sendString(final String s) throws IOException {
						pckOut.writeString(s + "\n");
					}
				});
				pckOut.end();
			} else if (msgs != null) {
				sendStatusReport(false, new Reporter() {
					void sendString(final String s) throws IOException {
						msgs.write(s + "\n");
					}
				});
			}

			postReceive.onPostReceive(this, filterCommands(Result.OK));
		}
	}

	private void unlockPack() {
		if (packLock != null) {
			packLock.unlock();
			packLock = null;
		}
	}

	/**
	 * Generate an advertisement of available refs and capabilities.
	 *
	 * @param adv
	 *            the advertisement formatter.
	 * @throws IOException
	 *             the formatter failed to write an advertisement.
	 */
	public void sendAdvertisedRefs(final RefAdvertiser adv) throws IOException {
		if (advertiseError != null) {
			adv.writeOne("ERR " + advertiseError);
			return;
		}

		final RevFlag advertised = walk.newFlag("ADVERTISED");
		adv.init(walk, advertised);
		adv.advertiseCapability(CAPABILITY_SIDE_BAND_64K);
		adv.advertiseCapability(CAPABILITY_DELETE_REFS);
		adv.advertiseCapability(CAPABILITY_REPORT_STATUS);
		if (allowOfsDelta)
			adv.advertiseCapability(CAPABILITY_OFS_DELTA);
		refs = refFilter.filter(db.getAllRefs());
		final Ref head = refs.remove(Constants.HEAD);
		adv.send(refs);
		if (head != null && !head.isSymbolic())
			adv.advertiseHave(head.getObjectId());
		adv.includeAdditionalHaves();
		if (adv.isEmpty())
			adv.advertiseId(ObjectId.zeroId(), "capabilities^{}");
		adv.end();
	}

	private void recvCommands() throws IOException {
		for (;;) {
			String line;
			try {
				line = pckIn.readStringRaw();
			} catch (EOFException eof) {
				if (commands.isEmpty())
					return;
				throw eof;
			}
			if (line == PacketLineIn.END)
				break;

			if (commands.isEmpty()) {
				final int nul = line.indexOf('\0');
				if (nul >= 0) {
					for (String c : line.substring(nul + 1).split(" "))
						enabledCapablities.add(c);
					line = line.substring(0, nul);
				}
			}

			if (line.length() < 83) {
				final String m = "error: invalid protocol: wanted 'old new ref'";
				sendError(m);
				throw new PackProtocolException(m);
			}

			final ObjectId oldId = ObjectId.fromString(line.substring(0, 40));
			final ObjectId newId = ObjectId.fromString(line.substring(41, 81));
			final String name = line.substring(82);
			final ReceiveCommand cmd = new ReceiveCommand(oldId, newId, name);
			if (name.equals(Constants.HEAD)) {
				cmd.setResult(Result.REJECTED_CURRENT_BRANCH);
			} else {
				cmd.setRef(refs.get(cmd.getRefName()));
			}
			commands.add(cmd);
		}
	}

	private void enableCapabilities() {
		reportStatus = enabledCapablities.contains(CAPABILITY_REPORT_STATUS);

		sideBand = enabledCapablities.contains(CAPABILITY_SIDE_BAND_64K);
		if (sideBand) {
			OutputStream out = rawOut;

			rawOut = new SideBandOutputStream(CH_DATA, MAX_BUF, out);
			pckOut = new PacketLineOut(rawOut);
			msgs = new OutputStreamWriter(new SideBandOutputStream(CH_PROGRESS,
					MAX_BUF, out), Constants.CHARSET);
		}
	}

	private boolean needPack() {
		for (final ReceiveCommand cmd : commands) {
			if (cmd.getType() != ReceiveCommand.Type.DELETE)
				return true;
		}
		return false;
	}

	private void receivePack() throws IOException {
		// It might take the client a while to pack the objects it needs
		// to send to us.  We should increase our timeout so we don't
		// abort while the client is computing.
		//
		if (timeoutIn != null)
			timeoutIn.setTimeout(10 * timeout * 1000);

		ip = IndexPack.create(db, rawIn);
		ip.setFixThin(true);
		ip.setNeedNewObjectIds(needNewObjectIds);
		ip.setNeedBaseObjectIds(needBaseObjectIds);
		ip.setObjectChecking(isCheckReceivedObjects());
		ip.index(NullProgressMonitor.INSTANCE);

		String lockMsg = "jgit receive-pack";
		if (getRefLogIdent() != null)
			lockMsg += " from " + getRefLogIdent().toExternalString();
		packLock = ip.renameAndOpenPack(lockMsg);

		if (timeoutIn != null)
			timeoutIn.setTimeout(timeout * 1000);
	}

	private void checkConnectivity() throws IOException {
		final ObjectWalk ow = new ObjectWalk(db);
		for (final ReceiveCommand cmd : commands) {
			if (cmd.getResult() != Result.NOT_ATTEMPTED)
				continue;
			if (cmd.getType() == ReceiveCommand.Type.DELETE)
				continue;
			ow.markStart(ow.parseAny(cmd.getNewId()));
		}
		for (final Ref ref : refs.values())
			ow.markUninteresting(ow.parseAny(ref.getObjectId()));
		ow.checkConnectivity();
	}

	private void validateCommands() {
		for (final ReceiveCommand cmd : commands) {
			final Ref ref = cmd.getRef();
			if (cmd.getResult() != Result.NOT_ATTEMPTED)
				continue;

			if (cmd.getType() == ReceiveCommand.Type.DELETE
					&& !isAllowDeletes()) {
				// Deletes are not supported on this repository.
				//
				cmd.setResult(Result.REJECTED_NODELETE);
				continue;
			}

			if (cmd.getType() == ReceiveCommand.Type.CREATE) {
				if (!isAllowCreates()) {
					cmd.setResult(Result.REJECTED_NOCREATE);
					continue;
				}

				if (ref != null && !isAllowNonFastForwards()) {
					// Creation over an existing ref is certainly not going
					// to be a fast-forward update. We can reject it early.
					//
					cmd.setResult(Result.REJECTED_NONFASTFORWARD);
					continue;
				}

				if (ref != null) {
					// A well behaved client shouldn't have sent us a
					// create command for a ref we advertised to it.
					//
					cmd.setResult(Result.REJECTED_OTHER_REASON, "ref exists");
					continue;
				}
			}

			if (cmd.getType() == ReceiveCommand.Type.DELETE && ref != null
					&& !ObjectId.zeroId().equals(cmd.getOldId())
					&& !ref.getObjectId().equals(cmd.getOldId())) {
				// Delete commands can be sent with the old id matching our
				// advertised value, *OR* with the old id being 0{40}. Any
				// other requested old id is invalid.
				//
				cmd.setResult(Result.REJECTED_OTHER_REASON,
						"invalid old id sent");
				continue;
			}

			if (cmd.getType() == ReceiveCommand.Type.UPDATE) {
				if (ref == null) {
					// The ref must have been advertised in order to be updated.
					//
					cmd.setResult(Result.REJECTED_OTHER_REASON, "no such ref");
					continue;
				}

				if (!ref.getObjectId().equals(cmd.getOldId())) {
					// A properly functioning client will send the same
					// object id we advertised.
					//
					cmd.setResult(Result.REJECTED_OTHER_REASON,
							"invalid old id sent");
					continue;
				}

				// Is this possibly a non-fast-forward style update?
				//
				RevObject oldObj, newObj;
				try {
					oldObj = walk.parseAny(cmd.getOldId());
				} catch (IOException e) {
					cmd.setResult(Result.REJECTED_MISSING_OBJECT, cmd
							.getOldId().name());
					continue;
				}

				try {
					newObj = walk.parseAny(cmd.getNewId());
				} catch (IOException e) {
					cmd.setResult(Result.REJECTED_MISSING_OBJECT, cmd
							.getNewId().name());
					continue;
				}

				if (oldObj instanceof RevCommit && newObj instanceof RevCommit) {
					try {
						if (!walk.isMergedInto((RevCommit) oldObj,
								(RevCommit) newObj)) {
							cmd
									.setType(ReceiveCommand.Type.UPDATE_NONFASTFORWARD);
						}
					} catch (MissingObjectException e) {
						cmd.setResult(Result.REJECTED_MISSING_OBJECT, e
								.getMessage());
					} catch (IOException e) {
						cmd.setResult(Result.REJECTED_OTHER_REASON);
					}
				} else {
					cmd.setType(ReceiveCommand.Type.UPDATE_NONFASTFORWARD);
				}
			}

			if (!cmd.getRefName().startsWith(Constants.R_REFS)
					|| !Repository.isValidRefName(cmd.getRefName())) {
				cmd.setResult(Result.REJECTED_OTHER_REASON, "funny refname");
			}
		}
	}

	private void executeCommands() {
		preReceive.onPreReceive(this, filterCommands(Result.NOT_ATTEMPTED));
		for (final ReceiveCommand cmd : filterCommands(Result.NOT_ATTEMPTED))
			execute(cmd);
	}

	private void execute(final ReceiveCommand cmd) {
		try {
			final RefUpdate ru = db.updateRef(cmd.getRefName());
			ru.setRefLogIdent(getRefLogIdent());
			switch (cmd.getType()) {
			case DELETE:
				if (!ObjectId.zeroId().equals(cmd.getOldId())) {
					// We can only do a CAS style delete if the client
					// didn't bork its delete request by sending the
					// wrong zero id rather than the advertised one.
					//
					ru.setExpectedOldObjectId(cmd.getOldId());
				}
				ru.setForceUpdate(true);
				status(cmd, ru.delete(walk));
				break;

			case CREATE:
			case UPDATE:
			case UPDATE_NONFASTFORWARD:
				ru.setForceUpdate(isAllowNonFastForwards());
				ru.setExpectedOldObjectId(cmd.getOldId());
				ru.setNewObjectId(cmd.getNewId());
				ru.setRefLogMessage("push", true);
				status(cmd, ru.update(walk));
				break;
			}
		} catch (IOException err) {
			cmd.setResult(Result.REJECTED_OTHER_REASON, "lock error: "
					+ err.getMessage());
		}
	}

	private void status(final ReceiveCommand cmd, final RefUpdate.Result result) {
		switch (result) {
		case NOT_ATTEMPTED:
			cmd.setResult(Result.NOT_ATTEMPTED);
			break;

		case LOCK_FAILURE:
		case IO_FAILURE:
			cmd.setResult(Result.LOCK_FAILURE);
			break;

		case NO_CHANGE:
		case NEW:
		case FORCED:
		case FAST_FORWARD:
			cmd.setResult(Result.OK);
			break;

		case REJECTED:
			cmd.setResult(Result.REJECTED_NONFASTFORWARD);
			break;

		case REJECTED_CURRENT_BRANCH:
			cmd.setResult(Result.REJECTED_CURRENT_BRANCH);
			break;

		default:
			cmd.setResult(Result.REJECTED_OTHER_REASON, result.name());
			break;
		}
	}

	private List<ReceiveCommand> filterCommands(final Result want) {
		final List<ReceiveCommand> r = new ArrayList<ReceiveCommand>(commands
				.size());
		for (final ReceiveCommand cmd : commands) {
			if (cmd.getResult() == want)
				r.add(cmd);
		}
		return r;
	}

	private void sendStatusReport(final boolean forClient, final Reporter out)
			throws IOException {
		if (unpackError != null) {
			out.sendString("unpack error " + unpackError.getMessage());
			if (forClient) {
				for (final ReceiveCommand cmd : commands) {
					out.sendString("ng " + cmd.getRefName()
							+ " n/a (unpacker error)");
				}
			}
			return;
		}

		if (forClient)
			out.sendString("unpack ok");
		for (final ReceiveCommand cmd : commands) {
			if (cmd.getResult() == Result.OK) {
				if (forClient)
					out.sendString("ok " + cmd.getRefName());
				continue;
			}

			final StringBuilder r = new StringBuilder();
			r.append("ng ");
			r.append(cmd.getRefName());
			r.append(" ");

			switch (cmd.getResult()) {
			case NOT_ATTEMPTED:
				r.append("server bug; ref not processed");
				break;

			case REJECTED_NOCREATE:
				r.append("creation prohibited");
				break;

			case REJECTED_NODELETE:
				r.append("deletion prohibited");
				break;

			case REJECTED_NONFASTFORWARD:
				r.append("non-fast forward");
				break;

			case REJECTED_CURRENT_BRANCH:
				r.append("branch is currently checked out");
				break;

			case REJECTED_MISSING_OBJECT:
				if (cmd.getMessage() == null)
					r.append("missing object(s)");
				else if (cmd.getMessage().length() == Constants.OBJECT_ID_STRING_LENGTH)
					r.append("object " + cmd.getMessage() + " missing");
				else
					r.append(cmd.getMessage());
				break;

			case REJECTED_OTHER_REASON:
				if (cmd.getMessage() == null)
					r.append("unspecified reason");
				else
					r.append(cmd.getMessage());
				break;

			case LOCK_FAILURE:
				r.append("failed to lock");
				break;

			case OK:
				// We shouldn't have reached this case (see 'ok' case above).
				continue;
			}
			out.sendString(r.toString());
		}
	}

	static abstract class Reporter {
		abstract void sendString(String s) throws IOException;
	}
}
