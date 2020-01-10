/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.fs.options;

import java.nio.file.CopyOption;
import java.nio.file.OpenOption;
import java.util.Date;
import java.util.TimeZone;

public class CommentedOption implements OpenOption, CopyOption {

	private final String sessionId;
	private final String name;
	private final String email;
	private final String message;
	private final Date when;
	private final TimeZone timeZone;

	public CommentedOption(final String name) {
		this(null, name, null, null, null, null);
	}

	public CommentedOption(final String name, final String message) {
		this(null, name, null, message, null, null);
	}

	public CommentedOption(final String name, final String email, final String message) {
		this(null, name, email, message, null, null);
	}

	public CommentedOption(final String sessionId, final String name, final String email, final String message) {
		this(sessionId, name, email, message, null, null);
	}

	public CommentedOption(final String name, final String email, final String message, final Date when) {
		this(null, name, email, message, when, null);
	}

	public CommentedOption(final String sessionId, final String name, final String email, final String message,
			final Date when) {
		this(sessionId, name, email, message, when, null);
	}

	public CommentedOption(final String name, final String email, final String message, final Date when,
			final TimeZone timeZone) {
		this(null, name, email, message, when, timeZone);
	}

	public CommentedOption(final String sessionId, final String name, final String email, final String message,
			final Date when, final TimeZone timeZone) {
		this.sessionId = sessionId;
		this.name = name;
		this.email = email;
		this.message = message;
		this.when = when;
		this.timeZone = timeZone;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getMessage() {
		return message;
	}

	public String getSessionId() {
		return sessionId;
	}

	public Date getWhen() {
		return when;
	}

	public TimeZone getTimeZone() {
		return timeZone;
	}
}
