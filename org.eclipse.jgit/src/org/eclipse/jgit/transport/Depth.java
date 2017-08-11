/*
 * Copyright (C) 2017, Harald Weiner
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
 */

package org.eclipse.jgit.transport;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;

/***
 * Helper class for handling truncating/deepening history. It is used by shallow
 * clones to fetch only a maximum number of commits.
 *
 * @since 5.0
 *
 */
public class Depth {

	/***
	 * The largest signed integer means infinite depth
	 */
	public static int DEPTH_INFINITE = 0x7fffffff;

	/***
	 * Fetch max. specified number of commits
	 */
	private int depth;

	/***
	 * Constructor for new instance of depth.
	 *
	 * @param depth
	 *            0 < depth <=
	 *            {@code org.eclipse.jgit.transport.Depth.DEPTH_INFINITE}.<br/>
	 *            If depth ==
	 *            {@code org.eclipse.jgit.transport.Depth.DEPTH_INFINITE} then
	 *            history will be cloned completely. otherwise the history will
	 *            be truncated to the specified number of commits.
	 * @throws IllegalArgumentException
	 *             if depth is not in allowed range an
	 *             <code>IllegalArgumentException</code> will be thrown
	 */
	public Depth(final int depth) {
		setDepth(depth);
	}

	/***
	 * Get depth.
	 *
	 * @return Maximum number of commits to fetch
	 */
	public int getDepth() {
		return this.depth;
	}

	/***
	 * Set depth to truncate history.
	 *
	 * @param depth
	 *            0 < depth <=
	 *            {@code org.eclipse.jgit.transport.Transport.DEPTH_INFINITE}.<br/>
	 *            If depth ==
	 *            {@code org.eclipse.jgit.transport.Transport.DEPTH_INFINITE}
	 *            then history will be cloned completely. otherwise the history
	 *            will be truncated to the specified number of commits.
	 * @throws IllegalArgumentException
	 *             if depth is not in allowed range an
	 *             <code>IllegalArgumentException</code> will be thrown
	 */
	public void setDepth(final int depth) {
		if ((depth <= 0) || (depth > DEPTH_INFINITE)) {
			throw new IllegalArgumentException(MessageFormat.format(
					JGitText.get().invalidDepth, Integer.valueOf(depth)));
		}
		this.depth = depth;
	}

	/***
	 * Test if depth has been specified.
	 *
	 * @param depth
	 *            Maximum number of commits to fetch. Can be null.
	 * @return Test result. True means that the depth value is inside the range.
	 */
	public static boolean isSet(final Depth depth) {
		if (depth == null) {
			return false;
		}
		if (depth.depth != DEPTH_INFINITE) {
			return true;
		}
		return false;
	}

}
