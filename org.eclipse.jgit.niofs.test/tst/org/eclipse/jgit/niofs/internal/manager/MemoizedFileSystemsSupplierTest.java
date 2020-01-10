/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.manager;

import java.util.function.Supplier;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MemoizedFileSystemsSupplierTest {

	public static int instanceCount = 0;

	@Test
	public void supplierTest() {

		getSupplier().get();
		getSupplier().get();
		assertEquals(2, instanceCount);

		instanceCount = 0;
		final Supplier<DummyObject> supplier = getLazySupplier();
		supplier.get();
		supplier.get();
		supplier.get();
		supplier.get();
		assertEquals(1, instanceCount);
	}

	Supplier<DummyObject> getLazySupplier() {
		return MemoizedFileSystemsSupplier.of(getSupplier());
	}

	Supplier<DummyObject> getSupplier() {
		return () -> new DummyObject();
	}

	private class DummyObject {

		public DummyObject() {
			test();
			instanceCount++;
		}

		public void test() {
			System.out.println("new Instance");
		}
	}
}