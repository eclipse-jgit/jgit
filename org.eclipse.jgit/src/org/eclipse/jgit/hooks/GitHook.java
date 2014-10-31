/*
 * Copyright (C) 2015 Obeo.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.hooks;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.eclipse.jgit.api.errors.HookFailureException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.Hook;
import org.eclipse.jgit.util.ProcessResult;

/**
 * Hook implementation.
 *
 * @author ldelaigue
 * @since 4.0
 */
class GitHook implements IHook {

	private final Repository repo;

	private final Hook hook;

	private PrintStream outputStream;

	private String[] parameters;

	private String stdinArgs;

	/**
	 * Constructor.
	 *
	 * @param repo
	 * @param hook
	 */
	protected GitHook(Repository repo, Hook hook) {
		if (repo == null) {
			throw new IllegalArgumentException(
					JGitText.get().repositoryIsRequired);
		}
		if (hook == null) {
			throw new IllegalArgumentException(JGitText.get().hookMustNotBeNull);
		}
		this.repo = repo;
		this.hook = hook;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jgit.hooks.IHookBuilder#setOutputStream(java.io.PrintStream)
	 */
	public IHook setOutputStream(PrintStream outputStream) {
		this.outputStream = outputStream;
		return this;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jgit.hooks.IHookBuilder#setParameters(java.lang.String)
	 */
	public IHook setParameters(String... parameters) {
		this.parameters = parameters;
		return this;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jgit.hooks.IHookBuilder#setStdInArg(java.lang.String)
	 */
	public IHook setStdInArg(String stdinArgs) {
		this.stdinArgs = stdinArgs;
		return this;
	}

	/**
	 * @return The outputStream to use in the hook, taking into account the fact
	 *         that is may not have been set. The result is never
	 *         <code>null</code>.
	 */
	protected PrintStream getOutputStream() {
		return outputStream == null ? System.out : outputStream;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jgit.hooks.IHookBuilder#run()
	 */
	public void run() throws IOException, HookFailureException {
		switch (hook) {
		case PRE_COMMIT:
			// Nothing to check here
			break;
		case COMMIT_MSG:
			// Check that there is 1 parameter
			if (parameters == null || parameters.length != 1) {
				throw new IllegalStateException(
						JGitText.get().commitMsgHookRequiresOneParam);
			}
			break;
		default:
			// Do nothing, unsupported
			return;
		}
		doRun();
	}

	private String[] getParameters() {
		if (parameters == null) {
			return new String[0];
		}
		return parameters;
	}

	/**
	 * Runs the hook, without performing any validity checks.
	 *
	 * @throws HookFailureException
	 */
	protected void doRun() throws HookFailureException {
		final ByteArrayOutputStream errorByteArray = new ByteArrayOutputStream();
		final PrintStream hookErrRedirect = new PrintStream(errorByteArray);
		ProcessResult result = FS.DETECTED.runIfPresent(repo, hook,
				getParameters(), getOutputStream(), hookErrRedirect, stdinArgs);
		if (result.getStatus() == ProcessResult.Status.OK
				&& result.getExitCode() != 0) {
			throw new HookFailureException(errorByteArray.toString(), hook,
					result.getExitCode());
		}
	}

}
