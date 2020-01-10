/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.hook;

public enum FileSystemHooks {

	ExternalUpdate, PostCommit, BranchAccessCheck, BranchAccessFilter;

	@FunctionalInterface
	public interface FileSystemHook {

		/**
		 * Performs this operation in a FileSystemHooks
		 * 
		 * @param context execution context
		 */
		void execute(FileSystemHookExecutionContext context);
	}
}
