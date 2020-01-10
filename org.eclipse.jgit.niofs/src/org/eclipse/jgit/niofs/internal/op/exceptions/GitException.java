/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.exceptions;

public class GitException extends RuntimeException {

	public GitException(final String message) {
		super(message);
	}

	public GitException(final String message, final Throwable t) {
		super(message, t);
	}
}
