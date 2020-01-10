/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.model;

import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotEmpty;

public class TextualDiff {

	private String oldFilePath;
	private String newFilePath;
	private String changeType;
	private int linesAdded;
	private int linesDeleted;
	private String diffText;

	public TextualDiff(final String oldFilePath, final String newFilePath, final String changeType,
			final int linesAdded, final int linesDeleted, final String diffText) {
		this.oldFilePath = checkNotEmpty("oldFilePath", oldFilePath);
		this.newFilePath = checkNotEmpty("newFilePath", newFilePath);
		this.changeType = checkNotEmpty("changeType", changeType);

		this.linesAdded = linesAdded;
		this.linesDeleted = linesDeleted;

		this.diffText = checkNotEmpty("diffText", diffText);
	}

	public String getOldFilePath() {
		return oldFilePath;
	}

	public String getNewFilePath() {
		return newFilePath;
	}

	public String getChangeType() {
		return changeType;
	}

	public int getLinesAdded() {
		return linesAdded;
	}

	public int getLinesDeleted() {
		return linesDeleted;
	}

	public String getDiffText() {
		return diffText;
	}
}
