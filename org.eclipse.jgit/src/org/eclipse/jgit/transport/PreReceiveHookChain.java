/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import java.util.Collection;
import java.util.List;

/**
 * {@link org.eclipse.jgit.transport.PreReceiveHook} that delegates to a list of
 * other hooks.
 * <p>
 * Hooks are run in the order passed to the constructor.
 */
public class PreReceiveHookChain implements PreReceiveHook {
	private final PreReceiveHook[] hooks;
	private final int count;

	/**
	 * Create a new hook chaining the given hooks together.
	 *
	 * @param hooks
	 *            hooks to execute, in order.
	 * @return a new hook chain of the given hooks.
	 */
	public static PreReceiveHook newChain(List<? extends PreReceiveHook> hooks) {
		PreReceiveHook[] newHooks = new PreReceiveHook[hooks.size()];
		int i = 0;
		for (PreReceiveHook hook : hooks)
			if (hook != PreReceiveHook.NULL)
				newHooks[i++] = hook;
		switch (i) {
		case 0:
			return PreReceiveHook.NULL;
		case 1:
			return newHooks[0];
		default:
			return new PreReceiveHookChain(newHooks, i);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void onPreReceive(ReceivePack rp,
			Collection<ReceiveCommand> commands) {
		for (int i = 0; i < count; i++)
			hooks[i].onPreReceive(rp, commands);
	}

	private PreReceiveHookChain(PreReceiveHook[] hooks, int count) {
		this.hooks = hooks;
		this.count = count;
	}
}
