/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.extensions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.assertj.core.api.Assertions;
import org.eclipse.jgit.niofs.internal.hook.FileSystemHookExecutionContext;
import org.eclipse.jgit.niofs.internal.hook.FileSystemHooks;
import org.eclipse.jgit.niofs.internal.hook.FileSystemHooksConstants;
import org.eclipse.jgit.niofs.internal.hook.JGitFSHooks;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JGitFSHooksTest {

	private static final String FS_NAME = "dora";
	private static final Integer EXIT_CODE = 0;

	@Captor
	private ArgumentCaptor<FileSystemHookExecutionContext> contextArgumentCaptor;

	@Test
	public void executeFSHooksTest() {

		FileSystemHookExecutionContext ctx = new FileSystemHookExecutionContext(FS_NAME);

		testExecuteFSHooks(ctx, FileSystemHooks.ExternalUpdate);

		ctx.addParam(FileSystemHooksConstants.POST_COMMIT_EXIT_CODE, EXIT_CODE);

		testExecuteFSHooks(ctx, FileSystemHooks.PostCommit);
	}

	private void testExecuteFSHooks(FileSystemHookExecutionContext ctx, FileSystemHooks hookType) {
		AtomicBoolean executedWithLambda = new AtomicBoolean(false);

		FileSystemHooks.FileSystemHook hook = spy(new FileSystemHooks.FileSystemHook() {
			@Override
			public void execute(FileSystemHookExecutionContext context) {
				assertEquals(FS_NAME, context.getFsName());
			}
		});

		FileSystemHooks.FileSystemHook lambdaHook = context -> {
			assertEquals(FS_NAME, context.getFsName());
			executedWithLambda.set(true);
		};

		JGitFSHooks.executeFSHooks(hook, hookType, ctx);
		JGitFSHooks.executeFSHooks(lambdaHook, hookType, ctx);

		verifyFSHook(hook, hookType);

		assertTrue(executedWithLambda.get());
	}

	@Test
	public void executeFSHooksArrayTest() {

		FileSystemHookExecutionContext ctx = new FileSystemHookExecutionContext(FS_NAME);

		testExecuteFSHooksArray(ctx, FileSystemHooks.ExternalUpdate);

		ctx.addParam(FileSystemHooksConstants.POST_COMMIT_EXIT_CODE, EXIT_CODE);

		testExecuteFSHooksArray(ctx, FileSystemHooks.PostCommit);
	}

	private void testExecuteFSHooksArray(FileSystemHookExecutionContext ctx, FileSystemHooks hookType) {

		AtomicBoolean executedWithLambda = new AtomicBoolean(false);

		FileSystemHooks.FileSystemHook hook = spy(new FileSystemHooks.FileSystemHook() {
			@Override
			public void execute(FileSystemHookExecutionContext context) {
				assertEquals(FS_NAME, context.getFsName());
			}
		});

		FileSystemHooks.FileSystemHook lambdaHook = context -> {
			assertEquals(FS_NAME, context.getFsName());
			executedWithLambda.set(true);
		};

		JGitFSHooks.executeFSHooks(Arrays.asList(hook, lambdaHook), hookType, ctx);

		verifyFSHook(hook, hookType);

		assertTrue(executedWithLambda.get());
	}

	private void verifyFSHook(FileSystemHooks.FileSystemHook hook, FileSystemHooks hookType) {
		verify(hook).execute(contextArgumentCaptor.capture());

		FileSystemHookExecutionContext ctx = contextArgumentCaptor.getValue();

		Assertions.assertThat(ctx).isNotNull().hasFieldOrPropertyWithValue("fsName", FS_NAME);

		if (hookType.equals(FileSystemHooks.PostCommit)) {
			Assertions.assertThat(ctx.getParamValue(FileSystemHooksConstants.POST_COMMIT_EXIT_CODE)).isNotNull()
					.isEqualTo(EXIT_CODE);
		}
	}
}