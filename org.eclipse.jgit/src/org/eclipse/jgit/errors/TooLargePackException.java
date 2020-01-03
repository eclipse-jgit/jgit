/*
 * Copyright (C) 2014, Sasa Zivkov <sasa.zivkov@sap.com>, SAP AG and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.transport.URIish;

/**
 * Thrown when a pack exceeds a given size limit
 *
 * @since 3.3
 */
public class TooLargePackException extends TransportException {
	private static final long serialVersionUID = 1L;

	/**
	 * Construct a too large pack exception.
	 *
	 * @param packSizeLimit
	 *            the pack size limit (in bytes) that was exceeded
	 */
	public TooLargePackException(long packSizeLimit) {
		super(MessageFormat.format(JGitText.get().receivePackTooLarge,
				Long.valueOf(packSizeLimit)));
	}

	/**
	 * Construct a too large pack exception.
	 *
	 * @param uri
	 *            URI used for transport
	 * @param s
	 *            message
	 * @since 4.0
	 */
	public TooLargePackException(URIish uri, String s) {
		super(uri.setPass(null) + ": " + s); //$NON-NLS-1$
	}
}
