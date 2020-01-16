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
 * Hook invoked by {@link org.eclipse.jgit.transport.ReceivePack} before any
 * updates are executed.
 * <p>
 * The hook is called with any commands that are deemed valid after parsing them
 * from the client and applying the standard receive configuration options to
 * them:
 * <ul>
 * <li><code>receive.denyDenyDeletes</code></li>
 * <li><code>receive.denyNonFastForwards</code></li>
 * </ul>
 * This means the hook will not receive a non-fast-forward update command if
 * denyNonFastForwards is set to true in the configuration file. To get all
 * commands within the hook, see
 * {@link org.eclipse.jgit.transport.ReceivePack#getAllCommands()}.
 * <p>
 * As the hook is invoked prior to the commands being executed, the hook may
 * choose to block any command by setting its result status with
 * {@link org.eclipse.jgit.transport.ReceiveCommand#setResult(ReceiveCommand.Result)}.
 * <p>
 * The hook may also choose to perform the command itself (or merely pretend
 * that it has performed the command), by setting the result status to
 * {@link org.eclipse.jgit.transport.ReceiveCommand.Result#OK}.
 * <p>
 * Hooks should run quickly, as they block the caller thread and the client
 * process from completing.
 * <p>
 * Hooks may send optional messages back to the client via methods on
 * {@link org.eclipse.jgit.transport.ReceivePack}. Implementors should be aware
 * that not all network transports support this output, so some (or all)
 * messages may simply be discarded. These messages should be advisory only.
 */
public interface PreReceiveHook {
	/** A simple no-op hook. */
	PreReceiveHook NULL = (final ReceivePack rp,
			final Collection<ReceiveCommand> commands) -> {
		// Do nothing.
	};

	/**
	 * Invoked just before commands are executed.
	 * <p>
	 * See the class description for how this method can impact execution.
	 *
	 * @param rp
	 *            the process handling the current receive. Hooks may obtain
	 *            details about the destination repository through this handle.
	 * @param commands
	 *            unmodifiable set of valid commands still pending execution.
	 *            May be the empty set.
	 */
	void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> commands);
}
