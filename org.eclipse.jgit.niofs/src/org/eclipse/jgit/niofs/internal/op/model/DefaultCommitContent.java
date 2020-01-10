/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.model;

import java.io.File;
import java.util.Map;

public class DefaultCommitContent implements CommitContent {

	private final Map<String, File> content;

	public DefaultCommitContent(final Map<String, File> content) {
		this.content = content;
	}

	public Map<String, File> getContent() {
		return content;
	}
}
