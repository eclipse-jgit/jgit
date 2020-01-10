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

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.niofs.internal.op.Git;

public class ResolveObjectIds {

	private final Git git;
	private final String[] ids;

	public ResolveObjectIds(final Git git, final String... ids) {
		this.git = git;
		this.ids = ids;
	}

	public List<ObjectId> execute() {
		final List<ObjectId> result = new ArrayList<>();

		for (final String id : ids) {
			try {
				final Ref refName = git.getRef(id);
				if (refName != null) {
					result.add(refName.getObjectId());
					continue;
				}

				try {
					final ObjectId _id = ObjectId.fromString(id);
					if (git.getRepository().getObjectDatabase().has(_id)) {
						result.add(_id);
					}
				} catch (final IllegalArgumentException ignored) {
				}
			} catch (final java.io.IOException ignored) {
			}
		}

		return result;
	}
}
