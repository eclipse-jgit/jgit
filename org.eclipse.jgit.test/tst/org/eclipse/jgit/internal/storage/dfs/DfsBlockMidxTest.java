/*
 * Copyright (c) 2025, Google LLC
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.storage.dfs;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.eclipse.jgit.internal.storage.pack.PackExt;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRng;
import org.junit.Test;

public class DfsBlockMidxTest {

	private static final DfsStreamKey key = DfsStreamKey
			.of(new DfsRepositoryDescription("repo"), "pack-1", PackExt.PACK);

	@Test
	public void block_boundaries() {
		DfsStreamKey key = DfsStreamKey.of(new DfsRepositoryDescription("repo"),
				"pack-1", PackExt.PACK);
		DfsBlock block = new DfsBlock(key, 600, new byte[300]);

		DfsBlockMidx midxBlock = new DfsBlockMidx(block, 1000);
		assertEquals(block.start, midxBlock.start);
		assertEquals(block.end, midxBlock.end);
		assertEquals(block.stream, midxBlock.stream);
	}

	@Test
	public void block_size() {
		byte[] rawData = new TestRng(JGitTestUtil.getName()).nextBytes(300);
		DfsBlock block600to900 = new DfsBlock(key, 600, rawData);
		DfsBlockMidx midxBlock = new DfsBlockMidx(block600to900, 1000);
		assertEquals(300, midxBlock.size());
	}

	@Test
	public void block_copy_allBytesInBlock() {
		byte[] rawData = new TestRng(JGitTestUtil.getName()).nextBytes(300);
		// Block from positions 600 to 900 in a pack X
		DfsBlock block600to900 = new DfsBlock(key, 600, rawData);
		// Pack X starts at offset 1000 in the multipack index
		DfsBlockMidx midxBlock = new DfsBlockMidx(block600to900, 1000);

		int bytesToCopy = 50;
		byte[] dest = new byte[bytesToCopy];
		// Copy from midx(1700, 1750) => pack(700, 750) => block(100, 150)
		int copied = midxBlock.copy(1700, dest, 0, bytesToCopy);

		assertEquals(bytesToCopy, copied);
		byte[] expected = Arrays.copyOfRange(rawData, 100, 150);
		assertArrayEquals(expected, dest);
	}

	@Test
	public void block_copy_notEnoughInBlock() {
		byte[] rawData = new TestRng(JGitTestUtil.getName()).nextBytes(300);
		DfsBlock block600to900 = new DfsBlock(key, 600, rawData);
		DfsBlockMidx midxBlock = new DfsBlockMidx(block600to900, 1000);

		int bytesToCopy = 500;
		byte[] dest = new byte[bytesToCopy];
		// Copy from midx(1700, 2100) => pack(700, 1100) => block(700, 900)
		// (the block doesn't have all the bytes)
		int r = midxBlock.copy(1700, dest, 0, bytesToCopy);

		assertEquals(200, r);
		byte[] expected = Arrays.copyOfRange(rawData, 100, 300);
		assertArrayEqualSegment(expected, dest, 0, 200);
	}

	@Test
	public void block_contains() {
		byte[] rawData = new TestRng(JGitTestUtil.getName()).nextBytes(300);
		DfsBlock block600to900 = new DfsBlock(key, 600, rawData);
		DfsBlockMidx midxBlock = new DfsBlockMidx(block600to900, 1000);

		assertTrue(midxBlock.contains(key, 1700));
		assertFalse(midxBlock.contains(key, 2100));
		assertFalse(midxBlock.contains(key, 699));
	}

	@Test
	public void block_zeroCopyByteBuffer() {
		byte[] rawData = new TestRng(JGitTestUtil.getName()).nextBytes(300);
		DfsBlock block600to900 = new DfsBlock(key, 600, rawData);
		ByteBuffer bb = block600to900.zeroCopyByteBuffer(54);

		assertEquals(rawData[54], bb.get());
	}

	private static void assertArrayEqualSegment(byte[] expected, byte[] actual, int start, int end) {
		assertTrue(start < end);
		for (int i = start; i < end; i++) {
			assertEquals("Diff at position" + i, expected[i], actual[i]);
		}
	}
}
