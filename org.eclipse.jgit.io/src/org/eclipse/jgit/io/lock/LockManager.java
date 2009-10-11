/*
 * Copyright (C) 2009, Imran M Yousuf <imyousuf@smartitengineering.com>
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

package org.eclipse.jgit.io.lock;

import java.net.URI;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages reentrant lock per URI
 * @author Imran M Yousuf (imyousuf at smartitengineering.com)
 * @since 0.6
 */
public final class LockManager {

  private static LockManager lockManager;

  public static LockManager getInstance() {
    if (lockManager == null) {
      lockManager = new LockManager();
    }
    return lockManager;
  }
  private final Map<URI, LockProvider> locks;

  private LockManager() {
    locks = new Hashtable<URI, LockProvider>();
  }

  public synchronized ReentrantLock register(URI key) {
    if (!locks.containsKey(key)) {
      locks.put(key, new LockProvider(
              new ReentrantLock()));
    }
    return locks.get(key).get();
  }

  public synchronized void unregister(URI key) {
    if (locks.containsKey(key)) {
      LockProvider provider = locks.get(key);
      if (provider != null) {
        provider.decreateCount();
        if (provider.getRegisterCount() < 1) {
          locks.remove(key);
        }
      }
    }
  }

  private static class LockProvider {

    private int registerCount = 0;
    private ReentrantLock lock;

    public LockProvider(ReentrantLock lock) {
      this.lock = lock;
    }

    public ReentrantLock get() {
      registerCount += 1;
      return lock;
    }

    public void decreateCount() {
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
      registerCount -= 1;
    }

    public int getRegisterCount() {
      return registerCount;
    }
  }
}
