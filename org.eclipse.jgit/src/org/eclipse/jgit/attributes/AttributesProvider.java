/*
 * Copyright (C) 2015, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.attributes;

/**
 * Interface for classes which provide git attributes
 *
 * @since 4.2
 */
public interface AttributesProvider {
	/**
	 * Get attributes
	 *
	 * @return the currently active attributes
	 */
	Attributes getAttributes();
}
