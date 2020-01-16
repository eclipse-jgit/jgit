/*
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

/**
 * Internal API to assist {@code org.eclipse.jgit.http.server}.
 * <p>
 * <b>Do not call.</b>
 *
 * @since 4.0
 */
public class InternalHttpServerGlue {
	/**
	 * Apply a default user agent for a request.
	 *
	 * @param up
	 *            current UploadPack instance.
	 * @param agent
	 *            user agent string from the HTTP headers.
	 */
	public static void setPeerUserAgent(UploadPack up, String agent) {
		up.userAgent = agent;
	}

	/**
	 * Apply a default user agent for a request.
	 *
	 * @param rp
	 *            current ReceivePack instance.
	 * @param agent
	 *            user agent string from the HTTP headers.
	 */
	public static void setPeerUserAgent(ReceivePack rp, String agent) {
		rp.userAgent = agent;
	}

	private InternalHttpServerGlue() {
	}
}
