/*******************************************************************************
 * Copyright (C) 2014, Obeo
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.jgit.lib.Config;

/**
 * This interface describes the general contract expected out of a merge driver
 * as can be registered against JGit.
 * 
 * @since 3.4
 */
public interface MergeDriver {
	/**
	 * This will be called by the merge strategy on every file it processes that
	 * needs a non-trivial merge (both ours and theirs have changed since their
	 * common ancestor).
	 * <p>
	 * Merge drivers are not expected to close the streams they are passed.
	 * </p>
	 *
	 * @param configuration
	 *            Configuration of the repository in which we're merging files.
	 * @param ours
	 *            An input stream providing access to "ours" version of the file
	 *            to merge.
	 * @param theirs
	 *            An input stream providing access to "theirs" version of the
	 *            file to merge.
	 * @param base
	 *            An input stream providing access to the base (common ancestor
	 *            of ours and theirs) version of the file to merge.
	 * @param output
	 *            Stream in which this is expected to output the result of the
	 *            merge operation.
	 * @param commitNames
	 *            Names of the commits we're currently merging.
	 * @return <code>true</code> if the merge ended successfully,
	 *         <code>false</code> in case of conflicts or merge errors.
	 * @throws IOException
	 */
	boolean merge(Config configuration, InputStream ours, InputStream theirs,
			InputStream base, OutputStream output, String[] commitNames)
			throws IOException;

	/**
	 * @return The human-readable name of this merge driver. Note that this will
	 *         be used as an identifier by the
	 *         {@link org.eclipse.jgit.merge.MergeDriverRegistry merge driver
	 *         registry}
	 */
	String getName();
}
