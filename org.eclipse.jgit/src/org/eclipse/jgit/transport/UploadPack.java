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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PackWriter;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevFlagSet;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.BasePackFetchConnection.MultiAck;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.util.io.InterruptTimer;
import org.eclipse.jgit.util.io.TimeoutInputStream;
import org.eclipse.jgit.util.io.TimeoutOutputStream;

/**
 * Implements the server side of a fetch connection, transmitting objects.
 */
public class UploadPack {
	static final String OPTION_INCLUDE_TAG = BasePackFetchConnection.OPTION_INCLUDE_TAG;

	static final String OPTION_MULTI_ACK = BasePackFetchConnection.OPTION_MULTI_ACK;

	static final String OPTION_MULTI_ACK_DETAILED = BasePackFetchConnection.OPTION_MULTI_ACK_DETAILED;

	static final String OPTION_THIN_PACK = BasePackFetchConnection.OPTION_THIN_PACK;

	static final String OPTION_SIDE_BAND = BasePackFetchConnection.OPTION_SIDE_BAND;

	static final String OPTION_SIDE_BAND_64K = BasePackFetchConnection.OPTION_SIDE_BAND_64K;

	static final String OPTION_OFS_DELTA = BasePackFetchConnection.OPTION_OFS_DELTA;

	static final String OPTION_NO_PROGRESS = BasePackFetchConnection.OPTION_NO_PROGRESS;

	/** Database we read the objects from. */
	private final Repository db;

	/** Revision traversal support over {@link #db}. */
	private final RevWalk walk;

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

	/** The refs we advertised as existing at the start of the connection. */
	private Map<String, Ref> refs;

	/** Filter used while advertising the refs to the client. */
	private RefFilter refFilter;

	/** Capabilities requested by the client. */
	private final Set<String> options = new HashSet<String>();

	/** Objects the client wants to obtain. */
	private final List<RevObject> wantAll = new ArrayList<RevObject>();

	/** Objects the client wants to obtain. */
	private final List<RevCommit> wantCommits = new ArrayList<RevCommit>();

	/** Objects on both sides, these don't have to be sent. */
	private final List<RevObject> commonBase = new ArrayList<RevObject>();

	/** null if {@link #commonBase} should be examined again. */
	private Boolean okToGiveUp;

	/** Marked on objects we sent in our advertisement list. */
	private final RevFlag ADVERTISED;

	/** Marked on objects the client has asked us to give them. */
	private final RevFlag WANT;

	/** Marked on objects both we and the client have. */
	private final RevFlag PEER_HAS;

	/** Marked on objects in {@link #commonBase}. */
	private final RevFlag COMMON;

	private final RevFlagSet SAVE;

	private MultiAck multiAck = MultiAck.OFF;

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

		ADVERTISED = walk.newFlag("ADVERTISED");
		WANT = walk.newFlag("WANT");
		PEER_HAS = walk.newFlag("PEER_HAS");
		COMMON = walk.newFlag("COMMON");
		walk.carry(PEER_HAS);

		SAVE = new RevFlagSet();
		SAVE.add(ADVERTISED);
		SAVE.add(WANT);
		SAVE.add(PEER_HAS);
		refFilter = RefFilter.DEFAULT;
	}

	/** @return the repository this upload is reading from. */
	public final Repository getRepository() {
		return db;
	}

	/** @return the RevWalk instance used by this connection. */
	public final RevWalk getRevWalk() {
		return walk;
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

	/** @return the filter used while advertising the refs to the client */
	public RefFilter getRefFilter() {
		return refFilter;
	}

	/**
	 * Set the filter used while advertising the refs to the client.
	 * <p>
	 * Only refs allowed by this filter will be sent to the client. This can
	 * be used by a server to restrict the list of references the client can
	 * obtain through clone or fetch, effectively limiting the access to only
	 * certain refs.
	 *
	 * @param refFilter
	 *            the filter; may be null to show all refs.
	 */
	public void setRefFilter(final RefFilter refFilter) {
		this.refFilter = refFilter != null ? refFilter : RefFilter.DEFAULT;
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

			if (timeout > 0) {
				final Thread caller = Thread.currentThread();
				timer = new InterruptTimer(caller.getName() + "-Timer");
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
			if (timer != null) {
				try {
					timer.terminate();
				} finally {
					timer = null;
				}
			}
		}
	}

	private void service() throws IOException {
		if (biDirectionalPipe)
			sendAdvertisedRefs(new PacketLineOutRefAdvertiser(pckOut));
		else {
			refs = refFilter.filter(db.getAllRefs());
			for (Ref r : refs.values()) {
				try {
					walk.parseAny(r.getObjectId()).add(ADVERTISED);
				} catch (IOException e) {
					// Skip missing/corrupt objects
				}
			}
		}

		recvWants();
		if (wantAll.isEmpty())
			return;

		if (options.contains(OPTION_MULTI_ACK_DETAILED))
			multiAck = MultiAck.DETAILED;
		else if (options.contains(OPTION_MULTI_ACK))
			multiAck = MultiAck.CONTINUE;
		else
			multiAck = MultiAck.OFF;

		if (negotiate())
			sendPack();
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
		adv.init(walk, ADVERTISED);
		adv.advertiseCapability(OPTION_INCLUDE_TAG);
		adv.advertiseCapability(OPTION_MULTI_ACK_DETAILED);
		adv.advertiseCapability(OPTION_MULTI_ACK);
		adv.advertiseCapability(OPTION_OFS_DELTA);
		adv.advertiseCapability(OPTION_SIDE_BAND);
		adv.advertiseCapability(OPTION_SIDE_BAND_64K);
		adv.advertiseCapability(OPTION_THIN_PACK);
		adv.advertiseCapability(OPTION_NO_PROGRESS);
		adv.setDerefTags(true);
		refs = refFilter.filter(db.getAllRefs());
		adv.send(refs);
		adv.end();
	}

	private void recvWants() throws IOException {
		boolean isFirst = true;
		for (;; isFirst = false) {
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
			if (!line.startsWith("want ") || line.length() < 45)
				throw new PackProtocolException("expected want; got " + line);

			if (isFirst && line.length() > 45) {
				String opt = line.substring(45);
				if (opt.startsWith(" "))
					opt = opt.substring(1);
				for (String c : opt.split(" "))
					options.add(c);
				line = line.substring(0, 45);
			}

			final ObjectId id = ObjectId.fromString(line.substring(5));
			final RevObject o;
			try {
				o = walk.parseAny(id);
			} catch (IOException e) {
				throw new PackProtocolException(id.name() + " not valid", e);
			}
			if (!o.has(ADVERTISED))
				throw new PackProtocolException(id.name() + " not valid");
			want(o);
		}
	}

	private void want(RevObject o) {
		if (!o.has(WANT)) {
			o.add(WANT);
			wantAll.add(o);

			if (o instanceof RevCommit)
				wantCommits.add((RevCommit) o);

			else if (o instanceof RevTag) {
				do {
					o = ((RevTag) o).getObject();
				} while (o instanceof RevTag);
				if (o instanceof RevCommit)
					want(o);
			}
		}
	}

	private boolean negotiate() throws IOException {
		ObjectId last = ObjectId.zeroId();
		for (;;) {
			String line;
			try {
				line = pckIn.readString();
			} catch (EOFException eof) {
				throw eof;
			}

			if (line == PacketLineIn.END) {
				if (commonBase.isEmpty() || multiAck != MultiAck.OFF)
					pckOut.writeString("NAK\n");
				pckOut.flush();
				if (!biDirectionalPipe)
					return false;

			} else if (line.startsWith("have ") && line.length() == 45) {
				final ObjectId id = ObjectId.fromString(line.substring(5));
				if (matchHave(id)) {
					// Both sides have the same object; let the client know.
					//
					last = id;
					switch (multiAck) {
					case OFF:
						if (commonBase.size() == 1)
							pckOut.writeString("ACK " + id.name() + "\n");
						break;
					case CONTINUE:
						pckOut.writeString("ACK " + id.name() + " continue\n");
						break;
					case DETAILED:
						pckOut.writeString("ACK " + id.name() + " common\n");
						break;
					}
				} else if (okToGiveUp()) {
					// They have this object; we don't.
					//
					switch (multiAck) {
					case OFF:
						break;
					case CONTINUE:
						pckOut.writeString("ACK " + id.name() + " continue\n");
						break;
					case DETAILED:
						pckOut.writeString("ACK " + id.name() + " ready\n");
						break;
					}
				}

			} else if (line.equals("done")) {
				if (commonBase.isEmpty())
					pckOut.writeString("NAK\n");

				else if (multiAck != MultiAck.OFF)
					pckOut.writeString("ACK " + last.name() + "\n");

				return true;

			} else {
				throw new PackProtocolException("expected have; got " + line);
			}
		}
	}

	private boolean matchHave(final ObjectId id) {
		final RevObject o;
		try {
			o = walk.parseAny(id);
		} catch (IOException err) {
			return false;
		}

		if (!o.has(PEER_HAS)) {
			o.add(PEER_HAS);
			if (o instanceof RevCommit)
				((RevCommit) o).carry(PEER_HAS);
			addCommonBase(o);
		}
		return true;
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
			for (final Iterator<RevCommit> i = wantCommits.iterator(); i
					.hasNext();) {
				final RevCommit want = i.next();
				if (wantSatisfied(want))
					i.remove();
			}
		} catch (IOException e) {
			throw new PackProtocolException("internal revision error", e);
		}
		return wantCommits.isEmpty();
	}

	private boolean wantSatisfied(final RevCommit want) throws IOException {
		walk.resetRetain(SAVE);
		walk.markStart(want);
		for (;;) {
			final RevCommit c = walk.next();
			if (c == null)
				break;
			if (c.has(PEER_HAS)) {
				addCommonBase(c);
				return true;
			}
		}
		return false;
	}

	private void sendPack() throws IOException {
		final boolean thin = options.contains(OPTION_THIN_PACK);
		final boolean progress = !options.contains(OPTION_NO_PROGRESS);
		final boolean sideband = options.contains(OPTION_SIDE_BAND)
				|| options.contains(OPTION_SIDE_BAND_64K);

		ProgressMonitor pm = NullProgressMonitor.INSTANCE;
		OutputStream packOut = rawOut;

		if (sideband) {
			int bufsz = SideBandOutputStream.SMALL_BUF;
			if (options.contains(OPTION_SIDE_BAND_64K))
				bufsz = SideBandOutputStream.MAX_BUF;

			packOut = new SideBandOutputStream(SideBandOutputStream.CH_DATA,
					bufsz, rawOut);
			if (progress)
				pm = new SideBandProgressMonitor(new SideBandOutputStream(
						SideBandOutputStream.CH_PROGRESS, bufsz, rawOut));
		}

		final PackWriter pw;
		pw = new PackWriter(db, pm, NullProgressMonitor.INSTANCE);
		pw.setDeltaBaseAsOffset(options.contains(OPTION_OFS_DELTA));
		pw.setThin(thin);
		pw.preparePack(wantAll, commonBase);
		if (options.contains(OPTION_INCLUDE_TAG)) {
			for (final Ref r : refs.values()) {
				final RevObject o;
				try {
					o = walk.parseAny(r.getObjectId());
				} catch (IOException e) {
					continue;
				}
				if (o.has(WANT) || !(o instanceof RevTag))
					continue;
				final RevTag t = (RevTag) o;
				if (!pw.willInclude(t) && pw.willInclude(t.getObject()))
					pw.addObject(t);
			}
		}
		pw.writePack(packOut);
		packOut.flush();

		if (sideband)
			pckOut.end();
	}
}
