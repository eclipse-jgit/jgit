/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.events;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages a thread-safe list of {@link org.eclipse.jgit.events.RepositoryListener}s.
 */
public class ListenerList {
	private final ConcurrentMap<Class<? extends RepositoryListener>, CopyOnWriteArrayList<ListenerHandle>> lists = new ConcurrentHashMap<>();

	/**
	 * Register a {@link org.eclipse.jgit.events.WorkingTreeModifiedListener}.
	 *
	 * @param listener
	 *            the listener implementation.
	 * @return handle to later remove the listener.
	 * @since 4.9
	 */
	public ListenerHandle addWorkingTreeModifiedListener(
			WorkingTreeModifiedListener listener) {
		return addListener(WorkingTreeModifiedListener.class, listener);
	}

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

			newList = new CopyOnWriteArrayList<>();
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
