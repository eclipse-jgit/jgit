/*
 * Copyright (c) 2020, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import java.util.List;

import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Factory for submissions on a single repository.
 *
 * @since 5.9
 */
public interface SingleRepoSubmissionFactory {

	/**
	 * Returns a handler of a submission. Caller must invoke {@link OpenSubmission#completed()} when the result of all operations (success or not) is known.
	 * 
	 * @param refUpdates reference update to perform in this submission
	 * 
	 * @return a handler of the created submission.
	 */
	OpenSubmission createSubmission(List<ReceiveCommand> refUpdates);
}
