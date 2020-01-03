/*
 * Copyright (C) 2011, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.submodule;

/**
 * Enumeration of different statuses that a submodule can be in
 */
public enum SubmoduleStatusType {

	/** Submodule's configuration is missing */
	MISSING,

	/** Submodule's Git repository is not initialized */
	UNINITIALIZED,

	/** Submodule's Git repository is initialized */
	INITIALIZED,

	/**
	 * Submodule commit checked out is different than the commit referenced in
	 * the index tree
	 */
	REV_CHECKED_OUT;
}
