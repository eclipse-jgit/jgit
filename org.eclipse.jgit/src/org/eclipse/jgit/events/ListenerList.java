/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.events;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Manages a thread-safe list of {@link RepositoryListener}s. */
public class ListenerList {
	private final ConcurrentMap<Class<? extends RepositoryListener>, CopyOnWriteArrayList<ListenerHandle>> lists = new ConcurrentHashMap<Class<? extends RepositoryListener>, CopyOnWriteArrayList<ListenerHandle>>();

	/**
	 * Register an IndexChangedListener.
	 *
	 * @param listener
	 *            the listener implementation.
	 * @return handle to later remove the listener.
	 */
	public ListenerHandle addIndexChangedListener(IndexChangedListener listener) {
		return addListener(IndexChangedListener.class, listener);
	}

	/**
	 * Register a RefsChangedListener.
	 *
	 * @param listener
	 *            the listener implementation.
	 * @return handle to later remove the listener.
	 */
	public ListenerHandle addRefsChangedListener(RefsChangedListener listener) {
		return addListener(RefsChangedListener.class, listener);
	}

	/**
	 * Register a ConfigChangedListener.
	 *
	 * @param listener
	 *            the listener implementation.
	 * @return handle to later remove the listener.
	 */
	public ListenerHandle addConfigChangedListener(
			ConfigChangedListener listener) {
		return addListener(ConfigChangedListener.class, listener);
	}

	/**
	 * Add a listener to the list.
	 *
	 * @param <T>
	 *            the type of listener being registered.
	 * @param type
	 *            type of listener being registered.
	 * @param listener
	 *            the listener instance.
	 * @return a handle to later remove the registration, if desired.
	 */
	public <T extends RepositoryListener> ListenerHandle addListener(
			Class<T> type, T listener) {
		ListenerHandle handle = new ListenerHandle(this, type, listener);
		add(handle);
		return handle;
	}

	/**
	 * Dispatch an event to all interested listeners.
	 * <p>
	 * Listeners are selected by the type of listener the event delivers to.
	 *
	 * @param event
	 *            the event to deliver.
	 */
	@SuppressWarnings("unchecked")
	public void dispatch(RepositoryEvent event) {
		List<ListenerHandle> list = lists.get(event.getListenerType());
		if (list != null) {
			for (ListenerHandle handle : list)
				event.dispatch(handle.listener);
		}
	}

	private void add(ListenerHandle handle) {
		List<ListenerHandle> list = lists.get(handle.type);
		if (list == null) {
			CopyOnWriteArrayList<ListenerHandle> newList;

			newList = new CopyOnWriteArrayList<ListenerHandle>();
			list = lists.putIfAbsent(handle.type, newList);
			if (list == null)
				list = newList;
		}
		list.add(handle);
	}

	void remove(ListenerHandle handle) {
		List<ListenerHandle> list = lists.get(handle.type);
		if (list != null)
			list.remove(handle);
	}
}
