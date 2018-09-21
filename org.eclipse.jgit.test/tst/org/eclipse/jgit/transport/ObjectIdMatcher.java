/*
 * Copyright (C) 2018, Google LLC.
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
 * In multiple tests we need to check that a collection of ObjectIds contain
 * certain SHA (written as strings). This matcher hides the ObjectId to string
 * conversion, to make the assertion more readable:
 *
 * assertThat(req.getWantsIds(), hasObjectIds("123123", "234234"));
 */
class ObjectIdMatcher extends TypeSafeMatcher<Collection<ObjectId>> {

	private Set<String> expectedOids;

	private ObjectIdMatcher(Set<String> oids) {
		this.expectedOids = oids;
	}

	@Override
	public void describeTo(Description desc) {
		desc.appendText("Object ids:");
		desc.appendValueList("<", ",", ">", expectedOids);
	}

	@Override
	protected boolean matchesSafely(Collection<ObjectId> objIds) {
		Set<String> asStrings = objIds.stream().map(ObjectId::name)
				.collect(Collectors.toSet());
		return asStrings.containsAll(expectedOids)
				&& expectedOids.containsAll(asStrings);
	}

	/**
	 * Assert that the expected set of object ids (received in the constructor)
	 * and the set here have the same cardinality and contents.
	 *
	 * @param oids
	 *            Object ids received as result of an operation
	 * @return true if sets of expected and result object ids are equal (same
	 *         cardinality and contents)
	 */
	@Factory
	static Matcher<Collection<ObjectId>> hasOnlyObjectIds(
			String... oids) {
		return new ObjectIdMatcher(Sets.of(oids));
	}
}