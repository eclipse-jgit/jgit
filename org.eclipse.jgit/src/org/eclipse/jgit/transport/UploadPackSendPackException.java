/*
 * Copyright (C) 2021, Fabio Ponciroli <ponch@gerritforge.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.IOException;

/**
 * Generic Exception encountered during an Upload Pack operation.
 *
 */
public class UploadPackSendPackException extends IOException {
    private static final long serialVersionUID = 1L;

	/**
	 * @param why
	 *            root cause exception.
	 */
    public UploadPackSendPackException(Throwable why) {
        initCause(why);
    }
}
