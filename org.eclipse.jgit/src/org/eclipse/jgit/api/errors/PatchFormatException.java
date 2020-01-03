/*
 * Copyright (C) 2012, IBM Corporation and others. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api.errors;

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.patch.FormatError;

/**
 * Exception thrown when applying a patch fails due to an invalid format
 *
 * @since 2.0
 */
public class PatchFormatException extends GitAPIException {
	private static final long serialVersionUID = 1L;

	private List<FormatError> errors;

	/**
	 * Constructor for PatchFormatException
	 *
	 * @param errors
	 *            a {@link java.util.List} of {@link FormatError}s
	 */
	public PatchFormatException(List<FormatError> errors) {
		super(MessageFormat.format(JGitText.get().patchFormatException, errors));
		this.errors = errors;
	}

	/**
	 * Get list of errors
	 *
	 * @return all the errors where unresolved conflicts have been detected
	 */
	public List<FormatError> getErrors() {
		return errors;
	}

}
