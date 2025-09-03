/*
 * Copyright (C) 2008-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_ATOMIC;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_DELETE_REFS;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_OFS_DELTA;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_PUSH_OPTIONS;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_QUIET;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_REPORT_STATUS;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_SIDE_BAND_64K;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_AGENT;
import static org.eclipse.jgit.transport.GitProtocolConstants.PACKET_ERR;
import static org.eclipse.jgit.transport.GitProtocolConstants.PACKET_SHALLOW;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_SESSION_ID;
import static org.eclipse.jgit.transport.SideBandOutputStream.CH_DATA;
import static org.eclipse.jgit.transport.SideBandOutputStream.CH_ERROR;
import static org.eclipse.jgit.transport.SideBandOutputStream.CH_PROGRESS;
import static org.eclipse.jgit.transport.SideBandOutputStream.MAX_BUF;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.TooLargePackException;
import org.eclipse.jgit.errors.UnpackException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.submodule.SubmoduleValidator;
import org.eclipse.jgit.internal.submodule.SubmoduleValidator.SubmoduleValidationException;
import org.eclipse.jgit.internal.transport.connectivity.FullConnectivityChecker;
import org.eclipse.jgit.internal.transport.parser.FirstCommand;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.GitmoduleEntry;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ConnectivityChecker.ConnectivityCheckInfo;
import org.eclipse.jgit.transport.PacketLineIn.InputOverLimitIOException;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.util.io.InterruptTimer;
import org.eclipse.jgit.util.io.LimitedInputStream;
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

	/** Expecting data after the pack footer */
	private boolean expectDataAfterPackFooter;

	/** Should an incoming transfer validate objects? */
	private ObjectChecker objectChecker;

	/** Should an incoming transfer permit create requests? */
	private boolean allowCreates;

	/** Should an incoming transfer permit delete requests? */
	private boolean allowAnyDeletes;

	private boolean allowBranchDeletes;

	/** Should an incoming transfer permit non-fast-forward requests? */
	private boolean allowNonFastForwards;

	/** Should an incoming transfer permit push options? **/
	private boolean allowPushOptions;

	/**
	 * Should the requested ref updates be performed as a single atomic
	 * transaction?
	 */
	private boolean atomic;

	private boolean allowOfsDelta;

	private boolean allowQuiet = true;

	/** Should the server advertise and accept the session-id capability. */
	private boolean allowReceiveClientSID;

	/** Identity to record action as within the reflog. */
	private PersonIdent refLogIdent;

	/** Hook used while advertising the refs to the client. */
	private AdvertiseRefsHook advertiseRefsHook;

	/** Filter used while advertising the refs to the client. */
	private RefFilter refFilter;

	/** Timeout in seconds to wait for client interaction. */
	private int timeout;

	/** Timer to manage {@link #timeout}. */
	private InterruptTimer timer;

	private TimeoutInputStream timeoutIn;

	// Original stream passed to init(), since rawOut may be wrapped in a
	// sideband.
	private OutputStream origOut;

	/** Raw input stream. */
	private InputStream rawIn;

	/** Raw output stream. */
	private OutputStream rawOut;

	/** Optional message output stream. */
	private OutputStream msgOut;

	private SideBandOutputStream errOut;

	/** Packet line input stream around {@link #rawIn}. */
	private PacketLineIn pckIn;

	/** Packet line output stream around {@link #rawOut}. */
	private PacketLineOut pckOut;

	private final MessageOutputWrapper msgOutWrapper = new MessageOutputWrapper();

	private PackParser parser;

	/** The refs we advertised as existing at the start of the connection. */
	private Map<String, Ref> refs;

	/** All SHA-1s shown to the client, which can be possible edges. */
	private Set<ObjectId> advertisedHaves;

	/** Capabilities requested by the client. */
	private Map<String, String> enabledCapabilities;

	/** Session ID sent from the client. Null if none was received. */
	private String clientSID;

	String userAgent;

	private Set<ObjectId> clientShallowCommits;

	private List<ReceiveCommand> commands;

	private long maxCommandBytes;

	private long maxDiscardBytes;

	private StringBuilder advertiseError;

	/**
	 * If {@link BasePackPushConnection#CAPABILITY_SIDE_BAND_64K} is enabled.
	 */
	private boolean sideBand;

	private boolean quiet;

	/** Lock around the received pack file, while updating refs. */
	private PackLock packLock;

	private boolean checkReferencedAreReachable;

	/** Git object size limit */
	private long maxObjectSizeLimit;

	/** Total pack size limit */
	private long maxPackSizeLimit = -1;

	/** The size of the received pack, including index size */
	private Long packSize;

	private PushCertificateParser pushCertificateParser;

	private SignedPushConfig signedPushConfig;

	private PushCertificate pushCert;

	private ReceivedPackStatistics stats;

	/**
	 * Connectivity checker to use.
	 * @since 5.7
	 */
	protected ConnectivityChecker connectivityChecker = new FullConnectivityChecker();

	/** Hook to validate the update commands before execution. */
	private PreReceiveHook preReceive;

	private ReceiveCommandErrorHandler receiveCommandErrorHandler = new ReceiveCommandErrorHandler() {
		// Use the default implementation.
	};

	private UnpackErrorHandler unpackErrorHandler = new DefaultUnpackErrorHandler();

	/** Hook to report on the commands after execution. */
	private PostReceiveHook postReceive;

	/** If {@link BasePackPushConnection#CAPABILITY_REPORT_STATUS} is enabled. */
	private boolean reportStatus;

	/** Whether the client intends to use push options. */
	private boolean usePushOptions;
	private List<String> pushOptions;

	/**
	 * Create a new pack receive for an open repository.
	 *
	 * @param into
	 *            the destination repository.
	 */
	public ReceivePack(Repository into) {
		db = into;
		walk = new RevWalk(db);
		walk.setRetainBody(false);

		TransferConfig tc = db.getConfig().get(TransferConfig.KEY);
		objectChecker = tc.newReceiveObjectChecker();
		allowReceiveClientSID = tc.isAllowReceiveClientSID();

		ReceiveConfig rc = db.getConfig().get(ReceiveConfig::new);
		allowCreates = rc.allowCreates;
		allowAnyDeletes = true;
		allowBranchDeletes = rc.allowDeletes;
		allowNonFastForwards = rc.allowNonFastForwards;
		allowOfsDelta = rc.allowOfsDelta;
		allowPushOptions = rc.allowPushOptions;
		maxCommandBytes = rc.maxCommandBytes;
		maxDiscardBytes = rc.maxDiscardBytes;
		advertiseRefsHook = AdvertiseRefsHook.DEFAULT;
		refFilter = RefFilter.DEFAULT;
		advertisedHaves = new HashSet<>();
		clientShallowCommits = new HashSet<>();
		signedPushConfig = rc.signedPush;
		preReceive = PreReceiveHook.NULL;
		postReceive = PostReceiveHook.NULL;
	}

	/** Configuration for receive operations. */
	private static class ReceiveConfig {
		final boolean allowCreates;

		final boolean allowDeletes;

		final boolean allowNonFastForwards;

		final boolean allowOfsDelta;

		final boolean allowPushOptions;

		final long maxCommandBytes;

		final long maxDiscardBytes;

		final SignedPushConfig signedPush;

		ReceiveConfig(Config config) {
			allowCreates = true;
			allowDeletes = !config.getBoolean("receive", "denydeletes", false); //$NON-NLS-1$ //$NON-NLS-2$
			allowNonFastForwards = !config.getBoolean("receive", //$NON-NLS-1$
					"denynonfastforwards", false); //$NON-NLS-1$
			allowOfsDelta = config.getBoolean("repack", "usedeltabaseoffset", //$NON-NLS-1$ //$NON-NLS-2$
					true);
			allowPushOptions = config.getBoolean("receive", "pushoptions", //$NON-NLS-1$ //$NON-NLS-2$
					false);
			maxCommandBytes = config.getLong("receive", //$NON-NLS-1$
					"maxCommandBytes", //$NON-NLS-1$
					3 << 20);
			maxDiscardBytes = config.getLong("receive", //$NON-NLS-1$
					"maxCommandDiscardBytes", //$NON-NLS-1$
					-1);
			signedPush = SignedPushConfig.KEY.parse(config);
		}
	}

	/**
	 * Output stream that wraps the current {@link #msgOut}.
	 * <p>
	 * We don't want to expose {@link #msgOut} directly because it can change
	 * several times over the course of a session.
	 */
	class MessageOutputWrapper extends OutputStream {
		@Override
		public void write(int ch) {
			if (msgOut != null) {
				try {
					msgOut.write(ch);
				} catch (IOException e) {
					// Ignore write failures.
				}
			}
		}

		@Override
		public void write(byte[] b, int off, int len) {
			if (msgOut != null) {
				try {
					msgOut.write(b, off, len);
				} catch (IOException e) {
					// Ignore write failures.
				}
			}
		}

		@Override
		public void write(byte[] b) {
			write(b, 0, b.length);
		}

		@Override
		public void flush() {
			if (msgOut != null) {
				try {
					msgOut.flush();
				} catch (IOException e) {
					// Ignore write failures.
				}
			}
		}
	}

	/**
	 * Get the repository this receive completes into.
	 *
	 * @return the repository this receive completes into.
	 */
	public Repository getRepository() {
		return db;
	}

	/**
	 * Get the RevWalk instance used by this connection.
	 *
	 * @return the RevWalk instance used by this connection.
	 */
	public RevWalk getRevWalk() {
		return walk;
	}

	/**
	 * Get refs which were advertised to the client.
	 *
	 * @return all refs which were advertised to the client, or null if
	 *         {@link #setAdvertisedRefs(Map, Set)} has not been called yet.
	 */
	public Map<String, Ref> getAdvertisedRefs() {
		return refs;
	}

	/**
	 * Set the refs advertised by this ReceivePack.
	 * <p>
	 * Intended to be called from a
	 * {@link org.eclipse.jgit.transport.PreReceiveHook}.
	 *
	 * @param allRefs
	 *            explicit set of references to claim as advertised by this
	 *            ReceivePack instance. This overrides any references that may
	 *            exist in the source repository. The map is passed to the
	 *            configured {@link #getRefFilter()}. If null, assumes all refs
	 *            were advertised.
	 * @param additionalHaves
	 *            explicit set of additional haves to claim as advertised. If
	 *            null, assumes the default set of additional haves from the
	 *            repository.
	 * @throws IOException
	 *             if an IO error occurred
	 */
	public void setAdvertisedRefs(Map<String, Ref> allRefs,
			Set<ObjectId> additionalHaves) throws IOException {
		refs = allRefs != null ? allRefs : getAllRefs();
		refs = refFilter.filter(refs);
		advertisedHaves.clear();

		Ref head = refs.get(HEAD);
		if (head != null && head.isSymbolic()) {
			refs.remove(HEAD);
		}

		for (Ref ref : refs.values()) {
			if (ref.getObjectId() != null) {
				advertisedHaves.add(ref.getObjectId());
			}
		}
		if (additionalHaves != null) {
			advertisedHaves.addAll(additionalHaves);
		} else {
			advertisedHaves.addAll(db.getAdditionalHaves());
		}
	}

	/**
	 * Get objects advertised to the client.
	 *
	 * @return the set of objects advertised to the as present in this
	 *         repository, or null if {@link #setAdvertisedRefs(Map, Set)} has
	 *         not been called yet.
	 */
	public final Set<ObjectId> getAdvertisedObjects() {
		return advertisedHaves;
	}

	/**
	 * Whether this instance will validate all referenced, but not supplied by
	 * the client, objects are reachable from another reference.
	 *
	 * @return true if this instance will validate all referenced, but not
	 *         supplied by the client, objects are reachable from another
	 *         reference.
	 */
	public boolean isCheckReferencedObjectsAreReachable() {
		return checkReferencedAreReachable;
	}

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
	 * been hidden from them via the configured
	 * {@link org.eclipse.jgit.transport.AdvertiseRefsHook} or
	 * {@link org.eclipse.jgit.transport.RefFilter}.
	 * <p>
	 * Enabling this feature may imply at least some, if not all, of the same
	 * functionality performed by {@link #setCheckReceivedObjects(boolean)}.
	 * Applications are encouraged to enable both features, if desired.
	 *
	 * @param b
	 *            {@code true} to enable the additional check.
	 */
	public void setCheckReferencedObjectsAreReachable(boolean b) {
		this.checkReferencedAreReachable = b;
	}

	/**
	 * Whether this class expects a bi-directional pipe opened between the
	 * client and itself.
	 *
	 * @return true if this class expects a bi-directional pipe opened between
	 *         the client and itself. The default is true.
	 */
	public boolean isBiDirectionalPipe() {
		return biDirectionalPipe;
	}

	/**
	 * Whether this class will assume the socket is a fully bidirectional pipe
	 * between the two peers and takes advantage of that by first transmitting
	 * the known refs, then waiting to read commands.
	 *
	 * @param twoWay
	 *            if true, this class will assume the socket is a fully
	 *            bidirectional pipe between the two peers and takes advantage
	 *            of that by first transmitting the known refs, then waiting to
	 *            read commands. If false, this class assumes it must read the
	 *            commands before writing output and does not perform the
	 *            initial advertising.
	 */
	public void setBiDirectionalPipe(boolean twoWay) {
		biDirectionalPipe = twoWay;
	}

	/**
	 * Whether there is data expected after the pack footer.
	 *
	 * @return {@code true} if there is data expected after the pack footer.
	 */
	public boolean isExpectDataAfterPackFooter() {
		return expectDataAfterPackFooter;
	}

	/**
	 * Whether there is additional data in InputStream after pack.
	 *
	 * @param e
	 *            {@code true} if there is additional data in InputStream after
	 *            pack.
	 */
	public void setExpectDataAfterPackFooter(boolean e) {
		expectDataAfterPackFooter = e;
	}

	/**
	 * Whether this instance will verify received objects are formatted
	 * correctly.
	 *
	 * @return {@code true} if this instance will verify received objects are
	 *         formatted correctly. Validating objects requires more CPU time on
	 *         this side of the connection.
	 */
	public boolean isCheckReceivedObjects() {
		return objectChecker != null;
	}

	/**
	 * Whether to enable checking received objects
	 *
	 * @param check
	 *            {@code true} to enable checking received objects; false to
	 *            assume all received objects are valid.
	 * @see #setObjectChecker(ObjectChecker)
	 */
	public void setCheckReceivedObjects(boolean check) {
		if (check && objectChecker == null)
			setObjectChecker(new ObjectChecker());
		else if (!check && objectChecker != null)
			setObjectChecker(null);
	}

	/**
	 * Set the object checking instance to verify each received object with
	 *
	 * @param impl
	 *            if non-null the object checking instance to verify each
	 *            received object with; null to disable object checking.
	 * @since 3.4
	 */
	public void setObjectChecker(ObjectChecker impl) {
		objectChecker = impl;
	}

	/**
	 * Whether the client can request refs to be created.
	 *
	 * @return {@code true} if the client can request refs to be created.
	 */
	public boolean isAllowCreates() {
		return allowCreates;
	}

	/**
	 * Whether to permit create ref commands to be processed.
	 *
	 * @param canCreate
	 *            {@code true} to permit create ref commands to be processed.
	 */
	public void setAllowCreates(boolean canCreate) {
		allowCreates = canCreate;
	}

	/**
	 * Whether the client can request refs to be deleted.
	 *
	 * @return {@code true} if the client can request refs to be deleted.
	 */
	public boolean isAllowDeletes() {
		return allowAnyDeletes;
	}

	/**
	 * Whether to permit delete ref commands to be processed.
	 *
	 * @param canDelete
	 *            {@code true} to permit delete ref commands to be processed.
	 */
	public void setAllowDeletes(boolean canDelete) {
		allowAnyDeletes = canDelete;
	}

	/**
	 * Whether the client can delete from {@code refs/heads/}.
	 *
	 * @return {@code true} if the client can delete from {@code refs/heads/}.
	 * @since 3.6
	 */
	public boolean isAllowBranchDeletes() {
		return allowBranchDeletes;
	}

	/**
	 * Configure whether to permit deletion of branches from the
	 * {@code refs/heads/} namespace.
	 *
	 * @param canDelete
	 *            {@code true} to permit deletion of branches from the
	 *            {@code refs/heads/} namespace.
	 * @since 3.6
	 */
	public void setAllowBranchDeletes(boolean canDelete) {
		allowBranchDeletes = canDelete;
	}

	/**
	 * Whether the client can request non-fast-forward updates of a ref,
	 * possibly making objects unreachable.
	 *
	 * @return {@code true} if the client can request non-fast-forward updates
	 *         of a ref, possibly making objects unreachable.
	 */
	public boolean isAllowNonFastForwards() {
		return allowNonFastForwards;
	}

	/**
	 * Configure whether to permit the client to ask for non-fast-forward
	 * updates of an existing ref.
	 *
	 * @param canRewind
	 *            {@code true} to permit the client to ask for non-fast-forward
	 *            updates of an existing ref.
	 */
	public void setAllowNonFastForwards(boolean canRewind) {
		allowNonFastForwards = canRewind;
	}

	/**
	 * Whether the client's commands should be performed as a single atomic
	 * transaction.
	 *
	 * @return {@code true} if the client's commands should be performed as a
	 *         single atomic transaction.
	 * @since 4.4
	 */
	public boolean isAtomic() {
		return atomic;
	}

	/**
	 * Configure whether to perform the client's commands as a single atomic
	 * transaction.
	 *
	 * @param atomic
	 *            {@code true} to perform the client's commands as a single
	 *            atomic transaction.
	 * @since 4.4
	 */
	public void setAtomic(boolean atomic) {
		this.atomic = atomic;
	}

	/**
	 * Get identity of the user making the changes in the reflog.
	 *
	 * @return identity of the user making the changes in the reflog.
	 */
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
	public void setRefLogIdent(PersonIdent pi) {
		refLogIdent = pi;
	}

	/**
	 * Get the hook used while advertising the refs to the client
	 *
	 * @return the hook used while advertising the refs to the client
	 */
	public AdvertiseRefsHook getAdvertiseRefsHook() {
		return advertiseRefsHook;
	}

	/**
	 * Get the filter used while advertising the refs to the client
	 *
	 * @return the filter used while advertising the refs to the client
	 */
	public RefFilter getRefFilter() {
		return refFilter;
	}

	/**
	 * Set the hook used while advertising the refs to the client.
	 * <p>
	 * If the {@link org.eclipse.jgit.transport.AdvertiseRefsHook} chooses to
	 * call {@link #setAdvertisedRefs(Map,Set)}, only refs set by this hook
	 * <em>and</em> selected by the {@link org.eclipse.jgit.transport.RefFilter}
	 * will be shown to the client. Clients may still attempt to create or
	 * update a reference not advertised by the configured
	 * {@link org.eclipse.jgit.transport.AdvertiseRefsHook}. These attempts
	 * should be rejected by a matching
	 * {@link org.eclipse.jgit.transport.PreReceiveHook}.
	 *
	 * @param advertiseRefsHook
	 *            the hook; may be null to show all refs.
	 */
	public void setAdvertiseRefsHook(AdvertiseRefsHook advertiseRefsHook) {
		if (advertiseRefsHook != null)
			this.advertiseRefsHook = advertiseRefsHook;
		else
			this.advertiseRefsHook = AdvertiseRefsHook.DEFAULT;
	}

	/**
	 * Set the filter used while advertising the refs to the client.
	 * <p>
	 * Only refs allowed by this filter will be shown to the client. The filter
	 * is run against the refs specified by the
	 * {@link org.eclipse.jgit.transport.AdvertiseRefsHook} (if applicable).
	 *
	 * @param refFilter
	 *            the filter; may be null to show all refs.
	 */
	public void setRefFilter(RefFilter refFilter) {
		this.refFilter = refFilter != null ? refFilter : RefFilter.DEFAULT;
	}

	/**
	 * Get timeout (in seconds) before aborting an IO operation.
	 *
	 * @return timeout (in seconds) before aborting an IO operation.
	 */
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
	public void setTimeout(int seconds) {
		timeout = seconds;
	}

	/**
	 * Set the maximum number of command bytes to read from the client.
	 *
	 * @param limit
	 *            command limit in bytes; if 0 there is no limit.
	 * @since 4.7
	 */
	public void setMaxCommandBytes(long limit) {
		maxCommandBytes = limit;
	}

	/**
	 * Set the maximum number of command bytes to discard from the client.
	 * <p>
	 * Discarding remaining bytes allows this instance to consume the rest of
	 * the command block and send a human readable over-limit error via the
	 * side-band channel. If the client sends an excessive number of bytes this
	 * limit kicks in and the instance disconnects, resulting in a non-specific
	 * 'pipe closed', 'end of stream', or similar generic error at the client.
	 * <p>
	 * When the limit is set to {@code -1} the implementation will default to
	 * the larger of {@code 3 * maxCommandBytes} or {@code 3 MiB}.
	 *
	 * @param limit
	 *            discard limit in bytes; if 0 there is no limit; if -1 the
	 *            implementation tries to set a reasonable default.
	 * @since 4.7
	 */
	public void setMaxCommandDiscardBytes(long limit) {
		maxDiscardBytes = limit;
	}

	/**
	 * Set the maximum allowed Git object size.
	 * <p>
	 * If an object is larger than the given size the pack-parsing will throw an
	 * exception aborting the receive-pack operation.
	 *
	 * @param limit
	 *            the Git object size limit. If zero then there is not limit.
	 */
	public void setMaxObjectSizeLimit(long limit) {
		maxObjectSizeLimit = limit;
	}

	/**
	 * Set the maximum allowed pack size.
	 * <p>
	 * A pack exceeding this size will be rejected.
	 *
	 * @param limit
	 *            the pack size limit, in bytes
	 * @since 3.3
	 */
	public void setMaxPackSizeLimit(long limit) {
		if (limit < 0)
			throw new IllegalArgumentException(
					MessageFormat.format(JGitText.get().receivePackInvalidLimit,
							Long.valueOf(limit)));
		maxPackSizeLimit = limit;
	}

	/**
	 * Check whether the client expects a side-band stream.
	 *
	 * @return true if the client has advertised a side-band capability, false
	 *         otherwise.
	 * @throws org.eclipse.jgit.transport.RequestNotYetReadException
	 *             if the client's request has not yet been read from the wire,
	 *             so we do not know if they expect side-band. Note that the
	 *             client may have already written the request, it just has not
	 *             been read.
	 */
	public boolean isSideBand() throws RequestNotYetReadException {
		checkRequestWasRead();
		return enabledCapabilities.containsKey(CAPABILITY_SIDE_BAND_64K);
	}

	/**
	 * Whether clients may request avoiding noisy progress messages.
	 *
	 * @return true if clients may request avoiding noisy progress messages.
	 * @since 4.0
	 */
	public boolean isAllowQuiet() {
		return allowQuiet;
	}

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
	public void setAllowQuiet(boolean allow) {
		allowQuiet = allow;
	}

	/**
	 * Whether the server supports receiving push options.
	 *
	 * @return true if the server supports receiving push options.
	 * @since 4.5
	 */
	public boolean isAllowPushOptions() {
		return allowPushOptions;
	}

	/**
	 * Configure if the server supports receiving push options.
	 *
	 * @param allow
	 *            true to optionally accept option strings from the client.
	 * @since 4.5
	 */
	public void setAllowPushOptions(boolean allow) {
		allowPushOptions = allow;
	}

	/**
	 * True if the client wants less verbose output.
	 *
	 * @return true if the client has requested the server to be less verbose.
	 * @throws org.eclipse.jgit.transport.RequestNotYetReadException
	 *             if the client's request has not yet been read from the wire,
	 *             so we do not know if they expect side-band. Note that the
	 *             client may have already written the request, it just has not
	 *             been read.
	 * @since 4.0
	 */
	public boolean isQuiet() throws RequestNotYetReadException {
		checkRequestWasRead();
		return quiet;
	}

	/**
	 * Set the configuration for push certificate verification.
	 *
	 * @param cfg
	 *            new configuration; if this object is null or its
	 *            {@link SignedPushConfig#getCertNonceSeed()} is null, push
	 *            certificate verification will be disabled.
	 * @since 4.1
	 */
	public void setSignedPushConfig(SignedPushConfig cfg) {
		signedPushConfig = cfg;
	}

	private PushCertificateParser getPushCertificateParser() {
		if (pushCertificateParser == null) {
			pushCertificateParser = new PushCertificateParser(db,
					signedPushConfig);
		}
		return pushCertificateParser;
	}

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
	public String getPeerUserAgent() {
		if (enabledCapabilities == null || enabledCapabilities.isEmpty()) {
			return userAgent;
		}

		return enabledCapabilities.getOrDefault(OPTION_AGENT, userAgent);
	}

	/**
	 * Get all of the command received by the current request.
	 *
	 * @return all of the command received by the current request.
	 */
	public List<ReceiveCommand> getAllCommands() {
		return Collections.unmodifiableList(commands);
	}

	/**
	 * Set an error handler for {@link ReceiveCommand}.
	 *
	 * @param receiveCommandErrorHandler
	 *            the error handler
	 * @since 5.7
	 */
	public void setReceiveCommandErrorHandler(
			ReceiveCommandErrorHandler receiveCommandErrorHandler) {
		this.receiveCommandErrorHandler = receiveCommandErrorHandler;
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
	 * {@link org.eclipse.jgit.transport.PreReceiveHook}s should always try to
	 * use
	 * {@link org.eclipse.jgit.transport.ReceiveCommand#setResult(Result, String)}
	 * with a result status of
	 * {@link org.eclipse.jgit.transport.ReceiveCommand.Result#REJECTED_OTHER_REASON}
	 * to indicate any reasons for rejecting an update. Messages attached to a
	 * command are much more likely to be returned to the client.
	 *
	 * @param what
	 *            string describing the problem identified by the hook. The
	 *            string must not end with an LF, and must not contain an LF.
	 */
	public void sendError(String what) {
		if (refs == null) {
			if (advertiseError == null)
				advertiseError = new StringBuilder();
			advertiseError.append(what).append('\n');
		} else {
			msgOutWrapper.write(Constants.encode("error: " + what + "\n")); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private void fatalError(String msg) {
		if (errOut != null) {
			try {
				errOut.write(Constants.encode(msg));
				errOut.flush();
			} catch (IOException e) {
				// Ignore write failures
			}
		} else {
			sendError(msg);
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
	public void sendMessage(String what) {
		msgOutWrapper.write(Constants.encode(what + "\n")); //$NON-NLS-1$
	}

	/**
	 * Get an underlying stream for sending messages to the client.
	 *
	 * @return an underlying stream for sending messages to the client.
	 */
	public OutputStream getMessageOutputStream() {
		return msgOutWrapper;
	}

	/**
	 * Get whether or not a pack has been received.
	 *
	 * This can be called before calling {@link #getPackSize()} to avoid causing
	 * {@code IllegalStateException} when the pack size was not set because no
	 * pack was received.
	 *
	 * @return true if a pack has been received.
	 * @since 5.6
	 */
	public boolean hasReceivedPack() {
		return packSize != null;
	}

	/**
	 * Get the size of the received pack file including the index size.
	 *
	 * This can only be called if the pack is already received.
	 *
	 * @return the size of the received pack including index size
	 * @throws java.lang.IllegalStateException
	 *             if called before the pack has been received
	 * @since 3.3
	 */
	public long getPackSize() {
		if (packSize != null)
			return packSize.longValue();
		throw new IllegalStateException(JGitText.get().packSizeNotSetYet);
	}

	/**
	 * Get the commits from the client's shallow file.
	 *
	 * @return if the client is a shallow repository, the list of edge commits
	 *         that define the client's shallow boundary. Empty set if the
	 *         client is earlier than Git 1.9, or is a full clone.
	 */
	private Set<ObjectId> getClientShallowCommits() {
		return clientShallowCommits;
	}

	/**
	 * Whether any commands to be executed have been read.
	 *
	 * @return {@code true} if any commands to be executed have been read.
	 */
	private boolean hasCommands() {
		return !commands.isEmpty();
	}

	/**
	 * Whether an error occurred that should be advertised.
	 *
	 * @return true if an error occurred that should be advertised.
	 */
	private boolean hasError() {
		return advertiseError != null;
	}

	/**
	 * Initialize the instance with the given streams.
	 *
	 * Visible for out-of-tree subclasses (e.g. tests that need to set the
	 * streams without going through the {@link #service()} method).
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
	 */
	protected void init(final InputStream input, final OutputStream output,
			final OutputStream messages) {
		origOut = output;
		rawIn = input;
		rawOut = output;
		msgOut = messages;

		if (timeout > 0) {
			final Thread caller = Thread.currentThread();
			timer = new InterruptTimer(caller.getName() + "-Timer"); //$NON-NLS-1$
			timeoutIn = new TimeoutInputStream(rawIn, timer);
			TimeoutOutputStream o = new TimeoutOutputStream(rawOut, timer);
			timeoutIn.setTimeout(timeout * 1000);
			o.setTimeout(timeout * 1000);
			rawIn = timeoutIn;
			rawOut = o;
		}

		pckIn = new PacketLineIn(rawIn);
		pckOut = new PacketLineOut(rawOut);
		pckOut.setFlushOnEnd(false);

		enabledCapabilities = new HashMap<>();
		commands = new ArrayList<>();
	}

	/**
	 * Get advertised refs, or the default if not explicitly advertised.
	 *
	 * @return advertised refs, or the default if not explicitly advertised.
	 * @throws IOException
	 *             if an IO error occurred
	 */
	private Map<String, Ref> getAdvertisedOrDefaultRefs() throws IOException {
		if (refs == null)
			setAdvertisedRefs(null, null);
		return refs;
	}

	/**
	 * Receive a pack from the stream and check connectivity if necessary.
	 *
	 * Visible for out-of-tree subclasses. Subclasses overriding this method
	 * should invoke this implementation, as it alters the instance state (e.g.
	 * it reads the pack from the input and parses it before running the
	 * connectivity checks).
	 *
	 * @throws java.io.IOException
	 *             an error occurred during unpacking or connectivity checking.
	 * @throws LargeObjectException
	 *             an large object needs to be opened for the check.
	 * @throws SubmoduleValidationException
	 *             fails to validate the submodule.
	 */
	protected void receivePackAndCheckConnectivity() throws IOException,
			LargeObjectException, SubmoduleValidationException {
		receivePack();
		if (needCheckConnectivity()) {
			checkSubmodules();
			checkConnectivity();
		}
		parser = null;
	}

	/**
	 * Unlock the pack written by this object.
	 *
	 * @throws java.io.IOException
	 *             the pack could not be unlocked.
	 */
	private void unlockPack() throws IOException {
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
	 * @throws java.io.IOException
	 *             the formatter failed to write an advertisement.
	 * @throws org.eclipse.jgit.transport.ServiceMayNotContinueException
	 *             the hook denied advertisement.
	 */
	public void sendAdvertisedRefs(RefAdvertiser adv)
			throws IOException, ServiceMayNotContinueException {
		if (advertiseError != null) {
			adv.writeOne(PACKET_ERR + advertiseError);
			return;
		}

		try {
			advertiseRefsHook.advertiseRefs(this);
		} catch (ServiceMayNotContinueException fail) {
			if (fail.getMessage() != null) {
				adv.writeOne(PACKET_ERR + fail.getMessage());
				fail.setOutput();
			}
			throw fail;
		}

		adv.init(db);
		adv.advertiseCapability(CAPABILITY_SIDE_BAND_64K);
		adv.advertiseCapability(CAPABILITY_DELETE_REFS);
		adv.advertiseCapability(CAPABILITY_REPORT_STATUS);
		if (allowReceiveClientSID) {
			adv.advertiseCapability(OPTION_SESSION_ID);
		}
		if (allowQuiet) {
			adv.advertiseCapability(CAPABILITY_QUIET);
		}
		String nonce = getPushCertificateParser().getAdvertiseNonce();
		if (nonce != null) {
			adv.advertiseCapability(nonce);
		}
		if (db.getRefDatabase().performsAtomicTransactions()) {
			adv.advertiseCapability(CAPABILITY_ATOMIC);
		}
		if (allowOfsDelta) {
			adv.advertiseCapability(CAPABILITY_OFS_DELTA);
		}
		if (allowPushOptions) {
			adv.advertiseCapability(CAPABILITY_PUSH_OPTIONS);
		}
		adv.advertiseCapability(OPTION_AGENT, UserAgent.get());
		adv.send(getAdvertisedOrDefaultRefs().values());
		for (ObjectId obj : advertisedHaves) {
			adv.advertiseHave(obj);
		}
		if (adv.isEmpty()) {
			adv.advertiseId(ObjectId.zeroId(), "capabilities^{}"); //$NON-NLS-1$
		}
		adv.end();
	}

	/**
	 * Returns the statistics on the received pack if available. This should be
	 * called after {@link #receivePack} is called.
	 *
	 * @return ReceivedPackStatistics
	 * @since 4.6
	 */
	@Nullable
	public ReceivedPackStatistics getReceivedPackStatistics() {
		return stats;
	}

	/**
	 * Extract the full list of refs from the ref-db.
	 *
	 * @return Map of all refname/ref
	 */
	private Map<String, Ref> getAllRefs() {
		try {
			return db.getRefDatabase().getRefs().stream()
					.collect(Collectors.toMap(Ref::getName,
							Function.identity()));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Receive a list of commands from the input.
	 *
	 * @throws IOException
	 *             if an IO error occurred
	 */
	private void recvCommands() throws IOException {
		PacketLineIn pck = maxCommandBytes > 0
				? new PacketLineIn(rawIn, maxCommandBytes)
				: pckIn;
		PushCertificateParser certParser = getPushCertificateParser();
		boolean firstPkt = true;
		try {
			for (;;) {
				String line;
				try {
					line = pck.readString();
				} catch (EOFException eof) {
					if (commands.isEmpty())
						return;
					throw eof;
				}
				if (PacketLineIn.isEnd(line)) {
					break;
				}

				int len = PACKET_SHALLOW.length() + 40;
				if (line.length() >= len && line.startsWith(PACKET_SHALLOW)) {
					parseShallow(line.substring(PACKET_SHALLOW.length(), len));
					continue;
				}

				if (firstPkt) {
					firstPkt = false;
					FirstCommand firstLine = FirstCommand.fromLine(line);
					enabledCapabilities = firstLine.getCapabilities();
					line = firstLine.getLine();
					enableCapabilities();

					if (line.equals(GitProtocolConstants.OPTION_PUSH_CERT)) {
						certParser.receiveHeader(pck, !isBiDirectionalPipe());
						continue;
					}
				}

				if (line.equals(PushCertificateParser.BEGIN_SIGNATURE)) {
					certParser.receiveSignature(pck);
					continue;
				}

				ReceiveCommand cmd = parseCommand(line);
				if (cmd.getRefName().equals(Constants.HEAD)) {
					cmd.setResult(Result.REJECTED_CURRENT_BRANCH);
				} else {
					cmd.setRef(refs.get(cmd.getRefName()));
				}
				commands.add(cmd);
				if (certParser.enabled()) {
					certParser.addCommand(cmd);
				}
			}
			pushCert = certParser.build();
			if (hasCommands()) {
				readPostCommands(pck);
			}
		} catch (Throwable t) {
			discardCommands();
			throw t;
		}
	}

	private void discardCommands() {
		if (sideBand) {
			long max = maxDiscardBytes;
			if (max < 0) {
				max = Math.max(3 * maxCommandBytes, 3L << 20);
			}
			try {
				new PacketLineIn(rawIn, max).discardUntilEnd();
			} catch (IOException e) {
				// Ignore read failures attempting to discard.
			}
		}
	}

	private void parseShallow(String idStr) throws PackProtocolException {
		ObjectId id;
		try {
			id = ObjectId.fromString(idStr);
		} catch (InvalidObjectIdException e) {
			throw new PackProtocolException(e.getMessage(), e);
		}
		clientShallowCommits.add(id);
	}

	/**
	 * @param in
	 *            request stream.
	 * @throws IOException
	 *             request line cannot be read.
	 */
	void readPostCommands(PacketLineIn in) throws IOException {
		if (usePushOptions) {
			pushOptions = new ArrayList<>(4);
			for (;;) {
				String option = in.readString();
				if (PacketLineIn.isEnd(option)) {
					break;
				}
				pushOptions.add(option);
			}
		}
	}

	/**
	 * Enable capabilities based on a previously read capabilities line.
	 */
	private void enableCapabilities() {
		reportStatus = isCapabilityEnabled(CAPABILITY_REPORT_STATUS);
		usePushOptions = isCapabilityEnabled(CAPABILITY_PUSH_OPTIONS);
		sideBand = isCapabilityEnabled(CAPABILITY_SIDE_BAND_64K);
		quiet = allowQuiet && isCapabilityEnabled(CAPABILITY_QUIET);

		clientSID = enabledCapabilities.get(OPTION_SESSION_ID);

		if (sideBand) {
			OutputStream out = rawOut;

			rawOut = new SideBandOutputStream(CH_DATA, MAX_BUF, out);
			msgOut = new SideBandOutputStream(CH_PROGRESS, MAX_BUF, out);
			errOut = new SideBandOutputStream(CH_ERROR, MAX_BUF, out);

			pckOut = new PacketLineOut(rawOut);
			pckOut.setFlushOnEnd(false);
		}
	}

	/**
	 * Check if the peer requested a capability.
	 *
	 * @param name
	 *            protocol name identifying the capability.
	 * @return true if the peer requested the capability to be enabled.
	 */
	private boolean isCapabilityEnabled(String name) {
		return enabledCapabilities.containsKey(name);
	}

	private void checkRequestWasRead() {
		if (enabledCapabilities == null)
			throw new RequestNotYetReadException();
	}

	/**
	 * Whether a pack is expected based on the list of commands.
	 *
	 * @return {@code true} if a pack is expected based on the list of commands.
	 */
	private boolean needPack() {
		for (ReceiveCommand cmd : commands) {
			if (cmd.getType() != ReceiveCommand.Type.DELETE)
				return true;
		}
		return false;
	}

	/**
	 * Receive a pack from the input and store it in the repository.
	 *
	 * @throws IOException
	 *             an error occurred reading or indexing the pack.
	 */
	private void receivePack() throws IOException {
		// It might take the client a while to pack the objects it needs
		// to send to us. We should increase our timeout so we don't
		// abort while the client is computing.
		//
		if (timeoutIn != null)
			timeoutIn.setTimeout(10 * timeout * 1000);

		ProgressMonitor receiving = NullProgressMonitor.INSTANCE;
		ProgressMonitor resolving = NullProgressMonitor.INSTANCE;
		if (sideBand && !quiet)
			resolving = new SideBandProgressMonitor(msgOut);

		try (ObjectInserter ins = db.newObjectInserter()) {
			String lockMsg = "jgit receive-pack"; //$NON-NLS-1$
			if (getRefLogIdent() != null)
				lockMsg += " from " + getRefLogIdent().toExternalString(); //$NON-NLS-1$

			parser = ins.newPackParser(packInputStream());
			parser.setAllowThin(true);
			parser.setNeedNewObjectIds(checkReferencedAreReachable);
			parser.setNeedBaseObjectIds(checkReferencedAreReachable);
			parser.setCheckEofAfterPackFooter(!biDirectionalPipe
					&& !isExpectDataAfterPackFooter());
			parser.setExpectDataAfterPackFooter(isExpectDataAfterPackFooter());
			parser.setObjectChecker(objectChecker);
			parser.setLockMessage(lockMsg);
			parser.setMaxObjectSizeLimit(maxObjectSizeLimit);
			packLock = parser.parse(receiving, resolving);
			packSize = Long.valueOf(parser.getPackSize());
			stats = parser.getReceivedPackStatistics();
			ins.flush();
		}

		if (timeoutIn != null)
			timeoutIn.setTimeout(timeout * 1000);
	}

	private InputStream packInputStream() {
		InputStream packIn = rawIn;
		if (maxPackSizeLimit >= 0) {
			packIn = new LimitedInputStream(packIn, maxPackSizeLimit) {
				@Override
				protected void limitExceeded() throws TooLargePackException {
					throw new TooLargePackException(limit);
				}
			};
		}
		return packIn;
	}

	private boolean needCheckConnectivity() {
		return isCheckReceivedObjects()
				|| isCheckReferencedObjectsAreReachable()
				|| !getClientShallowCommits().isEmpty();
	}

	private void checkSubmodules() throws IOException, LargeObjectException,
			SubmoduleValidationException {
		ObjectDatabase odb = db.getObjectDatabase();
		if (objectChecker == null) {
			return;
		}
		for (GitmoduleEntry entry : objectChecker.getGitsubmodules()) {
			AnyObjectId blobId = entry.getBlobId();
			ObjectLoader blob = odb.open(blobId, Constants.OBJ_BLOB);

			SubmoduleValidator.assertValidGitModulesFile(
					new String(blob.getBytes(), UTF_8));
		}
	}

	private void checkConnectivity() throws IOException {
		ProgressMonitor checking = NullProgressMonitor.INSTANCE;
		if (sideBand && !quiet) {
			SideBandProgressMonitor m = new SideBandProgressMonitor(msgOut);
			m.setDelayStart(750, TimeUnit.MILLISECONDS);
			checking = m;
		}

		connectivityChecker.checkConnectivity(createConnectivityCheckInfo(),
				advertisedHaves, checking);
	}

	private ConnectivityCheckInfo createConnectivityCheckInfo() {
		ConnectivityCheckInfo info = new ConnectivityCheckInfo();
		info.setCheckObjects(checkReferencedAreReachable);
		info.setCommands(getAllCommands());
		info.setRepository(db);
		info.setParser(parser);
		info.setWalk(walk);
		return info;
	}

	/**
	 * Validate the command list.
	 */
	private void validateCommands() {
		for (ReceiveCommand cmd : commands) {
			final Ref ref = cmd.getRef();
			if (cmd.getResult() != Result.NOT_ATTEMPTED)
				continue;

			if (cmd.getType() == ReceiveCommand.Type.DELETE) {
				if (!isAllowDeletes()) {
					// Deletes are not supported on this repository.
					cmd.setResult(Result.REJECTED_NODELETE);
					continue;
				}
				if (!isAllowBranchDeletes()
						&& ref.getName().startsWith(Constants.R_HEADS)) {
					// Branches cannot be deleted, but other refs can.
					cmd.setResult(Result.REJECTED_NODELETE);
					continue;
				}
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
					cmd.setResult(Result.REJECTED_OTHER_REASON,
							JGitText.get().refAlreadyExists);
					continue;
				}
			}

			if (cmd.getType() == ReceiveCommand.Type.DELETE && ref != null) {
				ObjectId id = ref.getObjectId();
				if (id == null) {
					id = ObjectId.zeroId();
				}
				if (!ObjectId.zeroId().equals(cmd.getOldId())
						&& !id.equals(cmd.getOldId())) {
					// Delete commands can be sent with the old id matching our
					// advertised value, *OR* with the old id being 0{40}. Any
					// other requested old id is invalid.
					//
					cmd.setResult(Result.REJECTED_OTHER_REASON,
							JGitText.get().invalidOldIdSent);
					continue;
				}
			}

			if (cmd.getType() == ReceiveCommand.Type.UPDATE) {
				if (ref == null) {
					// The ref must have been advertised in order to be updated.
					//
					cmd.setResult(Result.REJECTED_OTHER_REASON,
							JGitText.get().noSuchRef);
					continue;
				}
				ObjectId id = ref.getObjectId();
				if (id == null) {
					// We cannot update unborn branch
					cmd.setResult(Result.REJECTED_OTHER_REASON,
							JGitText.get().cannotUpdateUnbornBranch);
					continue;
				}

				if (!id.equals(cmd.getOldId())) {
					// A properly functioning client will send the same
					// object id we advertised.
					//
					cmd.setResult(Result.REJECTED_OTHER_REASON,
							JGitText.get().invalidOldIdSent);
					continue;
				}

				// Is this possibly a non-fast-forward style update?
				//
				RevObject oldObj, newObj;
				try {
					oldObj = walk.parseAny(cmd.getOldId());
				} catch (IOException e) {
					receiveCommandErrorHandler
							.handleOldIdValidationException(cmd, e);
					continue;
				}

				try {
					newObj = walk.parseAny(cmd.getNewId());
				} catch (IOException e) {
					receiveCommandErrorHandler
							.handleNewIdValidationException(cmd, e);
					continue;
				}

				if (oldObj instanceof RevCommit
						&& newObj instanceof RevCommit) {
					try {
						if (walk.isMergedInto((RevCommit) oldObj,
								(RevCommit) newObj)) {
							cmd.setTypeFastForwardUpdate();
						} else {
							cmd.setType(ReceiveCommand.Type.UPDATE_NONFASTFORWARD);
						}
					} catch (IOException e) {
						receiveCommandErrorHandler
								.handleFastForwardCheckException(cmd, e);
					}
				} else {
					cmd.setType(ReceiveCommand.Type.UPDATE_NONFASTFORWARD);
				}

				if (cmd.getType() == ReceiveCommand.Type.UPDATE_NONFASTFORWARD
						&& !isAllowNonFastForwards()) {
					cmd.setResult(Result.REJECTED_NONFASTFORWARD);
					continue;
				}
			}

			if (!cmd.getRefName().startsWith(Constants.R_REFS)
					|| !Repository.isValidRefName(cmd.getRefName())) {
				cmd.setResult(Result.REJECTED_OTHER_REASON,
						JGitText.get().funnyRefname);
			}
		}
	}

	/**
	 * Whether any commands have been rejected so far.
	 *
	 * @return if any commands have been rejected so far.
	 */
	private boolean anyRejects() {
		for (ReceiveCommand cmd : commands) {
			if (cmd.getResult() != Result.NOT_ATTEMPTED
					&& cmd.getResult() != Result.OK)
				return true;
		}
		return false;
	}

	/**
	 * Set the result to fail for any command that was not processed yet.
	 *
	 */
	private void failPendingCommands() {
		ReceiveCommand.abort(commands);
	}

	/**
	 * Filter the list of commands according to result.
	 *
	 * @param want
	 *            desired status to filter by.
	 * @return a copy of the command list containing only those commands with
	 *         the desired status.
	 * @since 5.7
	 */
	protected List<ReceiveCommand> filterCommands(Result want) {
		return ReceiveCommand.filter(commands, want);
	}

	/**
	 * Execute commands to update references.
	 * @since 5.7
	 */
	protected void executeCommands() {
		List<ReceiveCommand> toApply = filterCommands(Result.NOT_ATTEMPTED);
		if (toApply.isEmpty())
			return;

		ProgressMonitor updating = NullProgressMonitor.INSTANCE;
		if (sideBand) {
			SideBandProgressMonitor pm = new SideBandProgressMonitor(msgOut);
			pm.setDelayStart(250, TimeUnit.MILLISECONDS);
			updating = pm;
		}

		BatchRefUpdate batch = db.getRefDatabase().newBatchUpdate();
		batch.setAllowNonFastForwards(isAllowNonFastForwards());
		batch.setAtomic(isAtomic());
		batch.setRefLogIdent(getRefLogIdent());
		batch.setRefLogMessage("push", true); //$NON-NLS-1$
		batch.addCommand(toApply);
		try {
			batch.setPushCertificate(getPushCertificate());
			batch.execute(walk, updating);
		} catch (IOException e) {
			receiveCommandErrorHandler.handleBatchRefUpdateException(toApply,
					e);
		}
	}

	/**
	 * Send a status report.
	 *
	 * @param unpackError
	 *            an error that occurred during unpacking, or {@code null}
	 * @throws java.io.IOException
	 *             an error occurred writing the status report.
	 * @since 5.6
	 */
	private void sendStatusReport(Throwable unpackError) throws IOException {
		Reporter out = new Reporter() {
			@Override
			void sendString(String s) throws IOException {
				if (reportStatus) {
					pckOut.writeString(s + '\n');
				} else if (msgOut != null) {
					msgOut.write(Constants.encode(s + '\n'));
				}
			}
		};

		try {
			if (unpackError != null) {
				out.sendString("unpack error " + unpackError.getMessage()); //$NON-NLS-1$
				if (reportStatus) {
					for (ReceiveCommand cmd : commands) {
						out.sendString("ng " + cmd.getRefName() //$NON-NLS-1$
								+ " n/a (unpacker error)"); //$NON-NLS-1$
					}
				}
				return;
			}

			if (reportStatus) {
				out.sendString("unpack ok"); //$NON-NLS-1$
			}
			for (ReceiveCommand cmd : commands) {
				if (cmd.getResult() == Result.OK) {
					if (reportStatus) {
						out.sendString("ok " + cmd.getRefName()); //$NON-NLS-1$
					}
					continue;
				}

				final StringBuilder r = new StringBuilder();
				if (reportStatus) {
					r.append("ng ").append(cmd.getRefName()).append(" "); //$NON-NLS-1$ //$NON-NLS-2$
				} else {
					r.append(" ! [rejected] ").append(cmd.getRefName()) //$NON-NLS-1$
							.append(" ("); //$NON-NLS-1$
				}

				if (cmd.getResult() == Result.REJECTED_MISSING_OBJECT) {
					if (cmd.getMessage() == null)
						r.append("missing object(s)"); //$NON-NLS-1$
					else if (cmd.getMessage()
							.length() == Constants.OBJECT_ID_STRING_LENGTH) {
						// TODO: Using get/setMessage to store an OID is a
						// misuse. The caller should set a full error message.
						r.append("object "); //$NON-NLS-1$
						r.append(cmd.getMessage());
						r.append(" missing"); //$NON-NLS-1$
					} else {
						r.append(cmd.getMessage());
					}
				} else if (cmd.getMessage() != null) {
					r.append(cmd.getMessage());
				} else {
					switch (cmd.getResult()) {
					case NOT_ATTEMPTED:
						r.append("server bug; ref not processed"); //$NON-NLS-1$
						break;

					case REJECTED_NOCREATE:
						r.append("creation prohibited"); //$NON-NLS-1$
						break;

					case REJECTED_NODELETE:
						r.append("deletion prohibited"); //$NON-NLS-1$
						break;

					case REJECTED_NONFASTFORWARD:
						r.append("non-fast forward"); //$NON-NLS-1$
						break;

					case REJECTED_CURRENT_BRANCH:
						r.append("branch is currently checked out"); //$NON-NLS-1$
						break;

					case REJECTED_OTHER_REASON:
						r.append("unspecified reason"); //$NON-NLS-1$
						break;

					case LOCK_FAILURE:
						r.append("failed to lock"); //$NON-NLS-1$
						break;

					case REJECTED_MISSING_OBJECT:
					case OK:
						// We shouldn't have reached this case (see 'ok' case
						// above and if-statement above).
						throw new AssertionError();
					}
				}

				if (!reportStatus) {
					r.append(")"); //$NON-NLS-1$
				}
				out.sendString(r.toString());
			}
		} finally {
			if (reportStatus) {
				pckOut.end();
			}
		}
	}

	/**
	 * Close and flush (if necessary) the underlying streams.
	 *
	 * @throws IOException
	 *             if an IO error occurred
	 */
	private void close() throws IOException {
		if (sideBand) {
			// If we are using side band, we need to send a final
			// flush-pkt to tell the remote peer the side band is
			// complete and it should stop decoding. We need to
			// use the original output stream as rawOut is now the
			// side band data channel.
			//
			((SideBandOutputStream) msgOut).flushBuffer();
			((SideBandOutputStream) rawOut).flushBuffer();

			PacketLineOut plo = new PacketLineOut(origOut);
			plo.setFlushOnEnd(false);
			plo.end();
		}

		if (biDirectionalPipe) {
			// If this was a native git connection, flush the pipe for
			// the caller. For smart HTTP we don't do this flush and
			// instead let the higher level HTTP servlet code do it.
			//
			if (!sideBand && msgOut != null)
				msgOut.flush();
			rawOut.flush();
		}
	}

	/**
	 * Release any resources used by this object.
	 *
	 * @throws java.io.IOException
	 *             the pack could not be unlocked.
	 */
	private void release() throws IOException {
		walk.close();
		unlockPack();
		timeoutIn = null;
		rawIn = null;
		rawOut = null;
		msgOut = null;
		pckIn = null;
		pckOut = null;
		refs = null;
		// Keep the capabilities. If responses are sent after this release
		// we need to remember at least whether sideband communication has to be
		// used
		commands = null;
		if (timer != null) {
			try {
				timer.terminate();
			} finally {
				timer = null;
			}
		}
	}

	/** Interface for reporting status messages. */
	abstract static class Reporter {
		abstract void sendString(String s) throws IOException;
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
	public PushCertificate getPushCertificate() {
		return pushCert;
	}

	/**
	 * Set the push certificate used to verify the pusher's identity.
	 * <p>
	 * Should only be called if reconstructing an instance without going through
	 * the normal {@link #recvCommands()} flow.
	 *
	 * @param cert
	 *            the push certificate to set.
	 * @since 4.1
	 */
	public void setPushCertificate(PushCertificate cert) {
		pushCert = cert;
	}

	/**
	 * Gets an unmodifiable view of the option strings associated with the push.
	 *
	 * @return an unmodifiable view of pushOptions, or null (if pushOptions is).
	 * @since 4.5
	 */
	@Nullable
	public List<String> getPushOptions() {
		if (isAllowPushOptions() && usePushOptions) {
			return Collections.unmodifiableList(pushOptions);
		}

		// The client doesn't support push options. Return null to
		// distinguish this from the case where the client declared support
		// for push options and sent an empty list of them.
		return null;
	}

	/**
	 * Set the push options supplied by the client.
	 * <p>
	 * Should only be called if reconstructing an instance without going through
	 * the normal {@link #recvCommands()} flow.
	 *
	 * @param options
	 *            the list of options supplied by the client. The
	 *            {@code ReceivePack} instance takes ownership of this list.
	 *            Callers are encouraged to first create a copy if the list may
	 *            be modified later.
	 * @since 4.5
	 */
	public void setPushOptions(@Nullable List<String> options) {
		usePushOptions = options != null;
		pushOptions = options;
	}

	/**
	 * Get the hook invoked before updates occur.
	 *
	 * @return the hook invoked before updates occur.
	 */
	public PreReceiveHook getPreReceiveHook() {
		return preReceive;
	}

	/**
	 * Set the hook which is invoked prior to commands being executed.
	 * <p>
	 * Only valid commands (those which have no obvious errors according to the
	 * received input and this instance's configuration) are passed into the
	 * hook. The hook may mark a command with a result of any value other than
	 * {@link org.eclipse.jgit.transport.ReceiveCommand.Result#NOT_ATTEMPTED} to
	 * block its execution.
	 * <p>
	 * The hook may be called with an empty command collection if the current
	 * set is completely invalid.
	 *
	 * @param h
	 *            the hook instance; may be null to disable the hook.
	 */
	public void setPreReceiveHook(PreReceiveHook h) {
		preReceive = h != null ? h : PreReceiveHook.NULL;
	}

	/**
	 * Get the hook invoked after updates occur.
	 *
	 * @return the hook invoked after updates occur.
	 */
	public PostReceiveHook getPostReceiveHook() {
		return postReceive;
	}

	/**
	 * Set the hook which is invoked after commands are executed.
	 * <p>
	 * Only successful commands (type is
	 * {@link org.eclipse.jgit.transport.ReceiveCommand.Result#OK}) are passed
	 * into the hook. The hook may be called with an empty command collection if
	 * the current set all resulted in an error.
	 *
	 * @param h
	 *            the hook instance; may be null to disable the hook.
	 */
	public void setPostReceiveHook(PostReceiveHook h) {
		postReceive = h != null ? h : PostReceiveHook.NULL;
	}

	/**
	 * Get the current unpack error handler.
	 *
	 * @return the current unpack error handler.
	 * @since 5.8
	 */
	public UnpackErrorHandler getUnpackErrorHandler() {
		return unpackErrorHandler;
	}

	/**
	 * Set the unpackErrorHandler
	 *
	 * @param unpackErrorHandler
	 *            the unpackErrorHandler
	 * @since 5.7
	 */
	public void setUnpackErrorHandler(UnpackErrorHandler unpackErrorHandler) {
		this.unpackErrorHandler = unpackErrorHandler;
	}

	/**
	 * Get the client session-id
	 *
	 * @return The client session-id.
	 * @since 6.4
	 */
	public String getClientSID() {
		return clientSID;
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
	 * @throws java.io.IOException
	 *             if an IO error occurred
	 */
	public void receive(final InputStream input, final OutputStream output,
			final OutputStream messages) throws IOException {
		init(input, output, messages);
		try {
			service();
		} catch (PackProtocolException e) {
			fatalError(e.getMessage());
			throw e;
		} catch (InputOverLimitIOException e) {
			String msg = JGitText.get().tooManyCommands;
			fatalError(msg);
			throw new PackProtocolException(msg, e);
		} finally {
			try {
				close();
			} finally {
				release();
			}
		}
	}

	/**
	 * Execute the receive task on the socket.
	 *
	 * <p>
	 * Same as {@link #receive}, but the exceptions are not reported to the
	 * client yet.
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
	 * @throws java.io.IOException
	 *             if an IO error occurred
	 * @since 5.7
	 */
	public void receiveWithExceptionPropagation(InputStream input,
			OutputStream output, OutputStream messages) throws IOException {
		init(input, output, messages);
		try {
			service();
		} finally {
			try {
				close();
			} finally {
				release();
			}
		}
	}

	private void service() throws IOException {
		if (isBiDirectionalPipe()) {
			sendAdvertisedRefs(new PacketLineOutRefAdvertiser(pckOut));
			pckOut.flush();
		} else
			getAdvertisedOrDefaultRefs();
		if (hasError())
			return;

		recvCommands();

		if (hasCommands()) {
			try (PostReceiveExecutor e = new PostReceiveExecutor()) {
				if (needPack()) {
					try {
						receivePackAndCheckConnectivity();
					} catch (IOException | RuntimeException
							| SubmoduleValidationException | Error err) {
						unlockPack();
						unpackErrorHandler.handleUnpackException(err);
						throw new UnpackException(err);
					}
				}

				try {
					setAtomic(isCapabilityEnabled(CAPABILITY_ATOMIC));

					validateCommands();
					if (atomic && anyRejects()) {
						failPendingCommands();
					}

					preReceive.onPreReceive(
							this, filterCommands(Result.NOT_ATTEMPTED));
					if (atomic && anyRejects()) {
						failPendingCommands();
					}
					executeCommands();
				} finally {
					unlockPack();
				}

				sendStatusReport(null);
			}
		}
	}

	static ReceiveCommand parseCommand(String line)
			throws PackProtocolException {
		if (line == null || line.length() < 83) {
			throw new PackProtocolException(
					JGitText.get().errorInvalidProtocolWantedOldNewRef);
		}
		String oldStr = line.substring(0, 40);
		String newStr = line.substring(41, 81);
		ObjectId oldId, newId;
		try {
			oldId = ObjectId.fromString(oldStr);
			newId = ObjectId.fromString(newStr);
		} catch (InvalidObjectIdException e) {
			throw new PackProtocolException(
					JGitText.get().errorInvalidProtocolWantedOldNewRef, e);
		}
		String name = line.substring(82);
		if (!Repository.isValidRefName(name)) {
			throw new PackProtocolException(
					JGitText.get().errorInvalidProtocolWantedOldNewRef);
		}
		return new ReceiveCommand(oldId, newId, name);
	}

	private class PostReceiveExecutor implements AutoCloseable {
		@Override
		public void close() {
			postReceive.onPostReceive(ReceivePack.this,
					filterCommands(Result.OK));
		}
	}

	private class DefaultUnpackErrorHandler implements UnpackErrorHandler {
		@Override
		public void handleUnpackException(Throwable t) throws IOException {
			sendStatusReport(t);
		}
	}
}
