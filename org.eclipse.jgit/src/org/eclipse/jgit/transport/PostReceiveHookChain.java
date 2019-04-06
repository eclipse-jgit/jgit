/*
 * Copyright (C) 2011, Google Inc.
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

import java.util.Collection;
import java.util.List;

/**
 * {@link org.eclipse.jgit.transport.PostReceiveHook} that delegates to a list
 * of other hooks.
 * <p>
 * Hooks are run in the order passed to the constructor.
 */
public class PostReceiveHookChain implements PostReceiveHook {
	private final PostReceiveHook[] hooks;
	private final int count;

	/**
	 * Create a new hook chaining the given hooks together.
	 *
	 * @param hooks
	 *            hooks to execute, in order.
	 * @return a new hook chain of the given hooks.
	 */
	public static PostReceiveHook newChain(
			List<? extends PostReceiveHook> hooks) {
		PostReceiveHook[] newHooks = new PostReceiveHook[hooks.size()];
		int i = 0;
		for (PostReceiveHook hook : hooks)
			if (hook != PostReceiveHook.NULL)
				newHooks[i++] = hook;
            switch (i) {
                case 0:
                    return PostReceiveHook.NULL;
                case 1:
                    return newHooks[0];
                default:
                    return new PostReceiveHookChain(newHooks, i);
            }
	}

	/** {@inheritDoc} */
	@Override
	public void onPostReceive(ReceivePack rp,
			Collection<ReceiveCommand> commands) {
		for (int i = 0; i < count; i++)
			hooks[i].onPostReceive(rp, commands);
	}

	private PostReceiveHookChain(PostReceiveHook[] hooks, int count) {
		this.hooks = hooks;
		this.count = count;
	}
}
