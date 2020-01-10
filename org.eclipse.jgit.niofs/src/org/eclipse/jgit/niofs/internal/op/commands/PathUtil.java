/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.op.commands;

public class PathUtil {

	public static String normalize(final String path) {

		if (path.equals("/")) {
			return "";
		}

		final boolean startsWith = path.startsWith("/");
		final boolean endsWith = path.endsWith("/");
		if (startsWith && endsWith) {
			return path.substring(1, path.length() - 1);
		}
		if (startsWith) {
			return path.substring(1);
		}
		if (endsWith) {
			return path.substring(0, path.length() - 1);
		}
		return path;
	}
}
