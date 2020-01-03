/*
 * Copyright (C) 2013, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.errors;

/**
 * Attempt to modify a rebase-todo file in an unsupported way
 *
 * @since 3.2
 */
public class IllegalTodoFileModification extends Exception {
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor for IllegalTodoFileModification
	 *
	 * @param msg
	 *            error message
	 */
	public IllegalTodoFileModification(String msg) {
		super(msg);
	}
}
