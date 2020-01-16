/*
 * Copyright (C) 2008, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.util.Collection;

/**
 * Hook invoked by {@link org.eclipse.jgit.transport.ReceivePack} after all
 * updates are executed.
 * <p>
 * The hook is called after all commands have been processed. Only commands with
 * a status of {@link org.eclipse.jgit.transport.ReceiveCommand.Result#OK} are
 * passed into the hook. To get all commands within the hook, see
 * {@link org.eclipse.jgit.transport.ReceivePack#getAllCommands()}.
 * <p>
 * Any post-receive hook implementation should not update the status of a
 * command, as the command has already completed or failed, and the status has
 * already been returned to the client.
 * <p>
 * Hooks should execute quickly, as they block the server and the client from
 * completing the connection.
 */
public interface PostReceiveHook {
	/** A simple no-op hook. */
	PostReceiveHook NULL = (final ReceivePack rp,
			final Collection<ReceiveCommand> commands) -> {
		// Do nothing.
	};

	/**
	 * Invoked after all commands are executed and status has been returned.
	 *
	 * @param rp
	 *            the process handling the current receive. Hooks may obtain
	 *            details about the destination repository through this handle.
	 * @param commands
	 *            unmodifiable set of successfully completed commands. May be
	 *            the empty set.
	 */
	void onPostReceive(ReceivePack rp,
			Collection<ReceiveCommand> commands);
}
