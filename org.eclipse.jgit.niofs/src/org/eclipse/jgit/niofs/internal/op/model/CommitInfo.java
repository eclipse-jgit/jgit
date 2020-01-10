/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.model;

import java.util.Date;
import java.util.TimeZone;

public class CommitInfo {

	private final String sessionId;
	private final String name;
	private final String email;
	private final String message;
	private final TimeZone timeZone;
	private final Date when;

	public CommitInfo(final String sessionId, final String name, final String email, final String message,
			final TimeZone timeZone, final Date when) {
		this.sessionId = sessionId;
		this.name = name;
		this.email = email;
		this.message = message;
		this.timeZone = timeZone;
		this.when = when;
	}

	public String getSessionId() {
		return sessionId;
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

	public TimeZone getTimeZone() {
		return timeZone;
	}

	public Date getWhen() {
		return when;
	}
}
