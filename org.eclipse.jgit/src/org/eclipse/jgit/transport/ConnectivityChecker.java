/*
 * Copyright (c) 2019, Google LLC  and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.io.IOException;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;

/**
 *
 * Checks that received pack doesn't depend on objects which user don't have
 * access to.
 *
 * @since 5.6
 */
public interface ConnectivityChecker {

	/**
	 * Checks connectivity of the commit graph after pack uploading.
	 *
	 * @param baseReceivePack
	 * @param haves
	 *            Set of references known for client.
	 * @param monitor
	 *            Monitor to publish progress to.
	 * @throws IOException
	 *             an error occurred during connectivity checking.
	 *
	 */
	void checkConnectivity(BaseReceivePack baseReceivePack,
			Set<ObjectId> haves, ProgressMonitor monitor)
			throws IOException;

}
