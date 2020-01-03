/*
 * Copyright (C) 2012, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
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
