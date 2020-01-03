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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.HandlerWrapper;

/** Logs request made through {@link AppServer}. */
class TestRequestLog extends HandlerWrapper {
	private static final int MAX = 16;

	private final List<AccessEvent> events = new ArrayList<>();

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

			synchronized (events) {
				events.clear();
			}
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

			synchronized (events) {
				return events;
			}
		} finally {
			active.release(MAX);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void handle(String target, Request baseRequest,
			HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		try {
			for (;;) {
				try {
					active.acquire();
					break;
				} catch (InterruptedException e) {
					continue;
				}
			}

			super.handle(target, baseRequest, request, response);

			if (DispatcherType.REQUEST.equals(baseRequest.getDispatcherType()))
				log((Request) request, (Response) response);

		} finally {
			active.release();
		}
	}

	private void log(Request request, Response response) {
		synchronized (events) {
			events.add(new AccessEvent(request, response));
		}
	}
}
