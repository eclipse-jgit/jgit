/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.util;

import java.util.concurrent.ThreadFactory;

public class DescriptiveThreadFactory implements ThreadFactory {

	@Override
	public Thread newThread(final Runnable r) {
		if (r instanceof DescriptiveRunnable) {
			return new Thread(r, ((DescriptiveRunnable) r).getDescription());
		}
		return new Thread(r);
	}
}
