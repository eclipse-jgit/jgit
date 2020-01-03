/*
 * Copyright (C) 2019, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link org.eclipse.jgit.transport.ProtocolV2Hook} that delegates to a list of
 * other hooks.
 * <p>
 * Hooks are run in the order passed to the constructor. If running a method on
 * one hook throws an exception, execution of remaining hook methods is aborted.
 *
 * @since 5.5
 */
public class ProtocolV2HookChain implements ProtocolV2Hook {
	private final List<? extends ProtocolV2Hook> hooks;

	/**
	 * Create a new hook chaining the given hooks together.
	 *
	 * @param hooks
	 *            hooks to execute, in order.
	 * @return a new hook chain of the given hooks.
	 */
	public static ProtocolV2Hook newChain(
			List<? extends ProtocolV2Hook> hooks) {
		List<? extends ProtocolV2Hook> newHooks = hooks.stream()
				.filter(hook -> !hook.equals(ProtocolV2Hook.DEFAULT))
				.collect(Collectors.toList());

		if (newHooks.isEmpty()) {
			return ProtocolV2Hook.DEFAULT;
		} else if (newHooks.size() == 1) {
			return newHooks.get(0);
		} else {
			return new ProtocolV2HookChain(newHooks);
		}
	}

	@Override
	public void onCapabilities(CapabilitiesV2Request req)
			throws ServiceMayNotContinueException {
		for (ProtocolV2Hook hook : hooks) {
			hook.onCapabilities(req);
		}
	}

	@Override
	public void onLsRefs(LsRefsV2Request req)
			throws ServiceMayNotContinueException {
		for (ProtocolV2Hook hook : hooks) {
			hook.onLsRefs(req);
		}
	}

	@Override
	public void onFetch(FetchV2Request req)
			throws ServiceMayNotContinueException {
		for (ProtocolV2Hook hook : hooks) {
			hook.onFetch(req);
		}
	}

	private ProtocolV2HookChain(List<? extends ProtocolV2Hook> hooks) {
		this.hooks = Collections.unmodifiableList(hooks);
	}
}
