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

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

public class ResolveRevCommit {

	private final Repository repo;
	private final ObjectId objectId;

	public ResolveRevCommit(final Repository repo, final ObjectId objectId) {
		this.repo = repo;
		this.objectId = objectId;
	}

	public RevCommit execute() throws IOException {
		try (final ObjectReader reader = repo.newObjectReader()) {
			return RevCommit.parse(reader.open(objectId).getBytes());
		}
	}
}
