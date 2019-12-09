/*
 * Copyright (c) 2019, Google LLC  and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.transport.ReceiveCommand.Result;

/**
 * Exception handler for processing {@link ReceiveCommand}.
 *
 * @since 5.7
 */
public interface ReceiveCommandErrorHandler {
	/**
	 * Handle an exception thrown while validating the new commit ID.
	 *
	 * @param cmd
	 *            offending command
	 * @param e
	 *            exception thrown
	 */
	default void handleNewIdValidationException(ReceiveCommand cmd,
			IOException e) {
		cmd.setResult(Result.REJECTED_MISSING_OBJECT, cmd.getNewId().name());
	}

	/**
	 * Handle an exception thrown while validating the old commit ID.
	 *
	 * @param cmd
	 *            offending command
	 * @param e
	 *            exception thrown
	 */
	default void handleOldIdValidationException(ReceiveCommand cmd,
			IOException e) {
		cmd.setResult(Result.REJECTED_MISSING_OBJECT, cmd.getOldId().name());
	}

	/**
	 * Handle an exception thrown while checking if the update is fast-forward.
	 *
	 * @param cmd
	 *            offending command
	 * @param e
	 *            exception thrown
	 */
	default void handleFastForwardCheckException(ReceiveCommand cmd,
			IOException e) {
		if (e instanceof MissingObjectException) {
			cmd.setResult(Result.REJECTED_MISSING_OBJECT, e.getMessage());
		} else {
			cmd.setResult(Result.REJECTED_OTHER_REASON);
		}
	}

	/**
	 * Handle an exception thrown while checking if the update is fast-forward.
	 *
	 * @param cmds
	 *            commands being processed
	 * @param e
	 *            exception thrown
	 */
	default void handleBatchRefUpdateException(List<ReceiveCommand> cmds,
			IOException e) {
		for (ReceiveCommand cmd : cmds) {
			if (cmd.getResult() == Result.NOT_ATTEMPTED) {
				cmd.reject(e);
			}
		}
	}
}
