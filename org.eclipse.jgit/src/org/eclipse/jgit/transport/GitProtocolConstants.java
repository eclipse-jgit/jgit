/*
 * Copyright (C) 2008, 2013 Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, 2020 Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

/**
 * Wire constants for the native Git protocol.
 *
 * @since 3.2
 */
public final class GitProtocolConstants {
	/**
	 * Include tags if we are also including the referenced objects.
	 *
	 * @since 3.2
	 */
	public static final String OPTION_INCLUDE_TAG = "include-tag"; //$NON-NLS-1$

	/**
	 * Multi-ACK support for improved negotiation.
	 *
	 * @since 3.2
	 */
	public static final String OPTION_MULTI_ACK = "multi_ack"; //$NON-NLS-1$

	/**
	 * Multi-ACK detailed support for improved negotiation.
	 *
	 * @since 3.2
	 */
	public static final String OPTION_MULTI_ACK_DETAILED = "multi_ack_detailed"; //$NON-NLS-1$

	/**
	 * The client supports packs with deltas but not their bases.
	 *
	 * @since 3.2
	 */
	public static final String OPTION_THIN_PACK = "thin-pack"; //$NON-NLS-1$

	/**
	 * The client supports using the side-band for progress messages.
	 *
	 * @since 3.2
	 */
	public static final String OPTION_SIDE_BAND = "side-band"; //$NON-NLS-1$

	/**
	 * The client supports using the 64K side-band for progress messages.
	 *
	 * @since 3.2
	 */
	public static final String OPTION_SIDE_BAND_64K = "side-band-64k"; //$NON-NLS-1$

	/**
	 * The client supports packs with OFS deltas.
	 *
	 * @since 3.2
	 */
	public static final String OPTION_OFS_DELTA = "ofs-delta"; //$NON-NLS-1$

	/**
	 * The client supports shallow fetches.
	 *
	 * @since 3.2
	 */
	public static final String OPTION_SHALLOW = "shallow"; //$NON-NLS-1$

	/**
	 * The client wants the "deepen" command to be interpreted as relative to
	 * the client's shallow commits.
	 *
	 * @since 5.0
	 */
	public static final String OPTION_DEEPEN_RELATIVE = "deepen-relative"; //$NON-NLS-1$

	/**
	 * The client does not want progress messages and will ignore them.
	 *
	 * @since 3.2
	 */
	public static final String OPTION_NO_PROGRESS = "no-progress"; //$NON-NLS-1$

	/**
	 * The client supports receiving a pack before it has sent "done".
	 *
	 * @since 3.2
	 */
	public static final String OPTION_NO_DONE = "no-done"; //$NON-NLS-1$

	/**
	 * The client supports fetching objects at the tip of any ref, even if not
	 * advertised.
	 *
	 * @since 3.2
	 */
	public static final String OPTION_ALLOW_TIP_SHA1_IN_WANT = "allow-tip-sha1-in-want"; //$NON-NLS-1$

	/**
	 * The client supports fetching objects that are reachable from a tip of a
	 * ref that is allowed to fetch.
	 *
	 * @since 4.1
	 */
	public static final String OPTION_ALLOW_REACHABLE_SHA1_IN_WANT = "allow-reachable-sha1-in-want"; //$NON-NLS-1$

	/**
	 * Symbolic reference support for better negotiation.
	 *
	 * @since 3.6
	 */
	public static final String OPTION_SYMREF = "symref"; //$NON-NLS-1$

	/**
	 * The client will send a push certificate.
	 *
	 * @since 4.0
	 */
	public static final String OPTION_PUSH_CERT = "push-cert"; //$NON-NLS-1$

	/**
	 * The client specified a filter expression.
	 *
	 * @since 5.0
	 */
	public static final String OPTION_FILTER = "filter"; //$NON-NLS-1$

	/**
	 * The client specified a want-ref expression.
	 *
	 * @since 5.1
	 */
	public static final String OPTION_WANT_REF = "want-ref"; //$NON-NLS-1$

	/**
	 * The client requested that the whole response be multiplexed, with
	 * each non-flush and non-delim pkt prefixed by a sideband designator.
	 *
	 * @since 5.5
	 */
	public static final String OPTION_SIDEBAND_ALL = "sideband-all"; //$NON-NLS-1$

	/**
	 * The server waits for client to send "done" before sending any packs back.
	 *
	 * @since 5.13
	 */
	public static final String OPTION_WAIT_FOR_DONE = "wait-for-done"; //$NON-NLS-1$

	/**
	 * The client supports atomic pushes. If this option is used, the server
	 * will update all refs within one atomic transaction.
	 *
	 * @since 3.6
	 */
	public static final String CAPABILITY_ATOMIC = "atomic"; //$NON-NLS-1$

	/**
	 * The client expects less noise, e.g. no progress.
	 *
	 * @since 4.0
	 */
	public static final String CAPABILITY_QUIET = "quiet"; //$NON-NLS-1$

	/**
	 * The client expects a status report after the server processes the pack.
	 *
	 * @since 3.2
	 */
	public static final String CAPABILITY_REPORT_STATUS = "report-status"; //$NON-NLS-1$

	/**
	 * The server supports deleting refs.
	 *
	 * @since 3.2
	 */
	public static final String CAPABILITY_DELETE_REFS = "delete-refs"; //$NON-NLS-1$

	/**
	 * The server supports packs with OFS deltas.
	 *
	 * @since 3.2
	 */
	public static final String CAPABILITY_OFS_DELTA = "ofs-delta"; //$NON-NLS-1$

	/**
	 * The client supports using the 64K side-band for progress messages.
	 *
	 * @since 3.2
	 */
	public static final String CAPABILITY_SIDE_BAND_64K = "side-band-64k"; //$NON-NLS-1$

	/**
	 * The server allows recording of push certificates.
	 *
	 * @since 4.0
	 */
	public static final String CAPABILITY_PUSH_CERT = "push-cert"; //$NON-NLS-1$

	/**
	 * Implementation name and version of the client or server.
	 *
	 * @since 4.0
	 */
	public static final String OPTION_AGENT = "agent"; //$NON-NLS-1$

	/**
	 * The server supports the receiving of push options.
	 *
	 * @since 4.5
	 */
	public static final String CAPABILITY_PUSH_OPTIONS = "push-options"; //$NON-NLS-1$

	/**
	 * The server supports the client specifying ref names.
	 *
	 * @since 5.1
	 */
	public static final String CAPABILITY_REF_IN_WANT = "ref-in-want"; //$NON-NLS-1$

	/**
	 * The server supports arbitrary options
	 *
	 * @since 5.2
	 */
	public static final String CAPABILITY_SERVER_OPTION = "server-option"; //$NON-NLS-1$

	/**
	 * The server supports the receiving of shallow options.
	 *
	 * @since 6.2
	 */
	public static final String CAPABILITY_SHALLOW = "shallow"; //$NON-NLS-1$

	/**
	 * Option for passing application-specific options to the server.
	 *
	 * @since 5.2
	 */
	public static final String OPTION_SERVER_OPTION = "server-option"; //$NON-NLS-1$

	/**
	 * The server supports listing refs using protocol v2.
	 *
	 * @since 5.0
	 */
	public static final String COMMAND_LS_REFS = "ls-refs"; //$NON-NLS-1$

	/**
	 * The server supports fetch using protocol v2.
	 *
	 * @since 5.0
	 */
	public static final String COMMAND_FETCH = "fetch"; //$NON-NLS-1$

	/**
	 * The server supports the object-info capability.
	 *
	 * @since 5.13
	 */
	public static final String COMMAND_OBJECT_INFO = "object-info"; //$NON-NLS-1$

	/**
	 * HTTP header to set by clients to request a specific git protocol version
	 * in the HTTP transport.
	 *
	 * @since 5.11
	 */
	public static final String PROTOCOL_HEADER = "Git-Protocol"; //$NON-NLS-1$

	/**
	 * Environment variable to set by clients to request a specific git protocol
	 * in the file:// and ssh:// transports.
	 *
	 * @since 5.11
	 */
	public static final String PROTOCOL_ENVIRONMENT_VARIABLE = "GIT_PROTOCOL"; //$NON-NLS-1$

	/**
	 * Protocol V2 ref advertisement attribute containing the peeled object id
	 * for annotated tags.
	 *
	 * @since 5.11
	 */
	public static final String REF_ATTR_PEELED = "peeled:"; //$NON-NLS-1$

	/**
	 * Protocol V2 ref advertisement attribute containing the name of the ref
	 * for symbolic refs.
	 *
	 * @since 5.11
	 */
	public static final String REF_ATTR_SYMREF_TARGET = "symref-target:"; //$NON-NLS-1$

	/**
	 * Protocol V2 acknowledgments section header.
	 *
	 * @since 5.11
	 */
	public static final String SECTION_ACKNOWLEDGMENTS = "acknowledgments"; //$NON-NLS-1$

	/**
	 * Protocol V2 packfile section header.
	 *
	 * @since 5.11
	 */
	public static final String SECTION_PACKFILE = "packfile"; //$NON-NLS-1$

	/**
	 * Protocol V2 shallow-info section header.
	 *
	 * @since 6.2
	 */
	public static final String SECTION_SHALLOW_INFO = "shallow-info"; //$NON-NLS-1$

	/**
	 * Protocol announcement for protocol version 1. This is the same as V0,
	 * except for this initial line.
	 *
	 * @since 5.11
	 */
	public static final String VERSION_1 = "version 1"; //$NON-NLS-1$

	/**
	 * Protocol announcement for protocol version 2.
	 *
	 * @since 5.11
	 */
	public static final String VERSION_2 = "version 2"; //$NON-NLS-1$

	/**
	 * Protocol request for protocol version 2.
	 *
	 * @since 5.11
	 */
	public static final String VERSION_2_REQUEST = "version=2"; //$NON-NLS-1$

	enum MultiAck {
		OFF, CONTINUE, DETAILED;
	}

	private GitProtocolConstants() {
	}
}
