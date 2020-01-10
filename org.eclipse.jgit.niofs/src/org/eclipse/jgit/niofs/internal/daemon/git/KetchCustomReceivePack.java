/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.daemon.git;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.reftree.RefTreeDatabase;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;

public class KetchCustomReceivePack extends ReceivePack {

	public KetchCustomReceivePack(final Repository into) {
		super(into);
	}

	@Override
	public void setAdvertisedRefs(final Map<String, Ref> allRefs, final Set<ObjectId> additionalHaves) {
		super.setAdvertisedRefs(allRefs, additionalHaves);
		final Map<String, Ref> refs = getAdvertisedRefs();
		if (getRepository().getRefDatabase() instanceof RefTreeDatabase) {
			final RefDatabase bootstrap = ((RefTreeDatabase) getRepository().getRefDatabase()).getBootstrap();
			try {
				for (Map.Entry<String, Ref> entry : bootstrap.getRefs("").entrySet()) {
					refs.put(entry.getKey(), entry.getValue());
				}
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	protected void executeCommands() {
		if (getRepository().getRefDatabase() instanceof RefTreeDatabase) {
			List<ReceiveCommand> toApply = filterCommands(ReceiveCommand.Result.NOT_ATTEMPTED);
			if (toApply.isEmpty()) {
				return;
			}
			final BatchRefUpdate batch = ((RefTreeDatabase) getRepository().getRefDatabase()).getBootstrap()
					.newBatchUpdate();
			batch.setAllowNonFastForwards(true);
			batch.setAtomic(false);
			batch.setRefLogIdent(getRefLogIdent());
			batch.setRefLogMessage("push", true); // $NON-NLS-1$
			batch.addCommand(toApply);
			try {
				batch.setPushCertificate(getPushCertificate());
				batch.execute(getRevWalk(), NullProgressMonitor.INSTANCE);
			} catch (IOException err) {
				for (ReceiveCommand cmd : toApply) {
					if (cmd.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
						cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON,
								MessageFormat.format(JGitText.get().lockError, err.getMessage()));
					}
				}
			}
		} else {
			super.executeCommands();
		}
	}
}
