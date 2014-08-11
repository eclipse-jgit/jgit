/*******************************************************************************
 * Copyright (c) 2014 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/
package org.eclipse.jgit.ignore2.tests;

import static org.junit.Assert.*;

import org.eclipse.jgit.ignore2.FastIgnoreRule;
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
