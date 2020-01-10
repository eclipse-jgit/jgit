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

import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotEmpty;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jgit.merge.MergeResult;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.exceptions.GitException;
import org.eclipse.jgit.revwalk.RevCommit;

public class ConflictBranchesChecker {

	private final Git git;
	private final String branchA;
	private final String branchB;

	public ConflictBranchesChecker(final Git git, final String branchA, final String branchB) {
		this.git = checkNotNull("git", git);
		this.branchA = checkNotEmpty("branchA", branchA);
		this.branchB = checkNotEmpty("branchB", branchB);
	}

	public List<String> execute() {
		BranchUtil.existsBranch(this.git, this.branchA);

		BranchUtil.existsBranch(this.git, this.branchB);

		List<String> result = new ArrayList<>();

		try {
			final RevCommit commitA = git.getLastCommit(branchA);
			final RevCommit commitB = git.getLastCommit(branchB);

			final RevCommit commonAncestor = git.getCommonAncestorCommit(branchA, branchB);

			ThreeWayMerger merger = MergeStrategy.RECURSIVE.newMerger(git.getRepository(), true);
			merger.setBase(commonAncestor);

			boolean canMerge = merger.merge(commitA, commitB);

			if (!canMerge) {
				ResolveMerger resolveMerger = (ResolveMerger) merger;
				Map<String, MergeResult<?>> mergeResults = resolveMerger.getMergeResults();
				result.addAll(mergeResults.keySet().stream().sorted(String::compareToIgnoreCase)
						.collect(Collectors.toList()));
			}
		} catch (IOException e) {
			throw new GitException(String.format("Error when checking for conflicts between branches %s and %s: %s",
					this.branchA, this.branchB, e));
		}

		return result;
	}
}
