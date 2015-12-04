/*
 * Copyright (C) 2016, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
 * Install an instance on {@link ReceivePack} to capture the commands and other
 * connection state and relay them through the {@link KetchLeader}, allowing the
 * leader to gain consensus about the new reference state.
 */
public class KetchPreReceive implements PreReceiveHook {
	private static final Logger log = LoggerFactory.getLogger(KetchPreReceive.class);

	private final KetchLeader leader;

	/**
	 * Construct a hook executing updates through a {@link KetchLeader}.
	 *
	 * @param leader
	 *            leader for this repository.
	 */
	public KetchPreReceive(KetchLeader leader) {
		this.leader = leader;
	}

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
