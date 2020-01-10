/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import org.eclipse.jgit.lib.ObjectId;

public class NotificationModel {

	private final ObjectId originalHead;
	private final String sessionId;
	private final String userName;
	private final String message;

	public NotificationModel(final ObjectId originalHead, final String sessionId, final String userName,
			final String message) {
		this.originalHead = originalHead;
		this.sessionId = sessionId;
		this.userName = userName;
		this.message = message;
	}

	public ObjectId getOriginalHead() {
		return originalHead;
	}

	public String getSessionId() {
		return sessionId;
	}

	public String getUserName() {
		return userName;
	}

	public String getMessage() {
		return message;
	}
}
