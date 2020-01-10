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

import java.nio.file.attribute.BasicFileAttributes;

import org.eclipse.jgit.niofs.fs.attribute.BranchDiff;
import org.eclipse.jgit.niofs.fs.attribute.DiffAttributes;

public class DiffAttributesImpl extends AbstractJGitBasicAttributesImpl implements DiffAttributes {

	private final BranchDiff branchDiff;

	public DiffAttributesImpl(final BasicFileAttributes attributes, final BranchDiff branchDiff) {
		super(attributes);
		this.branchDiff = branchDiff;
	}

	@Override
	public BranchDiff branchDiff() {
		return branchDiff;
	}
}
