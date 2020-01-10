/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.fs.options;

import java.nio.file.CopyOption;

public class CherryPickCopyOption implements CopyOption {

	private final String[] commits;

	public CherryPickCopyOption(final String... commits) {
		this.commits = commits;
	}

	public String[] getCommits() {
		return commits;
	}
}
