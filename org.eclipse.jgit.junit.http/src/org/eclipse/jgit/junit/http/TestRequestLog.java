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

package org.eclipse.jgit.junit.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.DispatcherType;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.HandlerWrapper;

/** Logs request made through {@link AppServer}. */
class TestRequestLog extends HandlerWrapper {
  private static final int MAX = 16;

  private final List<AccessEvent> events = new ArrayList<AccessEvent>();

  private final Semaphore active = new Semaphore(MAX);

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

  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request,
      HttpServletResponse response) throws IOException, ServletException {
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
