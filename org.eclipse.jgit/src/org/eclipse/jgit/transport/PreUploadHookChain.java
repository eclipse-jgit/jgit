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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;

/**
 * {@link org.eclipse.jgit.transport.PreUploadHook} that delegates to a list of
 * other hooks.
 * <p>
 * Hooks are run in the order passed to the constructor. If running a method on
 * one hook throws an exception, execution of remaining hook methods is aborted.
 */
public class PreUploadHookChain implements PreUploadHook {
	private final List<PreUploadHook> hooks;

	/**
	 * Create a new hook chaining the given hooks together.
	 *
	 * @param hooks
	 *            hooks to execute, in order.
	 * @return a new hook chain of the given hooks.
	 */
	public static PreUploadHook newChain(List<PreUploadHook> hooks) {
		List<PreUploadHook> newHooks = hooks.stream()
				.filter(hook -> !hook.equals(PreUploadHook.NULL))
				.collect(Collectors.toList());

		if (newHooks.isEmpty()) {
			return PreUploadHook.NULL;
		} else if (newHooks.size() == 1) {
			return newHooks.get(0);
		} else {
			return new PreUploadHookChain(newHooks);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void onBeginNegotiateRound(UploadPack up,
			Collection<? extends ObjectId> wants, int cntOffered)
			throws ServiceMayNotContinueException {
		for (PreUploadHook hook : hooks) {
			hook.onBeginNegotiateRound(up, wants, cntOffered);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void onEndNegotiateRound(UploadPack up,
			Collection<? extends ObjectId> wants, int cntCommon,
			int cntNotFound, boolean ready)
			throws ServiceMayNotContinueException {
		for (PreUploadHook hook : hooks) {
			hook.onEndNegotiateRound(up, wants, cntCommon, cntNotFound, ready);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void onSendPack(UploadPack up,
			Collection<? extends ObjectId> wants,
			Collection<? extends ObjectId> haves)
			throws ServiceMayNotContinueException {
		for (PreUploadHook hook : hooks) {
			hook.onSendPack(up, wants, haves);
		}
	}

	private PreUploadHookChain(List<PreUploadHook> hooks) {
		this.hooks = Collections.unmodifiableList(hooks);
	}
}
