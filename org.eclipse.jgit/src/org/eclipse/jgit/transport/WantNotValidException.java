/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.text.MessageFormat;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.AnyObjectId;

/**
 * Indicates client requested an object the server does not want to serve.
 * <p>
 * Typically visible only inside of the server implementation; clients are
 * usually looking at the text message from the server in a generic
 * {@link org.eclipse.jgit.errors.PackProtocolException}.
 *
 * @since 4.3
 */
public class WantNotValidException extends PackProtocolException {
	private static final long serialVersionUID = 1L;

	/**
	 * Construct a {@code "want $id not valid"} exception.
	 *
	 * @param id
	 *            invalid object identifier received from the client.
	 */
	public WantNotValidException(AnyObjectId id) {
		super(msg(id));
	}

	/**
	 * Construct a {@code "want $id not valid"} exception.
	 *
	 * @param id
	 *            invalid object identifier received from the client.
	 * @param cause
	 *            root cause of the object being invalid, such as an IOException
	 *            from the storage system.
	 */
	public WantNotValidException(AnyObjectId id, Throwable cause) {
		super(msg(id), cause);
	}

	private static String msg(AnyObjectId id) {
		return MessageFormat.format(JGitText.get().wantNotValid, id.name());
	}
}
