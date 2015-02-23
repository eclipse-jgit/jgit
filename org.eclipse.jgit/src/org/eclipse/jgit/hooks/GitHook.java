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
import java.util.concurrent.Callable;

import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.Hook;
import org.eclipse.jgit.util.ProcessResult;

/**
 * A HookRunner is responsible for executing a certain kind of hook at certain
 * points of git commands execution.
 *
 * @param <T>
 *            the return type which is expected from {@link #call()}
 * @since 4.0
 */
public abstract class GitHook<T> implements Callable<T> {

	private final Repository repo;

	/**
	 * The output stream to be used by the hook.
	 */
	protected final PrintStream outputStream;

	/**
	 * @param repo
	 * @param outputStream
	 *            The output stream the hook must use. {@code null} is allowed,
	 *            in which case the hook will use {@code System.out}.
	 */
	protected GitHook(Repository repo, PrintStream outputStream) {
		this.repo = repo;
		this.outputStream = outputStream;
	}

	/**
	 * Run the hook.
	 *
	 * @throws IOException
	 *             if IO goes wrong.
	 * @throws AbortedByHookException
	 *             If the hook has been run and a returned an exit code
	 *             different from zero.
	 */
	public abstract T call() throws IOException, AbortedByHookException;

	/**
	 * @return The name of the hook, which must not be {@code null}.
	 */
	public abstract Hook getHookType();

	/**
	 * @return The repository.
	 */
	protected Repository getRepository() {
		return repo;
	}

	/**
	 * @return The parameters the hook receives.
	 */
	protected abstract String[] getParameters();

	/**
	 * @return The parameters the hook receives.
	 */
	protected abstract String getStdinArgs();

	/**
	 * @return The output stream the hook must use.
	 */
	protected PrintStream getOutputStream() {
		return outputStream == null ? System.out : outputStream;
	}

	/**
	 * Runs the hook, without performing any validity checks.
	 *
	 * @throws AbortedByHookException
	 */
	protected void doRun() throws AbortedByHookException {
		final ByteArrayOutputStream errorByteArray = new ByteArrayOutputStream();
		final PrintStream hookErrRedirect = new PrintStream(errorByteArray);
		ProcessResult result = FS.DETECTED.runIfPresent(getRepository(),
				getHookType(), getParameters(), getOutputStream(),
				hookErrRedirect, getStdinArgs());
		if (result.isExecutedWithError()) {
			throw new AbortedByHookException(errorByteArray.toString(),
					getHookType(), result.getExitCode());
		}
	}

}