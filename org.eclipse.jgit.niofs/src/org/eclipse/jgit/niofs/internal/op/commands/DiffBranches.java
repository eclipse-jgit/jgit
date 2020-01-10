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

import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotEmpty;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.niofs.fs.attribute.FileDiff;
import org.eclipse.jgit.niofs.internal.FileDiffImpl;
import org.eclipse.jgit.niofs.internal.op.Git;
import org.eclipse.jgit.niofs.internal.op.exceptions.GitException;
import org.eclipse.jgit.patch.FileHeader;

/**
 * Implements the Git Diff command between branches for bare repositories. It
 * needs the repository, and the two branches from that repository you want to
 * diff. It returns a list of DiffFile with differences between branches.
 */
public class DiffBranches {

	private final Git git;
	private final String branchA;
	private final String branchB;

	public DiffBranches(Git git, String branchA, String branchB) {
		this.git = checkNotNull("git", git);
		this.branchA = checkNotEmpty("branchA", branchA);
		this.branchB = checkNotEmpty("branchB", branchB);
	}

	public List<FileDiff> execute() {
		final List<FileDiff> diffs = new ArrayList<>();

		final List<DiffEntry> result = git.listDiffs(git.getTreeFromRef(this.branchA),
				git.getTreeFromRef(this.branchB));

		final DiffFormatter formatter = createFormatter();

		result.forEach(elem -> {
			final FileHeader header = getFileHeader(formatter, elem);
			header.toEditList().forEach(edit -> diffs.add(createFileDiff(elem, header, edit)));
		});

		return diffs;
	}

	private FileHeader getFileHeader(final DiffFormatter formatter, final DiffEntry elem) {
		try {
			return formatter.toFileHeader(elem);
		} catch (IOException e) {
			throw new GitException("A problem occurred when trying to obtain diffs between files", e);
		}
	}

	private DiffFormatter createFormatter() {

		OutputStream outputStream = new ByteArrayOutputStream();
		DiffFormatter formatter = new DiffFormatter(outputStream);
		formatter.setRepository(git.getRepository());
		return formatter;
	}

	private FileDiff createFileDiff(final DiffEntry elem, final FileHeader header, final Edit edit) {
		try {
			final String changeType = header.getChangeType().toString();
			final int startA = edit.getBeginA();
			final int endA = edit.getEndA();
			final int startB = edit.getBeginB();
			final int endB = edit.getEndB();

			String pathA = header.getOldPath();
			String pathB = header.getNewPath();

			final List<String> linesA = getLines(elem.getOldId().toObjectId(), startA, endA);
			final List<String> linesB = getLines(elem.getNewId().toObjectId(), startB, endB);

			return new FileDiffImpl(pathA, pathB, startA, endA, startB, endB, changeType, linesA, linesB);
		} catch (IOException e) {
			throw new GitException("A problem occurred when trying to obtain diffs between files", e);
		}
	}

	private List<String> getLines(final ObjectId id, final int fromStart, final int fromEnd) throws IOException {
		List<String> lines = new ArrayList<>();
		if (!id.equals(ObjectId.zeroId())) {
			final ByteArrayOutputStream stream = new ByteArrayOutputStream();
			final ObjectLoader loader = git.getRepository().open(id);
			loader.copyTo(stream);
			final String content = stream.toString();
			final List<String> filteredLines = Arrays.asList(content.split("\n"));
			lines = filteredLines.subList(fromStart, fromEnd);
		}
		return lines;
	}
}
