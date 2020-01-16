/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport.sshd;

/**
 * A {@code SessionCloseListener} is invoked when a {@link SshdSession} is
 * closed.
 *
 * @since 5.2
 */
@FunctionalInterface
public interface SessionCloseListener {

	/**
	 * Invoked when a {@link SshdSession} has been closed.
	 *
	 * @param session
	 *            that was closed.
	 */
	void sessionClosed(SshdSession session);
}
