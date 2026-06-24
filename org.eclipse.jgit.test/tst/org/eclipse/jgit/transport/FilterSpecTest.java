/*
 * Copyright (C) 2026, Stuart Lang <stuart.b.lang@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.errors.PackProtocolException;
import org.junit.Test;

public class FilterSpecTest {

	@Test
	public void noFilterHasNoConfigValueOrLine() {
		assertTrue(FilterSpec.NO_FILTER.isNoOp());
		assertNull(FilterSpec.NO_FILTER.getFilterConfigValue());
		assertNull(FilterSpec.NO_FILTER.filterLine());
	}

	@Test
	public void blobNoneRoundTrips() throws Exception {
		assertRoundTrip("blob:none");
	}

	@Test
	public void blobLimitRoundTrips() throws Exception {
		assertRoundTrip("blob:limit=0");
		assertRoundTrip("blob:limit=4096");
	}

	@Test
	public void treeDepthRoundTrips() throws Exception {
		assertRoundTrip("tree:0");
		assertRoundTrip("tree:3");
	}

	@Test(expected = PackProtocolException.class)
	public void invalidFilterRejected() throws Exception {
		FilterSpec.fromFilterLine("bogus:spec");
	}

	private static void assertRoundTrip(String value)
			throws PackProtocolException {
		FilterSpec spec = FilterSpec.fromFilterLine(value);
		// The bare config value matches what was parsed...
		assertEquals(value, spec.getFilterConfigValue());
		// ...and the protocol line is the value prefixed with "filter ".
		assertEquals("filter " + value, spec.filterLine());
		// ...and re-parsing the config value yields an equivalent spec.
		assertEquals(value,
				FilterSpec.fromFilterLine(spec.getFilterConfigValue())
						.getFilterConfigValue());
	}
}
