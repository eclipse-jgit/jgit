/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.hook;

import java.util.List;

import org.eclipse.jgit.niofs.internal.JGitFileSystemImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JGitFSHooks {

	private static final Logger LOGGER = LoggerFactory.getLogger(JGitFileSystemImpl.class);

	public static void executeFSHooks(Object fsHook, FileSystemHooks hookType, FileSystemHookExecutionContext ctx) {
		if (fsHook == null) {
			return;
		}
		if (fsHook instanceof List) {
			List hooks = (List) fsHook;
			hooks.forEach(h -> executeHook(h, hookType, ctx));
		} else {
			executeHook(fsHook, hookType, ctx);
		}
	}

	private static void executeHook(Object hook, FileSystemHooks hookType, FileSystemHookExecutionContext ctx) {
		if (hook instanceof FileSystemHooks.FileSystemHook) {
			FileSystemHooks.FileSystemHook fsHook = (FileSystemHooks.FileSystemHook) hook;
			fsHook.execute(ctx);
		} else {
			LOGGER.error("Error executing FS Hook FS " + hookType + " on " + ctx.getFsName()
					+ ". Callback methods should implement FileSystemHooks.FileSystemHook. ");
		}
	}
}
