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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class ListRefs {

	private final Repository repo;

	public ListRefs(final Repository repo) {
		this.repo = repo;
	}

	public List<Ref> execute() {
		try {
			return new ArrayList<>(repo.getRefDatabase().getRefsByPrefix("refs/heads/"));
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}
}
