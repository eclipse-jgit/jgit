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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstract implementation of lockable
 * @author Imran M Yousuf (imyousuf at smartitengineering.com)
 * @since 0.6
 */
public abstract class AbstractLockable
        implements Lockable {

  private ReentrantLock lock;
  private boolean attainedCompleteLock;

  protected AbstractLockable() {
  }

  @Override
  protected void finalize()
          throws Throwable {
    LockManager.getInstance().unregister(getURI());
    super.finalize();
  }

  /**
   * Retrieve the URI of this instance.
   * @return The URI to identify this instance. Should never be NULL
   */
  public abstract URI getURI();

  protected boolean isInternalLockHeldOnly() {
    return getLock().isHeldByCurrentThread();
  }

  public boolean isHeldByCurrentThread() {
    return isInternalLockHeldOnly() && attainedCompleteLock;
  }

  public void lock() {
    getLock().lock();
    childLock();
  }

  public void lockInterruptibly()
          throws InterruptedException {
    getLock().lockInterruptibly();
    childLock();
  }

  public Condition newCondition() {
    return getLock().newCondition();
  }

  public boolean tryLock() {
    if (isHeldByCurrentThread()) {
      return true;
    }
    final boolean tryLock = getLock().tryLock();
    if (tryLock) {
      final boolean performTryLock = performTryLock();
      if (!performTryLock) {
        unlock();
      }
      else {
        attainedCompleteLock = true;
      }
    }
    return tryLock;
  }

  public boolean tryLock(long time,
                         TimeUnit unit)
          throws InterruptedException {
    if (isHeldByCurrentThread()) {
      return true;
    }
    final boolean tryLock;
    long currentTime = System.currentTimeMillis();
    tryLock = getLock().tryLock(time, unit);
    long duration = unit.convert(System.currentTimeMillis() - currentTime,
            TimeUnit.MILLISECONDS);
    if (tryLock) {
      final boolean performTryLock = performTryLock(duration, unit);
      if (!performTryLock) {
        unlock();
      }
      else {
        attainedCompleteLock = true;
      }
      return performTryLock;
    }
    return tryLock;
  }

  public void unlock() {
    if (isInternalLockHeldOnly()) {
      if (attainedCompleteLock) {
        performUnlock();
      }
      attainedCompleteLock = false;
      getLock().unlock();
    }
  }

  /**
   * Retrieves the {@link ReentrantLock lock} based on this instances URI. It is
   * to be noted that all instances of this URI will share the same lock.
   * @return The lock for this lockable instance
   */
  protected ReentrantLock getLock() {
    if (lock == null) {
      lock = LockManager.getInstance().register(getURI());
    }
    return lock;
  }

  /**
   * Performs additional lock operations if required by children. It will wait
   * until it can avail for lock, but it will not wait for ever and will then
   * throw and exception.
   * @throws Exception If lock could be attained
   */
  protected abstract void performLock()
          throws Exception;

  /**
   * Performs additional lock operations if required by children, but it will
   * not wait for lock until beyond the time unit specified.
   * @param time Number to wait for lock
   * @param unit Unit of the time number
   * @return True if lock was attained else false
   */
  protected abstract boolean performTryLock(long time,
                                            TimeUnit unit);

  /**
   * Performs additional lock operations if required by children, but it will
   * not wait for lock at all.
   * @return True if lock was attained else false
   */
  protected abstract boolean performTryLock();

  /**
   * Performs additional unlock operations if required by children.
   */
  protected abstract void performUnlock();

  private void childLock() {
    try {
      performLock();
      attainedCompleteLock = true;
    }
    catch (Exception ex) {
      unlock();
      attainedCompleteLock = false;
      throw new RuntimeException("Could not attain lock!", ex);
    }
  }
}
