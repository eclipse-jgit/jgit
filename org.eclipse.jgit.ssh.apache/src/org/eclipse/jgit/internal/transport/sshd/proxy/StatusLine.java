/*
 * Copyright (C) 2018, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd.proxy;

/**
 * A very simple representation of a HTTP status line.
 */
public class StatusLine {

	private final String version;

	private final int resultCode;

	private final String reason;

	/**
	 * Create a new {@link StatusLine} with the given response code and reason
	 * string.
	 *
	 * @param version
	 *            the version string (normally "HTTP/1.1" or "HTTP/1.0")
	 * @param resultCode
	 *            the HTTP response code (200, 401, etc.)
	 * @param reason
	 *            the reason phrase for the code
	 */
	public StatusLine(String version, int resultCode, String reason) {
		this.version = version;
		this.resultCode = resultCode;
		this.reason = reason;
	}

	/**
	 * Retrieves the version string.
	 *
	 * @return the version string
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * Retrieves the HTTP response code.
	 *
	 * @return the code
	 */
	public int getResultCode() {
		return resultCode;
	}

	/**
	 * Retrieves the HTTP reason phrase.
	 *
	 * @return the reason
	 */
	public String getReason() {
		return reason;
	}
}
