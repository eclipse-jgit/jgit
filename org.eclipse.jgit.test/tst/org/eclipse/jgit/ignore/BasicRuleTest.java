/*
 * Copyright (C) 2014, Andrey Loskutov <loskutov@gmx.de>
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
package org.eclipse.jgit.ignore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BasicRuleTest {

	@Test
	public void test() {
		FastIgnoreRule rule1 = new FastIgnoreRule("/hello/[a]/");
		FastIgnoreRule rule2 = new FastIgnoreRule("/hello/[a]/");
		FastIgnoreRule rule3 = new FastIgnoreRule("!/hello/[a]/");
		FastIgnoreRule rule4 = new FastIgnoreRule("/hello/[a]");
		assertTrue(rule1.dirOnly());
		assertTrue(rule3.dirOnly());
		assertFalse(rule4.dirOnly());
		assertFalse(rule1.getNegation());
		assertTrue(rule3.getNegation());
		assertNotEquals(rule1, null);
		assertEquals(rule1, rule1);
		assertEquals(rule1, rule2);
		assertNotEquals(rule1, rule3);
		assertNotEquals(rule1, rule4);
		assertEquals(rule1.hashCode(), rule2.hashCode());
		assertNotEquals(rule1.hashCode(), rule3.hashCode());
		assertEquals(rule1.toString(), rule2.toString());
		assertNotEquals(rule1.toString(), rule3.toString());
	}

}
