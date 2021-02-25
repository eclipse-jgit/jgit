/*
 * Copyright (C) 2010, 2020 Mathias Kinzler <mathias.kinzler@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api.errors;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.RefUpdate;

/**
 * Thrown when trying to create a {@link org.eclipse.jgit.lib.Ref} with the same
 * name as an existing one
 */
public class RefAlreadyExistsException extends GitAPIException {
	private static final long serialVersionUID = 1L;

	private final RefUpdate.Result updateResult;

	/**
	 * Creates a new instance with the given message.
	 *
	 * @param message
	 *            error message
	 */
	public RefAlreadyExistsException(String message) {
		this(message, null);
	}

	/**
	 * Constructor for RefAlreadyExistsException
	 *
	 * @param message
	 *            error message
	 * @param updateResult
	 *            that caused the exception; may be {@code null}
	 * @since 5.11
	 */
	public RefAlreadyExistsException(String message,
			@Nullable RefUpdate.Result updateResult) {
		super(message);
		this.updateResult = updateResult;
	}

	/**
	 * Retrieves the {@link org.eclipse.jgit.lib.RefUpdate.Result
	 * RefUpdate.Result} that caused the exception.
	 *
	 * @return the {@link org.eclipse.jgit.lib.RefUpdate.Result
	 *         RefUpdate.Result} or {@code null} if unknown
	 * @since 5.11
	 */
	@Nullable
	public RefUpdate.Result getUpdateResult() {
		return updateResult;
	}
}
