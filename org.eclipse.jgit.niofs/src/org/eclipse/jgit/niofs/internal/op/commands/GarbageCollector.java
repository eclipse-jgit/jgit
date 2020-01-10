/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.commands;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.storage.reftree.RefTreeDatabase;
import org.eclipse.jgit.niofs.internal.op.GitImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GarbageCollector {

	private Logger logger = LoggerFactory.getLogger(GarbageCollector.class);

	private final GitImpl git;

	public GarbageCollector(final GitImpl git) {
		this.git = git;
	}

	public void execute() {
		try {
			if (!(git.getRepository().getRefDatabase() instanceof RefTreeDatabase)) {
				git._gc().call();
			}
		} catch (GitAPIException | JGitInternalException e) {
			if (this.logger.isDebugEnabled()) {
				this.logger.error("Garbage collector can't perform this operation right now, please try it later.", e);
			}
		}
	}
}
