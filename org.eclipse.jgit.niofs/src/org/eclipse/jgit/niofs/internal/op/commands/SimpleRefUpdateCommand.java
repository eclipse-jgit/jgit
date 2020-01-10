/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.commands;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.revwalk.RevCommit;

public class SimpleRefUpdateCommand {

	private final Git git;
	private final String name;
	private final RevCommit commit;

	public SimpleRefUpdateCommand(final Git git, final String branchName, final RevCommit commit) {
		this.git = git;
		this.name = branchName;
		this.commit = commit;
	}

	public void execute() throws IOException, ConcurrentRefUpdateException {
		final ObjectId headId = git.getLastCommit(Constants.R_HEADS + name);
		final RefUpdate ru = git.getRepository().updateRef(Constants.R_HEADS + name);
		if (headId == null) {
			ru.setExpectedOldObjectId(ObjectId.zeroId());
		} else {
			ru.setExpectedOldObjectId(headId);
		}
		ru.setNewObjectId(commit.getId());
		ru.setRefLogMessage(commit.getShortMessage(), false);
		forceUpdate(ru, commit.getId());
	}

	private void forceUpdate(final RefUpdate ru, final ObjectId id)
			throws java.io.IOException, ConcurrentRefUpdateException {
		final RefUpdate.Result rc = ru.forceUpdate();
		switch (rc) {
		case NEW:
		case FORCED:
		case FAST_FORWARD:
		case NO_CHANGE:
			break;
		case REJECTED:
		case LOCK_FAILURE:
			throw new ConcurrentRefUpdateException(JGitText.get().couldNotLockHEAD, ru.getRef(), rc);
		default:
			throw new JGitInternalException(
					MessageFormat.format(JGitText.get().updatingRefFailed, Constants.HEAD, id.toString(), rc));
		}
	}
}
