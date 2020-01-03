/*
 * Copyright (C) 2012, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevTag;
import org.junit.Test;

public class GcTagTest extends GcTestCase {
	@Test
	public void lightweightTag_objectNotPruned() throws Exception {
		RevBlob a = tr.blob("a");
		tr.lightweightTag("t", a);
		gc.setExpireAgeMillis(0);
		fsTick();
		gc.prune(Collections.<ObjectId> emptySet());
		assertTrue(repo.getObjectDatabase().has(a));
	}

	@Test
	public void annotatedTag_objectNotPruned() throws Exception {
		RevBlob a = tr.blob("a");
		RevTag t = tr.tag("t", a); // this doesn't create the refs/tags/t ref
		tr.lightweightTag("t", t);

		gc.setExpireAgeMillis(0);
		fsTick();
		gc.prune(Collections.<ObjectId> emptySet());
		assertTrue(repo.getObjectDatabase().has(t));
		assertTrue(repo.getObjectDatabase().has(a));
	}
}
