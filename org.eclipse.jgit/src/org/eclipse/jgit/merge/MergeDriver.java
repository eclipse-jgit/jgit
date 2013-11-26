/*******************************************************************************
 * Copyright (C) 2013, Obeo
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/
package org.eclipse.jgit.merge;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.lib.Repository;

/**
 * This interface describes the general contract expected out of a merge driver
 * as can be registered against JGit.
 * <p>
 * Merge driver are expected to be able to determine whether they can handle a
 * given entry within a TreeWalk. The ResolveMerger will then select the highest
 * ranked merge driver that can handle that particular entry.
 * </p>
 */
public interface MergeDriver {
	/**
	 * This will be called by the merger strategy on every file it processes
	 * that needs a non-trivial merge (both ours and theirs have changed since
	 * their common ancestor).
	 * <p>
	 * Merge drivers are expected to leave the result of the merge in
	 * {@code ours}, overwriting it as necessary.
	 * </p>
	 *
	 * @param repository
	 *            Repository in which we are merging files.
	 * @param ours
	 *            A temporary file containing "ours" version of the file to
	 *            merge.
	 * @param theirs
	 *            A temporary file containing "theirs" version of the file to
	 *            merge.
	 * @param base
	 *            A temporary file containing the base (common ancestor of ours
	 *            and theirs) version of the file to merge.
	 * @param commitNames
	 *            names
	 * @return <code>true</code> if the merge ended successfully,
	 *         <code>false</code> in case of conflicts or merge errors.
	 * @throws IOException
	 */
	boolean merge(Repository repository, File ours, File theirs, File base,
			String[] commitNames)
			throws IOException;

	/**
	 * @return The human-readable name of this merge driver. Take note that this
	 *         will be used as an identifier by the
	 *         {@link org.eclipse.jgit.merge.MergeDriverRegistry merge driver
	 *         registry}
	 */
	String getName();
}
