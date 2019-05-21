/*
 * Copyright (C) 2012, Google Inc.
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

import java.util.List;

/**
 * {@link org.eclipse.jgit.transport.AdvertiseRefsHook} that delegates to a list
 * of other hooks.
 * <p>
 * Hooks are run in the order passed to the constructor. A hook may inspect or
 * modify the results of the previous hooks in the chain by calling
 * {@link org.eclipse.jgit.transport.UploadPack#getAdvertisedRefs()}, or
 * {@link org.eclipse.jgit.transport.ReceivePack#getAdvertisedRefs()} or
 * {@link org.eclipse.jgit.transport.ReceivePack#getAdvertisedObjects()}.
 */
public class AdvertiseRefsHookChain implements AdvertiseRefsHook {
	private final AdvertiseRefsHook[] hooks;
	private final int count;

	/**
	 * Create a new hook chaining the given hooks together.
	 *
	 * @param hooks
	 *            hooks to execute, in order.
	 * @return a new hook chain of the given hooks.
	 */
	public static AdvertiseRefsHook newChain(List<? extends AdvertiseRefsHook> hooks) {
		AdvertiseRefsHook[] newHooks = new AdvertiseRefsHook[hooks.size()];
		int i = 0;
		for (AdvertiseRefsHook hook : hooks)
			if (hook != AdvertiseRefsHook.DEFAULT)
				newHooks[i++] = hook;
		switch (i) {
		case 0:
			return AdvertiseRefsHook.DEFAULT;
		case 1:
			return newHooks[0];
		default:
			return new AdvertiseRefsHookChain(newHooks, i);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void advertiseRefs(ReceivePack rp)
			throws ServiceMayNotContinueException {
		for (int i = 0; i < count; i++)
			hooks[i].advertiseRefs(rp);
	}

	/** {@inheritDoc} */
	@Override
	public void advertiseRefs(UploadPack rp)
			throws ServiceMayNotContinueException {
		for (int i = 0; i < count; i++)
			hooks[i].advertiseRefs(rp);
	}

	private AdvertiseRefsHookChain(AdvertiseRefsHook[] hooks, int count) {
		this.hooks = hooks;
		this.count = count;
	}
}
