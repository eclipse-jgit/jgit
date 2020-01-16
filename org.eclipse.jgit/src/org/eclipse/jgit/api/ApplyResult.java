/*
 * Copyright (C) 2011, 2012 IBM Corporation and others. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the result of a {@link org.eclipse.jgit.api.ApplyCommand}
 *
 * @since 2.0
 */
public class ApplyResult {

	private List<File> updatedFiles = new ArrayList<>();

	/**
	 * Add updated file
	 *
	 * @param f
	 *            an updated file
	 * @return this instance
	 */
	public ApplyResult addUpdatedFile(File f) {
		updatedFiles.add(f);
		return this;

	}

	/**
	 * Get updated files
	 *
	 * @return updated files
	 */
	public List<File> getUpdatedFiles() {
		return updatedFiles;
	}
}
