/*
 * Copyright (C) 2014 Obeo.
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
package org.eclipse.jgit.util;

/**
 * An enum describing the different hooks a user can implement to customize his
 * repositories.
 *
 * @since 3.7
 */
public enum Hook {
	/**
	 * Literal for the "pre-commit" git hook.
	 * <p>
	 * This hook is invoked by git commit, and can be bypassed with the
	 * "no-verify" option. It takes no parameter, and is invoked before
	 * obtaining the proposed commit log message and making a commit.
	 * </p>
	 * <p>
	 * A non-zero exit code from the called hook means that the commit should be
	 * aborted.
	 * </p>
	 */
	PRE_COMMIT("pre-commit"), //$NON-NLS-1$

	/**
	 * Literal for the "prepare-commit-msg" git hook.
	 * <p>
	 * This hook is invoked by git commit right after preparing the default
	 * message, and before any editing possibility is displayed to the user.
	 * </p>
	 * <p>
	 * A non-zero exit code from the called hook means that the commit should be
	 * aborted.
	 * </p>
	 */
	PREPARE_COMMIT_MSG("prepare-commit-msg"), //$NON-NLS-1$

	/**
	 * Literal for the "commit-msg" git hook.
	 * <p>
	 * This hook is invoked by git commit, and can be bypassed with the
	 * "no-verify" option. Its single parameter is the path to the file
	 * containing the prepared commit message (typically
	 * "&lt;gitdir>/COMMIT-EDITMSG").
	 * </p>
	 * <p>
	 * A non-zero exit code from the called hook means that the commit should be
	 * aborted.
	 * </p>
	 */
	COMMIT_MSG("commit-msg"), //$NON-NLS-1$

	/**
	 * Literal for the "post-commit" git hook.
	 * <p>
	 * This hook is invoked by git commit. It takes no parameter and is invoked
	 * after a commit has been made.
	 * </p>
	 * <p>
	 * The exit code of this hook has no significance.
	 * </p>
	 */
	POST_COMMIT("post-commit"), //$NON-NLS-1$

	/**
	 * Literal for the "post-rewrite" git hook.
	 * <p>
	 * This hook is invoked after commands that rewrite commits (currently, only
	 * "git rebase" and "git commit --amend"). It a single argument denoting the
	 * source of the call (one of <code>rebase</code> or <code>amend</code>). It
	 * then accepts a list of rewritten commits through stdin, in the form
	 * <code>&lt;old SHA-1> &lt;new SHA-1>LF</code>.
	 * </p>
	 * <p>
	 * The exit code of this hook has no significance.
	 * </p>
	 */
	POST_REWRITE("post-rewrite"), //$NON-NLS-1$

	/**
	 * Literal for the "pre-rebase" git hook.
	 * <p>
	 * </p>
	 * This hook is invoked right before the rebase operation runs. It accepts
	 * up to two parameters, the first being the upstream from which the branch
	 * to rebase has been forked. If the tip of the series of commits to rebase
	 * is HEAD, the other parameter is unset. Otherwise, that tip is passed as
	 * the second parameter of the script.
	 * <p>
	 * A non-zero exit code from the called hook means that the rebase should be
	 * aborted.
	 * </p>
	 */
	PRE_REBASE("pre-rebase"); //$NON-NLS-1$

	private final String name;

	private Hook(String name) {
		this.name = name;
	}

	/**
	 * @return The name of this hook.
	 */
	public String getName() {
		return name;
	}
}
