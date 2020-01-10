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

import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import static java.util.Collections.emptyList;

public class ListDiffs {

	private final Git git;
	private final ObjectId oldRef;
	private final ObjectId newRef;

	public ListDiffs(final Git git, final ObjectId oldRef, final ObjectId newRef) {
		this.git = git;
		this.oldRef = oldRef;
		this.newRef = newRef;
	}

	public List<DiffEntry> execute() {
		if (newRef == null || git.getRepository() == null) {
			return emptyList();
		}

		try (final ObjectReader reader = git.getRepository().newObjectReader()) {
			CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
			if (oldRef != null) {
				oldTreeIter.reset(reader, oldRef);
			}
			CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
			newTreeIter.reset(reader, newRef);
			return new CustomDiffCommand(git).setNewTree(newTreeIter).setOldTree(oldTreeIter)
					.setShowNameAndStatusOnly(true).call();
		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
