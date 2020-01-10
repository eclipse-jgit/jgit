/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.niofs.internal.daemon.filter.HiddenBranchRefFilter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(MockitoJUnitRunner.class)
public class HiddenBranchRefFilterTest {

	private HiddenBranchRefFilter filter;

	@Mock
	private Ref ref;
	private Map<String, Ref> refs;

	@Before
	public void setUp() {

		refs = new HashMap<>();
		refs.put("master", ref);
		refs.put("develop", ref);
		refs.put("PR--from/develop-master", ref);
		refs.put("PR-1--master", ref);
		refs.put("PR-master", ref);
		refs.put("PR-1-from/develop-master", ref);

		filter = new HiddenBranchRefFilter();
	}

	@Test
	public void testHiddenBranchsFiltering() {
		final Map<String, Ref> filteredRefs = filter.filter(refs);
		final Set<Map.Entry<String, Ref>> set = filteredRefs.entrySet();
		assertEquals(5, set.size());
		assertFalse(set.stream().anyMatch(entry -> entry.getKey().equals("PR-1-from/develop-master")));
	}
}
