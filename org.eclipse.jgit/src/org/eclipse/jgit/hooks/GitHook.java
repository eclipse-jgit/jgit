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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.Callable;

import org.bouncycastle.util.io.TeeOutputStream;
import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.ProcessResult;

/**
 * Git can fire off custom scripts when certain important actions occur. These
 * custom scripts are called "hooks". There are two groups of hooks: client-side
 * (that run on local operations such as committing and merging), and
 * server-side (that run on network operations such as receiving pushed
 * commits). This is the abstract super-class of the different hook
 * implementations in JGit.
 *
 * @param <T>
 *            the return type which is expected from {@link #call()}
 * @see <a href="http://git-scm.com/book/en/v2/Customizing-Git-Git-Hooks">Git
 *      Hooks on the git-scm official site</a>
 * @since 4.0
 */
abstract class GitHook<T> implements Callable<T> {

	private final Repository repo;

	/**
	 * The output stream to be used by the hook.
	 */
	protected final PrintStream outputStream;

	/**
	 * The error stream to be used by the hook.
	 */
	protected final PrintStream errorStream;

	/**
	 * Constructor for GitHook.
	 * <p>
	 * This constructor will use stderr for the error stream.
	 * </p>
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 * @param outputStream
	 *            The output stream the hook must use. {@code null} is allowed,
	 *            in which case the hook will use {@code System.out}.
	 */
	protected GitHook(Repository repo, PrintStream outputStream) {
		this(repo, outputStream, null);
	}

	/**
	 * Constructor for GitHook
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 * @param outputStream
	 *            The output stream the hook must use. {@code null} is allowed,
	 *            in which case the hook will use {@code System.out}.
	 * @param errorStream
	 *            The error stream the hook must use. {@code null} is allowed,
	 *            in which case the hook will use {@code System.err}.
	 */
	protected GitHook(Repository repo, PrintStream outputStream,
			PrintStream errorStream) {
		this.repo = repo;
		this.outputStream = outputStream;
		this.errorStream = errorStream;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Run the hook.
	 */
	@Override
	public abstract T call() throws IOException, AbortedByHookException;

	/**
	 * Get name of the hook
	 *
	 * @return The name of the hook, which must not be {@code null}.
	 */
	public abstract String getHookName();

	/**
	 * Get the repository
	 *
	 * @return The repository.
	 */
	protected Repository getRepository() {
		return repo;
	}

	/**
	 * Override this method when needed to provide relevant parameters to the
	 * underlying hook script. The default implementation returns an empty
	 * array.
	 *
	 * @return The parameters the hook receives.
	 */
	protected String[] getParameters() {
		return new String[0];
	}

	/**
	 * Override to provide relevant arguments via stdin to the underlying hook
	 * script. The default implementation returns {@code null}.
	 *
	 * @return The parameters the hook receives.
	 */
	protected String getStdinArgs() {
		return null;
	}

	/**
	 * Get output stream
	 *
	 * @return The output stream the hook must use. Never {@code null},
	 *         {@code System.out} is returned by default.
	 */
	protected PrintStream getOutputStream() {
		return outputStream == null ? System.out : outputStream;
	}

	/**
	 * Get error stream
	 *
	 * @return The error stream the hook must use. Never {@code null},
	 *         {@code System.err} is returned by default.
	 */
	protected PrintStream getErrorStream() {
		return errorStream == null ? System.err : errorStream;
	}

	/**
	 * Runs the hook, without performing any validity checks.
	 *
	 * @throws org.eclipse.jgit.api.errors.AbortedByHookException
	 *             If the underlying hook script exited with non-zero.
	 */
	protected void doRun() throws AbortedByHookException {
		final ByteArrayOutputStream errorByteArray = new ByteArrayOutputStream();
		final TeeOutputStream stderrStream = new TeeOutputStream(errorByteArray,
				getErrorStream());
		PrintStream hookErrRedirect = null;
		try {
			hookErrRedirect = new PrintStream(stderrStream, false,
					UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			// UTF-8 is guaranteed to be available
		}
		Repository repository = getRepository();
		FS fs = repository.getFS();
		if (fs == null) {
			fs = FS.DETECTED;
		}
		ProcessResult result = fs.runHookIfPresent(repository, getHookName(),
				getParameters(), getOutputStream(), hookErrRedirect,
				getStdinArgs());
		if (result.isExecutedWithError()) {
			throw new AbortedByHookException(
					new String(errorByteArray.toByteArray(), UTF_8),
					getHookName(), result.getExitCode());
		}
	}

	/**
	 * Check whether a 'native' (i.e. script) hook is installed in the
	 * repository.
	 *
	 * @return whether a native hook script is installed in the repository.
	 * @since 4.11
	 */
	public boolean isNativeHookPresent() {
		FS fs = getRepository().getFS();
		if (fs == null) {
			fs = FS.DETECTED;
		}
		return fs.findHook(getRepository(), getHookName()) != null;
	}

}
