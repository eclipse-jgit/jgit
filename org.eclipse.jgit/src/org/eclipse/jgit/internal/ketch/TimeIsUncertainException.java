/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.ketch;

import java.io.IOException;

import org.eclipse.jgit.internal.JGitText;

class TimeIsUncertainException extends IOException {
	private static final long serialVersionUID = 1L;

	TimeIsUncertainException() {
		super(JGitText.get().timeIsUncertain);
	}

	TimeIsUncertainException(Exception e) {
		super(JGitText.get().timeIsUncertain, e);
	}
}
