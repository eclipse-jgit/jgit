/*
 * Copyright (c) 2026 Qualcomm Technologies, Inc. and/or its subsidiaries and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class PackBitmapIndexV1Test extends GcTestCase {
	@Test
	public void bitmapsMatchReloadedIndexOfSamePack() throws Exception {
		RevCommit tip = commitChain(10);
		tr.update("refs/heads/main", tip);
		gc.gc().get();
		Pack pack = singlePack();
		PackBitmapIndex before = pack.getBitmapIndex();
		BitmapIndexImpl bitmaps = new BitmapIndexImpl(before);
		BitmapBuilder need = bitmaps.newBitmapBuilder();
		need.or(bitmaps.getBitmap(tip));

		pack.close();
		PackBitmapIndex reloaded = pack.getBitmapIndex();

		assertNotSame(before, reloaded);
		assertEquals(before, reloaded);
		assertEquals(before.hashCode(), reloaded.hashCode());
		assertTrue(need.removeAllOrNone(reloaded));
	}

	@Test
	public void bitmapsDoNotMatchIndexOfOtherPack() throws Exception {
		RevCommit first = commitChain(3);
		tr.update("refs/heads/main", first);
		gc.gc().get();
		Pack firstPack = singlePack();
		PackBitmapIndex before = firstPack.getBitmapIndex();

		tr.update("refs/heads/main",
				tr.commit().parent(first).add("b", "b").create());
		gc.gc().get();
		Pack otherPack = repo.getObjectDatabase().getPacks().stream()
				.filter(p -> !p.getPackName().equals(firstPack.getPackName()))
				.findFirst().get();
		PackBitmapIndex other = otherPack.getBitmapIndex();

		assertFalse(before.equals(other));
		BitmapIndexImpl bitmaps = new BitmapIndexImpl(before);
		BitmapBuilder need = bitmaps.newBitmapBuilder();
		need.or(bitmaps.getBitmap(first));
		assertFalse(need.removeAllOrNone(other));
	}
}
