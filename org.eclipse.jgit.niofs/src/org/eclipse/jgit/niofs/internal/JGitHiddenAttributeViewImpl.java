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

import org.eclipse.jgit.niofs.fs.attribute.HiddenAttributeView;
import org.eclipse.jgit.niofs.fs.attribute.HiddenAttributes;
import org.eclipse.jgit.niofs.internal.daemon.filter.HiddenBranchRefFilter;

/**
 * This is the JGit implementation of the {@link HiddenAttributeViewImpl}. It
 * builds the HiddenAttributes object with "isHidden" attribute information.
 * That attribute lets you know if the branch you are querying is a hidden
 * branch or not. Hidden branches should not be used, are just a mechanism to
 * merge.
 */
public class JGitHiddenAttributeViewImpl extends HiddenAttributeViewImpl<JGitPathImpl> {

	private HiddenAttributes attrs = null;

	public JGitHiddenAttributeViewImpl(final JGitPathImpl path) {
		super(path);
	}

	@Override
	public HiddenAttributes readAttributes() throws IOException {
		if (attrs == null) {
			attrs = buildAttrs(path.getFileSystem(), path.getRefTree(), path.getPath());
		}
		return attrs;
	}

	@Override
	public Class<? extends BasicFileAttributeView>[] viewTypes() {
		return new Class[] { HiddenAttributeView.class, HiddenAttributeViewImpl.class,
				JGitVersionAttributeViewImpl.class };
	}

	private HiddenAttributes buildAttrs(final JGitFileSystem fileSystem, final String refTree, final String path)
			throws IOException {
		return new HiddenAttributesImpl(new JGitBasicAttributeView(this.path).readAttributes(),
				HiddenBranchRefFilter.isHidden(refTree));
	}
}
