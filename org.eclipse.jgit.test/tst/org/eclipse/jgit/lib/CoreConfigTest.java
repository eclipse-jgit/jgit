/*
 * Copyright (C) 2017, Google Inc.
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

package org.eclipse.jgit.lib;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.eclipse.jgit.lib.CoreConfig.LogAllRefUpdates;
import org.junit.Test;

public final class CoreConfigTest {
  @Test
	public void logAllRefUpdates() {
		assertFalse(LogAllRefUpdates.FALSE.shouldAutoCreateLog("ANYTHING"));
		assertFalse(LogAllRefUpdates.FALSE.shouldAutoCreateLog("HEAD"));
		assertFalse(LogAllRefUpdates.FALSE.shouldAutoCreateLog("refs/foo"));
		assertFalse(LogAllRefUpdates.FALSE.shouldAutoCreateLog("refs/heads/foo"));
		assertFalse(LogAllRefUpdates.FALSE.shouldAutoCreateLog("refs/tags/foo"));
		assertFalse(LogAllRefUpdates.FALSE.shouldAutoCreateLog("refs/remotes/foo"));
		assertFalse(LogAllRefUpdates.FALSE.shouldAutoCreateLog("refs/notes/foo"));
		assertFalse(LogAllRefUpdates.FALSE.shouldAutoCreateLog("refs/stash"));

		assertFalse(LogAllRefUpdates.TRUE.shouldAutoCreateLog("ANYTHING"));
		assertTrue(LogAllRefUpdates.TRUE.shouldAutoCreateLog("HEAD"));
		assertFalse(LogAllRefUpdates.TRUE.shouldAutoCreateLog("refs/foo"));
		assertTrue(LogAllRefUpdates.TRUE.shouldAutoCreateLog("refs/heads/foo"));
		assertFalse(LogAllRefUpdates.TRUE.shouldAutoCreateLog("refs/tags/foo"));
		assertTrue(LogAllRefUpdates.TRUE.shouldAutoCreateLog("refs/remotes/foo"));
		assertTrue(LogAllRefUpdates.TRUE.shouldAutoCreateLog("refs/notes/foo"));
		assertFalse(LogAllRefUpdates.FALSE.shouldAutoCreateLog("refs/stash"));

		assertTrue(LogAllRefUpdates.ALWAYS.shouldAutoCreateLog("ANYTHING"));
		assertTrue(LogAllRefUpdates.ALWAYS.shouldAutoCreateLog("HEAD"));
		assertTrue(LogAllRefUpdates.ALWAYS.shouldAutoCreateLog("refs/foo"));
		assertTrue(LogAllRefUpdates.ALWAYS.shouldAutoCreateLog("refs/heads/foo"));
		assertTrue(LogAllRefUpdates.ALWAYS.shouldAutoCreateLog("refs/tags/foo"));
		assertTrue(LogAllRefUpdates.ALWAYS.shouldAutoCreateLog("refs/remotes/foo"));
		assertTrue(LogAllRefUpdates.ALWAYS.shouldAutoCreateLog("refs/notes/foo"));
		assertFalse(LogAllRefUpdates.FALSE.shouldAutoCreateLog("refs/stash"));
	}
}
