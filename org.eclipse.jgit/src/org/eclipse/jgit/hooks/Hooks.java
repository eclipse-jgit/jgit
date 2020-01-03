/*
 * Copyright (C) 2015 Obeo. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
	 * Create pre-commit hook for the given repository with the default error
	 * stream
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
	 * Create pre-commit hook for the given repository
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 * @param outputStream
	 *            The output stream, or {@code null} to use {@code System.out}
	 * @param errorStream
	 *            The error stream, or {@code null} to use {@code System.err}
	 * @return The pre-commit hook for the given repository.
	 * @since 5.6
	 */
	public static PreCommitHook preCommit(Repository repo,
			PrintStream outputStream, PrintStream errorStream) {
		return new PreCommitHook(repo, outputStream, errorStream);
	}

	/**
	 * Create post-commit hook for the given repository with the default error
	 * stream
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
	 * Create post-commit hook for the given repository
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 * @param outputStream
	 *            The output stream, or {@code null} to use {@code System.out}
	 * @param errorStream
	 *            The error stream, or {@code null} to use {@code System.err}
	 * @return The pre-commit hook for the given repository.
	 * @since 5.6
	 */
	public static PostCommitHook postCommit(Repository repo,
			PrintStream outputStream, PrintStream errorStream) {
		return new PostCommitHook(repo, outputStream, errorStream);
	}

	/**
	 * Create commit-msg hook for the given repository with the default error
	 * stream
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
	 * Create commit-msg hook for the given repository
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 * @param outputStream
	 *            The output stream, or {@code null} to use {@code System.out}
	 * @param errorStream
	 *            The error stream, or {@code null} to use {@code System.err}
	 * @return The pre-commit hook for the given repository.
	 * @since 5.6
	 */
	public static CommitMsgHook commitMsg(Repository repo,
			PrintStream outputStream, PrintStream errorStream) {
		return new CommitMsgHook(repo, outputStream, errorStream);
	}

	/**
	 * Create pre-push hook for the given repository with the default error
	 * stream
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
					PrintStream ps = outputStream;
					if (ps == null) {
						ps = System.out;
					}
					ps.println(MessageFormat
							.format(JGitText.get().lfsHookConflict, repo));
				}
				return hook;
			}
		}
		return new PrePushHook(repo, outputStream);
	}

	/**
	 * Create pre-push hook for the given repository
	 *
	 * @param repo
	 *            a {@link org.eclipse.jgit.lib.Repository} object.
	 * @param outputStream
	 *            The output stream, or {@code null} to use {@code System.out}
	 * @param errorStream
	 *            The error stream, or {@code null} to use {@code System.err}
	 * @return The pre-push hook for the given repository.
	 * @since 5.6
	 */
	public static PrePushHook prePush(Repository repo, PrintStream outputStream,
			PrintStream errorStream) {
		if (LfsFactory.getInstance().isAvailable()) {
			PrePushHook hook = LfsFactory.getInstance().getPrePushHook(repo,
					outputStream, errorStream);
			if (hook != null) {
				if (hook.isNativeHookPresent()) {
					PrintStream ps = outputStream;
					if (ps == null) {
						ps = System.out;
					}
					ps.println(MessageFormat
							.format(JGitText.get().lfsHookConflict, repo));
				}
				return hook;
			}
		}
		return new PrePushHook(repo, outputStream, errorStream);
	}
}
