/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.fs.options;

import org.eclipse.jgit.niofs.fs.attribute.VersionRecord;

public class SquashOption extends CommentedOption {

	public static final String SQUASH_ATTR = "SQUASH_ATTR";
	public VersionRecord versionRecord;

	public SquashOption(VersionRecord record) {
		super(null, record.author(), record.email(), record.comment(), record.date(), null);
		this.setRecord(record);
	}

	public VersionRecord getRecord() {
		return versionRecord;
	}

	public void setRecord(final VersionRecord versionRecord) {
		this.versionRecord = versionRecord;
	}
}
