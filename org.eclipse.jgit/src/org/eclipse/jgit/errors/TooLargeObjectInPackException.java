/*
 * Copyright (C) 2011, Sasa Zivkov <sasa.zivkov@sap.com> and others
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
 * Thrown when PackParser finds an object larger than a predefined limit
 */
public class TooLargeObjectInPackException extends TransportException {
	private static final long serialVersionUID = 1L;

	/**
	 * Construct a too large object in pack exception when the exact size of the
	 * too large object is not available. This will be used when we find out
	 * that a delta sequence is already larger than the maxObjectSizeLimit but
	 * don't want to inflate the delta just to find out the exact size of the
	 * resulting object.
	 *
	 * @param maxObjectSizeLimit
	 *            the maximum object size limit
	 */
	public TooLargeObjectInPackException(long maxObjectSizeLimit) {
		super(MessageFormat.format(JGitText.get().receivePackObjectTooLarge1,
				Long.valueOf(maxObjectSizeLimit)));
	}

	/**
	 * Construct a too large object in pack exception when the exact size of the
	 * too large object is known.
	 *
	 * @param objectSize
	 *            a long.
	 * @param maxObjectSizeLimit
	 *            a long.
	 */
	public TooLargeObjectInPackException(long objectSize,
			long maxObjectSizeLimit) {
		super(MessageFormat.format(JGitText.get().receivePackObjectTooLarge2,
				Long.valueOf(objectSize), Long.valueOf(maxObjectSizeLimit)));
	}

	/**
	 * Construct a too large object in pack exception.
	 *
	 * @param uri
	 *            URI used for transport
	 * @param s
	 *            message
	 * @since 4.4
	 */
	public TooLargeObjectInPackException(URIish uri, String s) {
		super(uri.setPass(null) + ": " + s); //$NON-NLS-1$
	}
}
