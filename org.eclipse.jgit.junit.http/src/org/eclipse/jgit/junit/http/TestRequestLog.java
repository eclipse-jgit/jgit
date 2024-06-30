/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.junit.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.eclipse.jetty.server.Handler.Wrapper;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

/** Logs request made through {@link AppServer}. */
class TestRequestLog extends Wrapper {
	private static final int MAX = 16;

	private final List<AccessEvent> events = Collections
			.synchronizedList(new ArrayList<>());

	private final Semaphore active = new Semaphore(MAX, true);

	/** Reset the log back to its original empty state. */
	void clear() {
		try {
			for (;;) {
				try {
					active.acquire(MAX);
					break;
				} catch (InterruptedException e) {
					continue;
				}
			}
			events.clear();
		} finally {
			active.release(MAX);
		}
	}

	/** @return all of the events made since the last clear. */
	List<AccessEvent> getEvents() {
		try {
			for (;;) {
				try {
					active.acquire(MAX);
					break;
				} catch (InterruptedException e) {
					continue;
				}
			}
			return Collections.unmodifiableList(new ArrayList<>(events));
		} finally {
			active.release(MAX);
		}
	}

	@Override
	public boolean handle(Request request, Response response, Callback callback)
			throws Exception {
		try {
			for (;;) {
				try {
					active.acquire();
					break;
				} catch (InterruptedException e) {
					continue;
				}
			}

			AccessEvent event = new AccessEvent(request);
			events.add(event);

			boolean result = super.handle(request, response, callback);

			event.setResponse(response);
			return result;
		} finally {
			active.release();
		}
	}
}
