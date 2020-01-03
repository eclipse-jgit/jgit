/*
 * Copyright (C) 2019, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.util;

/**
 * Utility methods for object references
 *
 * @since 5.4
 */
public interface References {

	/**
	 * Compare two references
	 *
	 * @param <T>
	 *            type of the references
	 * @param ref1
	 *            first reference
	 * @param ref2
	 *            second reference
	 * @return {@code true} if both references refer to the same object
	 */
	@SuppressWarnings("ReferenceEquality")
	public static <T> boolean isSameObject(T ref1, T ref2) {
		return ref1 == ref2;
	}
}
