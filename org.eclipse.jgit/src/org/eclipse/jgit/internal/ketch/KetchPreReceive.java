/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.ketch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.jgit.internal.ketch.Proposal.State.EXECUTED;
import static org.eclipse.jgit.internal.ketch.Proposal.State.QUEUED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ProgressSpinner;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PreReceiveHook for handling push traffic in a Ketch system.
 * <p>
 * Install an instance on {@link org.eclipse.jgit.transport.ReceivePack} to
 * capture the commands and other connection state and relay them through the
 * {@link org.eclipse.jgit.internal.ketch.KetchLeader}, allowing the leader to
 * gain consensus about the new reference state.
 */
public class KetchPreReceive implements PreReceiveHook {
	private static final Logger log = LoggerFactory.getLogger(KetchPreReceive.class);

	private final KetchLeader leader;

	/**
	 * Construct a hook executing updates through a
	 * {@link org.eclipse.jgit.internal.ketch.KetchLeader}.
	 *
	 * @param leader
	 *            leader for this repository.
	 */
	public KetchPreReceive(KetchLeader leader) {
		this.leader = leader;
	}

	/** {@inheritDoc} */
	@Override
	public void onPreReceive(ReceivePack rp, Collection<ReceiveCommand> cmds) {
		cmds = ReceiveCommand.filter(cmds, NOT_ATTEMPTED);
		if (cmds.isEmpty()) {
			return;
		}

		try {
			Proposal proposal = new Proposal(rp.getRevWalk(), cmds)
				.setPushCertificate(rp.getPushCertificate())
				.setAuthor(rp.getRefLogIdent())
				.setMessage("push"); //$NON-NLS-1$
			leader.queueProposal(proposal);
			if (proposal.isDone()) {
				// This failed fast, e.g. conflict or bad precondition.
				return;
			}

			ProgressSpinner spinner = new ProgressSpinner(
					rp.getMessageOutputStream());
			if (proposal.getState() == QUEUED) {
				waitForQueue(proposal, spinner);
			}
			if (!proposal.isDone()) {
				waitForPropose(proposal, spinner);
			}
		} catch (IOException | InterruptedException e) {
			String msg = JGitText.get().transactionAborted;
			for (ReceiveCommand cmd : cmds) {
				if (cmd.getResult() == NOT_ATTEMPTED) {
					cmd.setResult(REJECTED_OTHER_REASON, msg);
				}
			}
			log.error(msg, e);
		}
	}

	private void waitForQueue(Proposal proposal, ProgressSpinner spinner)
			throws InterruptedException {
		spinner.beginTask(KetchText.get().waitingForQueue, 1, SECONDS);
		while (!proposal.awaitStateChange(QUEUED, 250, MILLISECONDS)) {
			spinner.update();
		}
		switch (proposal.getState()) {
		case RUNNING:
		default:
			spinner.endTask(KetchText.get().starting);
			break;

		case EXECUTED:
			spinner.endTask(KetchText.get().accepted);
			break;

		case ABORTED:
			spinner.endTask(KetchText.get().failed);
			break;
		}
	}

	private void waitForPropose(Proposal proposal, ProgressSpinner spinner)
			throws InterruptedException {
		spinner.beginTask(KetchText.get().proposingUpdates, 2, SECONDS);
		while (!proposal.await(250, MILLISECONDS)) {
			spinner.update();
		}
		spinner.endTask(proposal.getState() == EXECUTED
				? KetchText.get().accepted
				: KetchText.get().failed);
	}
}
