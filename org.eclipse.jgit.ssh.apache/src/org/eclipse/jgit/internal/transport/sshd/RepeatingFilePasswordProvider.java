/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import org.apache.sshd.common.config.keys.FilePasswordProvider;

/**
 * A {@link FilePasswordProvider} augmented to support repeatedly asking for
 * passwords.
 *
 */
public interface RepeatingFilePasswordProvider extends FilePasswordProvider {

	/**
	 * Define the maximum number of attempts to get a password that should be
	 * attempted for one identity resource through this provider.
	 *
	 * @param numberOfPasswordPrompts
	 *            number of times to ask for a password;
	 *            {@link IllegalArgumentException} may be thrown if <= 0
	 */
	void setAttempts(int numberOfPasswordPrompts);

	/**
	 * Gets the maximum number of attempts to get a password that should be
	 * attempted for one identity resource through this provider.
	 *
	 * @return the maximum number of attempts to try, always >= 1.
	 */
	default int getAttempts() {
		return 1;
	}

}
