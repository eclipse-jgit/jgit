/*
 * Copyright (C) 2016, Google Inc.
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

package org.eclipse.jgit.util.time;

import java.time.Duration;

/**
 * A provider of time.
 * <p>
 * Clocks should provide wall clock time, obtained from a reasonable clock
 * source, such as the local system clock.
 * <p>
 * MonotonicClocks provide the following behavior, with the assertion always
 * being true if
 * {@link org.eclipse.jgit.util.time.ProposedTimestamp#blockUntil(Duration)} is
 * used:
 *
 * <pre>
 *   MonotonicClock clk = ...;
 *   long r1;
 *   try (ProposedTimestamp t1 = clk.propose()) {
 *   	r1 = t1.millis();
 *   	t1.blockUntil(...);
 *   }
 *
 *   try (ProposedTimestamp t2 = clk.propose()) {
 *   	assert t2.millis() > r1;
 *   }
 * </pre>
 *
 * @since 4.6
 */
public interface MonotonicClock {
	/**
	 * Obtain a timestamp close to "now".
	 * <p>
	 * Proposed times are close to "now", but may not yet be certainly in the
	 * past. This allows the calling thread to interleave other useful work
	 * while waiting for the clock instance to create an assurance it will never
	 * in the future propose a time earlier than the returned time.
	 * <p>
	 * A hypothetical implementation could read the local system clock (managed
	 * by NTP) and return that proposal, concurrently sending network messages
	 * to closely collaborating peers in the same cluster to also ensure their
	 * system clocks are ahead of this time. In such an implementation the
	 * {@link org.eclipse.jgit.util.time.ProposedTimestamp#blockUntil(Duration)}
	 * method would wait for replies from the peers indicating their own system
	 * clocks have moved past the proposed time.
	 *
	 * @return a {@link org.eclipse.jgit.util.time.ProposedTimestamp} object.
	 */
	ProposedTimestamp propose();
}
