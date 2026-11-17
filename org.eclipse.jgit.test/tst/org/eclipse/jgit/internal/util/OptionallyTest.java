/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.util;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class OptionallyTest {
	private final Object element = new Object();

	@Test
	public void softGetReturnsReferentAndNullAfterClear() {
		Optionally<Object> soft = new Optionally.Soft<>(element);
		assertSame(element, soft.get());

		soft.clear();
		assertNull(soft.get());
	}

	@Test
	public void hardGetReturnsElementAndNullAfterClear() {
		Optionally<Object> hard = new Optionally.Hard<>(element);
		assertSame(element, hard.get());

		hard.clear();
		assertNull(hard.get());
	}

	@Test
	public void hardOfNullGetReturnsNull() {
		Optionally<Object> hard = new Optionally.Hard<>(null);
		assertNull(hard.get());
	}

	@Test
	public void emptyGetReturnsNull() {
		Optionally<Object> empty = Optionally.empty();
		assertNull(empty.get());
	}
}
