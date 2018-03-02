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

import java.io.PrintStream;
import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.LfsFactory;

/**
 * Factory class for instantiating supported hooks.
 *
 * @since 4.0
 */
public class Hooks {

	/**
	 * Create pre-commit hook for the given repository
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 * @param outputStream
	 *            The output stream, or {@code null} to use {@code System.out}
	 * @return The pre-commit hook for the given repository.
	 */
	public static PreCommitHook preCommit(Repository repo,
			PrintStream outputStream) {
		return new PreCommitHook(repo, outputStream);
	}

	/**
	 * Create post-commit hook for the given repository
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 * @param outputStream
	 *            The output stream, or {@code null} to use {@code System.out}
	 * @return The post-commit hook for the given repository.
	 * @since 4.5
	 */
	public static PostCommitHook postCommit(Repository repo,
			PrintStream outputStream) {
		return new PostCommitHook(repo, outputStream);
	}

	/**
	 * Create commit-msg hook for the given repository
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 * @param outputStream
	 *            The output stream, or {@code null} to use {@code System.out}
	 * @return The commit-msg hook for the given repository.
	 */
	public static CommitMsgHook commitMsg(Repository repo,
			PrintStream outputStream) {
		return new CommitMsgHook(repo, outputStream);
	}

	/**
	 * Create pre-push hook for the given repository
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 * @param outputStream
	 *            The output stream, or {@code null} to use {@code System.out}
	 * @return The pre-push hook for the given repository.
	 * @since 4.2
	 */
	public static PrePushHook prePush(Repository repo, PrintStream outputStream) {
		if (LfsFactory.getInstance().isAvailable()) {
			PrePushHook hook = LfsFactory.getInstance().getPrePushHook(repo,
					outputStream);
			if (hook != null) {
				if (hook.isNativeHookPresent()) {
					throw new IllegalStateException(MessageFormat
							.format(JGitText.get().lfsHookConflict, repo));
				}
				return hook;
			}
		}
		return new PrePushHook(repo, outputStream);
	}
}
