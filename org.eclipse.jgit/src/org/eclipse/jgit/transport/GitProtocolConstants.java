/*
 * Copyright (C) 2008-2013, Google Inc.
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

/**
 * Wire constants for the native Git protocol.
 *
 * @since 3.2
 */
public class GitProtocolConstants {
	/**
	 * Include tags if we are also including the referenced objects.
	 *
	 * @since 3.2
	 */
	public static final String OPTION_INCLUDE_TAG = "include-tag"; //$NON-NLS-1$

	/**
	 * Mutli-ACK support for improved negotiation.
	 *
	 * @since 3.2
	 */
	public static final String OPTION_MULTI_ACK = "multi_ack"; //$NON-NLS-1$

	/**
	 * Mutli-ACK detailed support for improved negotiation.
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
	 * Symbolic reference support for better negotiation.
	 *
	 * @since 3.6
	 */
	public static final String OPTION_SYMREF = "symref"; //$NON-NLS-1$

	/**
	 * The client supports atomic pushes. If this option is used, the server
	 * will update all refs within one atomic transaction.
	 *
	 * @since 3.6
	 */
	public static final String CAPABILITY_ATOMIC = "atomic-push"; //$NON-NLS-1$

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

	static enum MultiAck {
		OFF, CONTINUE, DETAILED;
	}

	private GitProtocolConstants() {
	}
}
