/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
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

package org.eclipse.jgit.util;

import static org.junit.Assert.*;

import java.util.Comparator;

import org.junit.After;
import org.junit.Test;

public class BucketQueueTest {
	private QueueTester t0 = new QueueTester(0);

	private QueueTester t1 = new QueueTester(1);

	private QueueTester t2 = new QueueTester(2);

	private QueueTester t3 = new QueueTester(3);

	private QueueTester t4 = new QueueTester(4);

	private QueueTester t5 = new QueueTester(5);

	private QueueTester t6 = new QueueTester(6);

	Comparator<QueueTester> comparator = new Comparator<QueueTester>() {
		public int compare(QueueTester o1, QueueTester o2) {
			return o1.i - o2.i;
		}
	};

	Comparator<QueueTester> reverseComparator = new Comparator<QueueTester>() {
		public int compare(QueueTester o1, QueueTester o2) {
			return o2.i - o1.i;
		}
	};

	BucketQueue<QueueTester> q = new BucketQueue<QueueTester>(comparator);

	/**
	 * Structure to test queue
	 *
	 * @author keunhong
	 *
	 */
	private static class QueueTester extends Object {
		public int i;

		private static int KEY = 0;

		public int key = 0;

		public QueueTester(int i) {
			this.i = i;
			key = KEY++;
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof QueueTester)
				return ((QueueTester) o).key == key;
			else
				return false;
		}

		@Override
		public String toString() {
			return "(" + Integer.toString(i) + "," + Integer.toString(key)
					+ ")";
		}
	}

	@Test
	public void testEmpty() throws Exception {
		assertNull(q.peek());
	}

	@After
	public void clearQueue() {
		q = new BucketQueue<QueueTester>(comparator);
	}

	@Test
	public void testInsertInOrder() {
		q.add(t0);
		q.add(t1);
		q.add(t2);
		q.add(t3);

		assertEquals(t0, q.peek());
	}

	@Test
	public void testInsertBackwards() {
		q.add(t6);
		q.add(t5);
		q.add(t4);
		q.peek();
		q.add(t3);
		q.add(t2);
		q.add(t1);
		q.peek();

		assertEquals(t1, q.peek());
	}

	@Test
	public void testOutOfOrder() {
		q.add(t2);
		q.add(t3);
		q.add(t0);
		q.add(t1);

		assertEquals(t0, q.peek());
	}

	@Test
	public void testTieInOrder() {
		QueueTester a = new QueueTester(0);
		QueueTester b = new QueueTester(0);
		QueueTester c = new QueueTester(0);

		q.add(a);
		q.add(b);
		q.add(c);

		assertEquals(a, q.peek());
	}

	@Test
	public void testBucketSize() throws Exception {
		final QueueTester a = new QueueTester(0);

		q.add(a);
		q.add(a);
		q.add(a);
		q.add(a);
		q.add(a);
		q.add(a);
		q.add(a);
		q.add(a);
		q.add(a);
		q.add(a);

		assertEquals(0, q.getNumBuckets());
		assertEquals(10, q.size());

		q.peek();

		assertEquals(1, q.getNumBuckets());

		q.add(a);
		q.add(a);
		q.add(a);
		q.add(a);
		q.peek();

		assertEquals(2, q.getNumBuckets());
		assertEquals(14, q.size());

		q.add(a);
		q.peek();

		assertEquals(3, q.getNumBuckets());
		assertEquals(15, q.size());

		q.add(a);
		q.peek();

		assertEquals(1, q.getNumBuckets());
		assertEquals(16, q.size());

		q.pop();
		assertEquals(15, q.size());

		q.add(a);
		q.pop();
		q.pop();
		q.pop();
		assertEquals(13, q.size());
	}

	@Test
	public void testReverseComparator() {
		BucketQueue<QueueTester> qq = new BucketQueue<QueueTester>(
				reverseComparator);

		qq.add(t2);
		qq.add(t4);
		qq.add(t5);
		assertEquals(t5, qq.peek());
		qq.add(t3);
		assertEquals(t5, qq.peek());
	}

	@Test
	public void testDirectAdd() {
		q.add(t5);
		q.add(t6);
		q.peek();
		q.add(t3);
	}

	@Test
	public void testAlternate() {
		QueueTester a = new QueueTester(0);
		QueueTester b = new QueueTester(0);
		QueueTester c = new QueueTester(0);

		q.add(a);
		q.peek();
		q.add(b);
		q.peek();
		q.add(c);
		q.peek();

		assertEquals(a, q.peek());
	}

	@Test
	public void testBug() {

		BucketQueue<QueueTester> qq = new BucketQueue<QueueTester>(
				reverseComparator);

		QueueTester a100 = new QueueTester(100);
		QueueTester a98 = new QueueTester(98);
		QueueTester a99 = new QueueTester(99);
		QueueTester a89 = new QueueTester(89);
		QueueTester a97 = new QueueTester(97);
		QueueTester a96 = new QueueTester(96);
		QueueTester a91 = new QueueTester(91);
		QueueTester a95 = new QueueTester(95);
		QueueTester a94 = new QueueTester(94);
		QueueTester a92 = new QueueTester(92);
		QueueTester a93 = new QueueTester(93);
		QueueTester a90 = new QueueTester(90);
		QueueTester a77 = new QueueTester(77);
		QueueTester a79 = new QueueTester(79);
		QueueTester a88 = new QueueTester(88);
		QueueTester a84 = new QueueTester(84);
		QueueTester a87 = new QueueTester(87);
		QueueTester a86 = new QueueTester(86);
		QueueTester a85 = new QueueTester(85);
		QueueTester a83 = new QueueTester(83);
		QueueTester a80 = new QueueTester(80);
		QueueTester a78 = new QueueTester(78);
		QueueTester a82 = new QueueTester(82);
		QueueTester a81 = new QueueTester(81);
		QueueTester a76 = new QueueTester(76);
		QueueTester a73 = new QueueTester(73);
		QueueTester a74 = new QueueTester(74);
		QueueTester a75 = new QueueTester(75);

		qq.add(a100); // 100
		assertEquals(a100, qq.pop());
		qq.add(a98);
		qq.add(a99); // 98 99
		assertEquals(a99, qq.pop()); // 98
		qq.add(a89); // 89 98
		qq.peek();
		assertEquals(a98, qq.pop()); // 89
		qq.add(a97); // 89 97
		assertEquals(a97, qq.pop()); // 89
		qq.add(a96); // 89 96
		qq.add(a91); // 89 91 96
		assertEquals(a96, qq.pop()); // 89 91
		qq.add(a95); // 89 91 95
		assertEquals(a95, qq.pop()); // 89 91
		qq.add(a94); // 89 91 94
		assertEquals(a94, qq.pop()); // 89 91
		qq.add(a92); // 89 91 92
		qq.add(a93); // 89 91 92 93
		assertEquals(a93, qq.pop()); // 89 91 92
		qq.add(a90); // 89 90 91 92
		qq.peek();
		assertEquals(a92, qq.pop()); // 89 90 91
		qq.add(a77); // 77 89 90 91
		assertEquals(a91, qq.pop()); // 77 89 90
		qq.add(a79); // 77 79 89 90
		assertEquals(a90, qq.pop()); // 77 79 89
		qq.add(a88); // 77 79 88 89
		assertEquals(a89, qq.pop()); // 77 79 88
		qq.add(a84); // 77 79 84 88
		assertEquals(a88, qq.pop()); // 77 79 84
		qq.add(a87); // 77 79 84 87
		qq.add(a86); // 77 79 84 86 87
		assertEquals(a87, qq.pop()); // 77 79 84 86
		qq.add(a85); // 77 79 84 85 86
		assertEquals(a86, qq.pop()); // 77 79 84 85
		assertEquals(a85, qq.pop()); // 77 79 84
		qq.add(a83); // 77 79 83 84
		qq.add(a80); // 77 79 80 83 84
		assertEquals(a84, qq.pop()); // 77 79 80 83
		qq.add(a78); // 77 78 79 80 83
		assertEquals(a83, qq.pop()); // 77 78 79 80
		qq.add(a82); // 77 78 79 80 82
		assertEquals(a82, qq.pop()); // 77 78 79 80
		qq.add(a81); // 77 78 79 80 81
		assertEquals(a81, qq.pop()); // 77 78 79 80
		assertEquals(a80, qq.pop()); // 77 78 79
		assertEquals(a79, qq.pop()); // 77 78
		assertEquals(a78, qq.pop()); // 77
		qq.add(a76); // 76 77
		assertEquals(a77, qq.pop()); // 76
		qq.add(a73); // 73 76
		assertEquals(a76, qq.pop()); // 73
		qq.add(a74); // 73 74
		qq.add(a75); // 73 74 75
		assertEquals(a75, qq.pop()); // 73 74
		assertEquals(a74, qq.pop()); // 73
		assertEquals(a73, qq.pop()); //
		assertEquals(null, qq.pop()); //
	}
}
