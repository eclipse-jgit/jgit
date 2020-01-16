/*
 * Copyright (C) 2019, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef.Unpeeled;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Ref.Storage;
import org.junit.Test;

public class UploadPackRefSortingForReachabilityTest {

	@Test
	public void sortReferences() {
		List<Ref> refs = Stream.of("refs/changes/12/12", "refs/changes/12/1",
				"refs/heads/master", "refs/heads/something",
				"refs/changes/55/1", "refs/tags/v1.1")
				.map(s -> new Unpeeled(Storage.LOOSE, s, ObjectId.zeroId()))
				.collect(Collectors.toList());
		Stream<Ref> sorted = UploadPack.importantRefsFirst(refs);
		List<String> collected = sorted.map(Ref::getName)
				.collect(Collectors.toList());
		assertThat(collected,
				contains("refs/heads/master", "refs/heads/something",
						"refs/tags/v1.1", "refs/changes/12/12",
						"refs/changes/12/1", "refs/changes/55/1"));
	}
}
