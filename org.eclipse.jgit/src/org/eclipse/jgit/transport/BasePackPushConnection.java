/*
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_ATOMIC;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.TooLargeObjectInPackException;
import org.eclipse.jgit.errors.TooLargePackException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.pack.PackWriter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;

/**
 * Push implementation using the native Git pack transfer service.
 * <p>
 * This is the canonical implementation for transferring objects to the remote
 * repository from the local repository by talking to the 'git-receive-pack'
 * service. Objects are packed on the local side into a pack file and then sent
 * to the remote repository.
 * <p>
 * This connection requires only a bi-directional pipe or socket, and thus is
 * easily wrapped up into a local process pipe, anonymous TCP socket, or a
 * command executed through an SSH tunnel.
 * <p>
 * This implementation honors
 * {@link org.eclipse.jgit.transport.Transport#isPushThin()} option.
 * <p>
 * Concrete implementations should just call
 * {@link #init(java.io.InputStream, java.io.OutputStream)} and
 * {@link #readAdvertisedRefs()} methods in constructor or before any use. They
 * should also handle resources releasing in {@link #close()} method if needed.
 */
public abstract class BasePackPushConnection extends BasePackConnection implements
		PushConnection {
	/**
	 * The client expects a status report after the server processes the pack.
	 * @since 2.0
	 */
	public static final String CAPABILITY_REPORT_STATUS = GitProtocolConstants.CAPABILITY_REPORT_STATUS;

	/**
	 * The server supports deleting refs.
	 * @since 2.0
	 */
	public static final String CAPABILITY_DELETE_REFS = GitProtocolConstants.CAPABILITY_DELETE_REFS;

	/**
	 * The server supports packs with OFS deltas.
	 * @since 2.0
	 */
	public static final String CAPABILITY_OFS_DELTA = GitProtocolConstants.CAPABILITY_OFS_DELTA;

	/**
	 * The client supports using the 64K side-band for progress messages.
	 * @since 2.0
	 */
	public static final String CAPABILITY_SIDE_BAND_64K = GitProtocolConstants.CAPABILITY_SIDE_BAND_64K;

	/**
	 * The server supports the receiving of push options.
	 * @since 4.5
	 */
	public static final String CAPABILITY_PUSH_OPTIONS = GitProtocolConstants.CAPABILITY_PUSH_OPTIONS;

	private final boolean thinPack;
	private final boolean atomic;

	/** A list of option strings associated with this push. */
	private List<String> pushOptions;

	private boolean capableAtomic;
	private boolean capableDeleteRefs;
	private boolean capableReport;
	private boolean capableSideBand;
	private boolean capableOfsDelta;
	private boolean capablePushOptions;

	private boolean sentCommand;
	private boolean writePack;

	/** Time in milliseconds spent transferring the pack data. */
	private long packTransferTime;

	/**
	 * Create a new connection to push using the native git transport.
	 *
	 * @param packTransport
	 *            the transport.
	 */
	public BasePackPushConnection(PackTransport packTransport) {
		super(packTransport);
		thinPack = transport.isPushThin();
		atomic = transport.isPushAtomic();
		pushOptions = transport.getPushOptions();
	}

	/** {@inheritDoc} */
	@Override
	public void push(final ProgressMonitor monitor,
			final Map<String, RemoteRefUpdate> refUpdates)
			throws TransportException {
		push(monitor, refUpdates, null);
	}

	/** {@inheritDoc} */
	@Override
	public void push(final ProgressMonitor monitor,
			final Map<String, RemoteRefUpdate> refUpdates, OutputStream outputStream)
			throws TransportException {
		markStartedOperation();
		doPush(monitor, refUpdates, outputStream);
	}

	/** {@inheritDoc} */
	@Override
	protected TransportException noRepository(Throwable cause) {
		// Sadly we cannot tell the "invalid URI" case from "push not allowed".
		// Opening a fetch connection can help us tell the difference, as any
		// useful repository is going to support fetch if it also would allow
		// push. So if fetch throws NoRemoteRepositoryException we know the
		// URI is wrong. Otherwise we can correctly state push isn't allowed
		// as the fetch connection opened successfully.
		//
		TransportException te;
		try {
			transport.openFetch().close();
			te = new TransportException(uri, JGitText.get().pushNotPermitted);
		} catch (NoRemoteRepositoryException e) {
			// Fetch concluded the repository doesn't exist.
			te = e;
		} catch (NotSupportedException | TransportException e) {
			te = new TransportException(uri, JGitText.get().pushNotPermitted, e);
		}
		te.addSuppressed(cause);
		return te;
	}

	/**
	 * Push one or more objects and update the remote repository.
	 *
	 * @param monitor
	 *            progress monitor to receive status updates.
	 * @param refUpdates
	 *            update commands to be applied to the remote repository.
	 * @param outputStream
	 *            output stream to write sideband messages to
	 * @throws org.eclipse.jgit.errors.TransportException
	 *             if any exception occurs.
	 * @since 3.0
	 */
	protected void doPush(final ProgressMonitor monitor,
			final Map<String, RemoteRefUpdate> refUpdates,
			OutputStream outputStream) throws TransportException {
		try {
			writeCommands(refUpdates.values(), monitor, outputStream);

			if (pushOptions != null && capablePushOptions)
				transmitOptions();
			if (writePack)
				writePack(refUpdates, monitor);
			if (sentCommand) {
				if (capableReport)
					readStatusReport(refUpdates);
				if (capableSideBand) {
					// Ensure the data channel is at EOF, so we know we have
					// read all side-band data from all channels and have a
					// complete copy of the messages (if any) buffered from
					// the other data channels.
					//
					int b = in.read();
					if (0 <= b)
						throw new TransportException(uri, MessageFormat.format(
								JGitText.get().expectedEOFReceived,
								Character.valueOf((char) b)));
				}
			}
		} catch (TransportException e) {
			throw e;
		} catch (Exception e) {
			throw new TransportException(uri, e.getMessage(), e);
		} finally {
			close();
		}
	}

	private void writeCommands(final Collection<RemoteRefUpdate> refUpdates,
			final ProgressMonitor monitor, OutputStream outputStream) throws IOException {
		final String capabilities = enableCapabilities(monitor, outputStream);
		if (atomic && !capableAtomic) {
			throw new TransportException(uri,
					JGitText.get().atomicPushNotSupported);
		}

		if (pushOptions != null && !capablePushOptions) {
			throw new TransportException(uri,
					MessageFormat.format(JGitText.get().pushOptionsNotSupported,
							pushOptions.toString()));
		}

		for (RemoteRefUpdate rru : refUpdates) {
			if (!capableDeleteRefs && rru.isDelete()) {
				rru.setStatus(Status.REJECTED_NODELETE);
				continue;
			}

			final StringBuilder sb = new StringBuilder();
			ObjectId oldId = rru.getExpectedOldObjectId();
			if (oldId == null) {
				final Ref advertised = getRef(rru.getRemoteName());
				oldId = advertised != null ? advertised.getObjectId() : null;
				if (oldId == null) {
					oldId = ObjectId.zeroId();
				}
			}
			sb.append(oldId.name());
			sb.append(' ');
			sb.append(rru.getNewObjectId().name());
			sb.append(' ');
			sb.append(rru.getRemoteName());
			if (!sentCommand) {
				sentCommand = true;
				sb.append(capabilities);
			}

			pckOut.writeString(sb.toString());
			rru.setStatus(Status.AWAITING_REPORT);
			if (!rru.isDelete())
				writePack = true;
		}

		if (monitor.isCancelled())
			throw new TransportException(uri, JGitText.get().pushCancelled);
		pckOut.end();
		outNeedsEnd = false;
	}

	private void transmitOptions() throws IOException {
		for (String pushOption : pushOptions) {
			pckOut.writeString(pushOption);
		}

		pckOut.end();
	}

	private String enableCapabilities(final ProgressMonitor monitor,
			OutputStream outputStream) {
		final StringBuilder line = new StringBuilder();
		if (atomic)
			capableAtomic = wantCapability(line, CAPABILITY_ATOMIC);
		capableReport = wantCapability(line, CAPABILITY_REPORT_STATUS);
		capableDeleteRefs = wantCapability(line, CAPABILITY_DELETE_REFS);
		capableOfsDelta = wantCapability(line, CAPABILITY_OFS_DELTA);

		if (pushOptions != null) {
			capablePushOptions = wantCapability(line, CAPABILITY_PUSH_OPTIONS);
		}

		capableSideBand = wantCapability(line, CAPABILITY_SIDE_BAND_64K);
		if (capableSideBand) {
			in = new SideBandInputStream(in, monitor, getMessageWriter(),
					outputStream);
			pckIn = new PacketLineIn(in);
		}
		addUserAgentCapability(line);

		if (line.length() > 0)
			line.setCharAt(0, '\0');
		return line.toString();
	}

	private void writePack(final Map<String, RemoteRefUpdate> refUpdates,
			final ProgressMonitor monitor) throws IOException {
		Set<ObjectId> remoteObjects = new HashSet<>();
		Set<ObjectId> newObjects = new HashSet<>();

		try (PackWriter writer = new PackWriter(transport.getPackConfig(),
				local.newObjectReader())) {

			for (Ref r : getRefs()) {
				// only add objects that we actually have
				ObjectId oid = r.getObjectId();
				if (local.getObjectDatabase().has(oid))
					remoteObjects.add(oid);
			}
			remoteObjects.addAll(additionalHaves);
			for (RemoteRefUpdate r : refUpdates.values()) {
				if (!ObjectId.zeroId().equals(r.getNewObjectId()))
					newObjects.add(r.getNewObjectId());
			}

			writer.setIndexDisabled(true);
			writer.setUseCachedPacks(true);
			writer.setUseBitmaps(true);
			writer.setThin(thinPack);
			writer.setReuseValidatingObjects(false);
			writer.setDeltaBaseAsOffset(capableOfsDelta);
			writer.preparePack(monitor, newObjects, remoteObjects);

			OutputStream packOut = out;
			if (capableSideBand) {
				packOut = new CheckingSideBandOutputStream(in, out);
			}
			writer.writePack(monitor, monitor, packOut);

			packTransferTime = writer.getStatistics().getTimeWriting();
		}
	}

	private void readStatusReport(Map<String, RemoteRefUpdate> refUpdates)
			throws IOException {
		final String unpackLine = readStringLongTimeout();
		if (!unpackLine.startsWith("unpack ")) //$NON-NLS-1$
			throw new PackProtocolException(uri, MessageFormat
					.format(JGitText.get().unexpectedReportLine, unpackLine));
		final String unpackStatus = unpackLine.substring("unpack ".length()); //$NON-NLS-1$
		if (unpackStatus.startsWith("error Pack exceeds the limit of")) {//$NON-NLS-1$
			throw new TooLargePackException(uri,
					unpackStatus.substring("error ".length())); //$NON-NLS-1$
		} else if (unpackStatus.startsWith("error Object too large")) {//$NON-NLS-1$
			throw new TooLargeObjectInPackException(uri,
					unpackStatus.substring("error ".length())); //$NON-NLS-1$
		} else if (!unpackStatus.equals("ok")) { //$NON-NLS-1$
			throw new TransportException(uri, MessageFormat.format(
					JGitText.get().errorOccurredDuringUnpackingOnTheRemoteEnd, unpackStatus));
		}

		for (String refLine : pckIn.readStrings()) {
			boolean ok = false;
			int refNameEnd = -1;
			if (refLine.startsWith("ok ")) { //$NON-NLS-1$
				ok = true;
				refNameEnd = refLine.length();
			} else if (refLine.startsWith("ng ")) { //$NON-NLS-1$
				ok = false;
				refNameEnd = refLine.indexOf(' ', 3);
			}
			if (refNameEnd == -1)
				throw new PackProtocolException(MessageFormat.format(JGitText.get().unexpectedReportLine2
						, uri, refLine));
			final String refName = refLine.substring(3, refNameEnd);
			final String message = (ok ? null : refLine
					.substring(refNameEnd + 1));

			final RemoteRefUpdate rru = refUpdates.get(refName);
			if (rru == null)
				throw new PackProtocolException(MessageFormat.format(JGitText.get().unexpectedRefReport, uri, refName));
			if (ok) {
				rru.setStatus(Status.OK);
			} else {
				rru.setStatus(Status.REJECTED_OTHER_REASON);
				rru.setMessage(message);
			}
		}
		for (RemoteRefUpdate rru : refUpdates.values()) {
			if (rru.getStatus() == Status.AWAITING_REPORT)
				throw new PackProtocolException(MessageFormat.format(
						JGitText.get().expectedReportForRefNotReceived , uri, rru.getRemoteName()));
		}
	}

	private String readStringLongTimeout() throws IOException {
		if (timeoutIn == null)
			return pckIn.readString();

		// The remote side may need a lot of time to choke down the pack
		// we just sent them. There may be many deltas that need to be
		// resolved by the remote. Its hard to say how long the other
		// end is going to be silent. Taking 10x the configured timeout
		// or the time spent transferring the pack, whichever is larger,
		// gives the other side some reasonable window to process the data,
		// but this is just a wild guess.
		//
		final int oldTimeout = timeoutIn.getTimeout();
		final int sendTime = (int) Math.min(packTransferTime, 28800000L);
		try {
			int timeout = 10 * Math.max(sendTime, oldTimeout);
			timeoutIn.setTimeout((timeout < 0) ? Integer.MAX_VALUE : timeout);
			return pckIn.readString();
		} finally {
			timeoutIn.setTimeout(oldTimeout);
		}
	}

	/**
	 * Gets the list of option strings associated with this push.
	 *
	 * @return pushOptions
	 * @since 4.5
	 */
	public List<String> getPushOptions() {
		return pushOptions;
	}

	private static class CheckingSideBandOutputStream extends OutputStream {
		private final InputStream in;
		private final OutputStream out;

		CheckingSideBandOutputStream(InputStream in, OutputStream out) {
			this.in = in;
			this.out = out;
		}

		@Override
		public void write(int b) throws IOException {
			write(new byte[] { (byte) b });
		}

		@Override
		public void write(byte[] buf, int ptr, int cnt) throws IOException {
			try {
				out.write(buf, ptr, cnt);
			} catch (IOException e) {
				throw checkError(e);
			}
		}

		@Override
		public void flush() throws IOException {
			try {
				out.flush();
			} catch (IOException e) {
				throw checkError(e);
			}
		}

		private IOException checkError(IOException e1) {
			try {
				in.read();
			} catch (TransportException e2) {
				return e2;
			} catch (IOException e2) {
				return e1;
			}
			return e1;
		}
	}
}
