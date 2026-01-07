/*
 * Copyright (C) 2026 David Baker Effendi <david@brokk.ai> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.signing;

/**
 * Configuration for GPG process execution, specifically for GUI environments.
 *
 * @since 7.6
 */
public class GpgProcessConfiguration {

	private boolean removeGuiIncompatibleEnv = false;

	private boolean rejectTerminalPrompt = false;

	/**
	 * Default constructor.
	 */
	public GpgProcessConfiguration() {
		// Default values already set
	}

	/**
	 * Whether to remove environment variables that may be incompatible with GUI
	 * environments.
	 * <p>
	 * If {@code true}, environment variables such as {@code PINENTRY_USER_DATA}
	 * (which may prevent custom pinentry configuration) and {@code GPG_TTY}
	 * (which may force GPG to use the terminal for passphrase input) are
	 * removed from the GPG process environment.
	 * </p>
	 *
	 * @return {@code true} if GUI-incompatible environment variables should be
	 *         removed
	 */
	public boolean isRemoveGuiIncompatibleEnv() {
		return removeGuiIncompatibleEnv;
	}

	/**
	 * Configures whether to remove environment variables that may be
	 * incompatible with GUI environments.
	 *
	 * @param remove
	 *            {@code true} to remove variables like {@code GPG_TTY} and
	 *            {@code PINENTRY_USER_DATA}
	 * @return {@code this}
	 */
	public GpgProcessConfiguration setRemoveGuiIncompatibleEnv(boolean remove) {
		this.removeGuiIncompatibleEnv = remove;
		return this;
	}

	/**
	 * Whether to reject GPG launches that would result in a terminal-based
	 * passphrase prompt.
	 * <p>
	 * If {@code true}, the execution should fail (typically by throwing an
	 * exception) if GPG launches a pinentry of type {@code tty} or
	 * {@code curses}.
	 * </p>
	 *
	 * @return {@code true} if terminal-based prompts should be rejected
	 */
	public boolean isRejectTerminalPrompt() {
		return rejectTerminalPrompt;
	}

	/**
	 * Configures whether to reject GPG launches that would result in a
	 * terminal-based passphrase prompt.
	 *
	 * @param reject
	 *            {@code true} to reject terminal-based pinentry
	 * @return {@code this}
	 */
	public GpgProcessConfiguration setRejectTerminalPrompt(boolean reject) {
		this.rejectTerminalPrompt = reject;
		return this;
	}
}
