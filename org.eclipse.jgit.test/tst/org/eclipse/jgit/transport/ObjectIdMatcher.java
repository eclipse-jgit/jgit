/*
 * Copyright (C) 2018, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Sets;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

/**
 * Multiple tests check that a collection of ObjectIds contain certain SHA1
 * (written as strings). This matcher hides the ObjectId to string conversion to
 * make the assertion more readable:
 *
 * assertThat(req.getWantsIds(), hasOnlyObjectIds("123123", "234234"));
 */
class ObjectIdMatcher extends TypeSafeMatcher<Collection<ObjectId>> {

	private final Set<ObjectId> expectedOids;

	private ObjectIdMatcher(Set<String> oids) {
		this.expectedOids = oids.stream().map(ObjectId::fromString)
				.collect(Collectors.toSet());
	}

	@Override
	public void describeTo(Description desc) {
		desc.appendText("Object ids:");
		desc.appendValueList("<", ",", ">", expectedOids);
	}

	@Override
	protected boolean matchesSafely(Collection<ObjectId> resultOids) {
		return resultOids.containsAll(expectedOids)
				&& expectedOids.containsAll(resultOids);
	}

	/**
	 * Assert that all and only the received {@link ObjectId object ids} are in
	 * the expected set.
	 * <p>
	 * ObjectIds are compared by SHA1.
	 *
	 * @param oids
	 *            Object ids to examine.
	 * @return true if examined and specified sets contains exactly the same
	 *         elements.
	 */
	@Factory
	static Matcher<Collection<ObjectId>> hasOnlyObjectIds(
			String... oids) {
		return new ObjectIdMatcher(Sets.of(oids));
	}
}