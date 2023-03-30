/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.errors;

import java.io.IOException;

/**
 * Thrown when a PackFile no longer matches the PackIndex.
 */
public class PackMismatchException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * Type of pack mismatch exception
	 */
	public enum Type {
		/**
		 * Transient problem which maybe can be fixed by rescanning the packlist
		 */
		TRANSIENT,
		/**
		 * Permanent problem which isn't fixed by rescanning packlist
		 */
		PERMANENT
	}

	private Type type;

	/**
	 * Construct a pack modification error.
	 *
	 * @param why
	 *            description of the type of error.
	 */
	public PackMismatchException(String why) {
		super(why);
		this.type = Type.TRANSIENT;
	}

	/**
	 * Set the type of the exception
	 *
	 * @param type
	 *            type of the exception
	 */
	public void setType(Type type) {
		this.type = type;
	}

	/**
	 * Check if this is a permanent problem
	 *
	 * @return if this is a permanent problem and repeatedly scanning the
	 *         packlist couldn't fix it
	 */
	public boolean isPermanent() {
		return type == Type.PERMANENT;
	}

	@Override
	public String toString() {
		return super.toString() + "type: " + type.name(); //$NON-NLS-1$
	}
}
