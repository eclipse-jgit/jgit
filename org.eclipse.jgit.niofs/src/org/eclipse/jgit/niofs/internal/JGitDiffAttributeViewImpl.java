/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.List;

import org.eclipse.jgit.niofs.fs.attribute.DiffAttributes;
import org.eclipse.jgit.niofs.fs.attribute.FileDiff;
import org.eclipse.jgit.niofs.fs.attribute.VersionAttributeView;

/**
 *
 */
public class JGitDiffAttributeViewImpl extends DiffAttributeViewImpl<JGitPathImpl> {

	private DiffAttributes attrs = null;
	private final String params;

	public JGitDiffAttributeViewImpl(final JGitPathImpl path, final String params) {
		super(path);
		this.params = params;
	}

	@Override
	public DiffAttributes readAttributes() throws IOException {
		if (attrs == null) {
			attrs = buildAttrs(path.getFileSystem(), params);
		}
		return attrs;
	}

	@Override
	public Class<? extends BasicFileAttributeView>[] viewTypes() {
		return new Class[] { VersionAttributeView.class, VersionAttributeViewImpl.class,
				JGitDiffAttributeViewImpl.class };
	}

	private DiffAttributes buildAttrs(final JGitFileSystem fs, final String params) throws IOException {
		final String[] branches = params.split(",");
		final String branchA = branches[0];
		final String branchB = branches[1];
		final List<FileDiff> diffs = fs.getGit().diffRefs(branchA, branchB);

		return new DiffAttributesImpl(new JGitBasicAttributeView(this.path).readAttributes(), () -> diffs);
	}
}
