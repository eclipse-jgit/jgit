/*
 * Copyright (C) 2024, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class BitmapIndexTest extends LocalDiskRepositoryTestCase {

  private static final String MAIN = "refs/heads/main";

  TestRepository<FileRepository> repo;

  RevCommit tipWithBitmap;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    FileRepository db = createWorkRepository();
    repo = new TestRepository<>(db);

    RevCommit base = repo.commit().create();
    RevCommit one = repo.commit().parent(base).create();
    tipWithBitmap = repo.commit().parent(one).create();
    repo.update(MAIN, tipWithBitmap);

    GC gc = new GC(repo.getRepository());
    gc.setAuto(false);
    gc.gc().get();

    assertNotNull(repo.getRevWalk().getObjectReader().getBitmapIndex());
  }


  @Test
  public void listener_getBitmap_counted() throws Exception {
    try (RevWalk rw = repo.getRevWalk();
        ObjectReader or = rw.getObjectReader()) {
      BitmapLookupCounter counter = new BitmapLookupCounter();
      BitmapIndex bitmapIndex = or.getBitmapIndex();
      bitmapIndex.addBitmapLookupListener(counter);

      bitmapIndex.getBitmap(tipWithBitmap);
      bitmapIndex.getBitmap(tipWithBitmap);
      bitmapIndex.getBitmap(ObjectId.zeroId());

      assertEquals(2, counter.bitmapFound);
      assertEquals(1, counter.bitmapNotFound);
    }
  }

	private static class BitmapLookupCounter
			implements BitmapIndex.BitmapLookupListener {
		int bitmapFound = 0;

		int bitmapNotFound = 0;

		@Override
		public void onBitmapFound(AnyObjectId oid) {
			bitmapFound += 1;
		}

		@Override
		public void onBitmapNotFound(AnyObjectId oid) {
			bitmapNotFound += 1;
		}
	}
}