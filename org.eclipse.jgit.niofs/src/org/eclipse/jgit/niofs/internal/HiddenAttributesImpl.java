/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.nio.file.attribute.BasicFileAttributes;

import org.eclipse.jgit.niofs.fs.attribute.HiddenAttributes;

/**
 * HiddenAttribute implementation. Receives a BasicFIleAttributes, and if file
 * is hidden or not so creates a new object that has all those attributes
 * together.
 */
public class HiddenAttributesImpl extends AbstractJGitBasicAttributesImpl implements HiddenAttributes {

	private final boolean hidden;

	public HiddenAttributesImpl(final BasicFileAttributes attributes, final boolean isHidden) {
		super(attributes);
		this.hidden = isHidden;
	}

	@Override
	public boolean isHidden() {
		return this.hidden;
	}
}
