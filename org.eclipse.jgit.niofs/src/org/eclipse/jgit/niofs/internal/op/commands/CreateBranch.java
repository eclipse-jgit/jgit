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

import org.eclipse.jgit.niofs.internal.op.GitImpl;

public class CreateBranch {

	private final GitImpl git;
	private final String source;
	private final String target;

	public CreateBranch(final GitImpl git, final String source, final String target) {
		this.git = git;
		this.source = source;
		this.target = target;
	}

	public void execute() {
		try {
			git.refUpdate(target, git.resolveRevCommit(git.resolveObjectIds(source).get(0)));
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}
}
