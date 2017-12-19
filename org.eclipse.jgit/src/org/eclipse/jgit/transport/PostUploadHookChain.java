/*
 * Copyright (C) 2015, Google Inc.
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

import java.util.List;

import org.eclipse.jgit.storage.pack.PackStatistics;

/**
 * {@link org.eclipse.jgit.transport.PostUploadHook} that delegates to a list of
 * other hooks.
 * <p>
 * Hooks are run in the order passed to the constructor.
 *
 * @since 4.1
 */
public class PostUploadHookChain implements PostUploadHook {
	private final PostUploadHook[] hooks;
	private final int count;

	/**
	 * Create a new hook chaining the given hooks together.
	 *
	 * @param hooks
	 *            hooks to execute, in order.
	 * @return a new chain of the given hooks.
	 */
	public static PostUploadHook newChain(List<? extends PostUploadHook> hooks) {
		PostUploadHook[] newHooks = new PostUploadHook[hooks.size()];
		int i = 0;
		for (PostUploadHook hook : hooks)
			if (hook != PostUploadHook.NULL)
				newHooks[i++] = hook;
		if (i == 0)
			return PostUploadHook.NULL;
		else if (i == 1)
			return newHooks[0];
		else
			return new PostUploadHookChain(newHooks, i);
	}

	/** {@inheritDoc} */
	@Override
	public void onPostUpload(PackStatistics stats) {
		for (int i = 0; i < count; i++)
			hooks[i].onPostUpload(stats);
	}

	private PostUploadHookChain(PostUploadHook[] hooks, int count) {
		this.hooks = hooks;
		this.count = count;
	}
}
