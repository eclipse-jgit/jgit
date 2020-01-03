/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.util.Map;

import org.eclipse.jgit.lib.Ref;

/**
 * Filters the list of refs that are advertised to the client.
 * <p>
 * The filter is called by {@link org.eclipse.jgit.transport.ReceivePack} and
 * {@link org.eclipse.jgit.transport.UploadPack} to ensure that the refs are
 * filtered before they are advertised to the client.
 * <p>
 * This can be used by applications to control visibility of certain refs based
 * on a custom set of rules.
 */
public interface RefFilter {
	/**
	 * The default filter, allows all refs to be shown.
	 */
	RefFilter DEFAULT = (Map<String, Ref> refs) -> refs;

	/**
	 * Filters a {@code Map} of refs before it is advertised to the client.
	 *
	 * @param refs
	 *            the refs which this method need to consider.
	 * @return
	 *            the filtered map of refs.
	 */
	Map<String, Ref> filter(Map<String, Ref> refs);
}
