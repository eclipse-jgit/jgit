/*
 * Copyright (C) 2017, Google Inc.
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

package org.eclipse.jgit.internal.storage.reftable;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;
import org.eclipse.jgit.lib.SymbolicRef;
import org.junit.Test;

public class ReftableTest {
	private static final String MASTER = "refs/heads/master";
	private static final String V1_0 = "refs/tags/v1.0";

	@Test
	public void emptyTable() throws IOException {
		byte[] table = write();
		assertEquals(8 + 16 /* header, footer */, table.length);
		assertEquals('\1', table[0]);
		assertEquals('R', table[1]);
		assertEquals('E', table[2]);
		assertEquals('F', table[3]);
		assertEquals(0x01, table[4]);
		assertTrue(ReftableConstants.isFileHeaderMagic(table, 0, 8));
		assertTrue(ReftableConstants.isFileHeaderMagic(table, 8, 16));

		assertFalse(seekToFirstRef(table).next());
		assertFalse(seek(table, HEAD).next());
		assertFalse(seek(table, R_HEADS).next());
	}

	@Test
	public void oneIdRef() throws IOException {
		Ref exp = ref(MASTER, 1);
		byte[] table = write(exp);
		assertEquals(8 + 2 + MASTER.length() + 20 + 12 + 16, table.length);

		ReftableReader r = seekToFirstRef(table);
		assertTrue(r.next());

		Ref act = r.getRef();
		assertNotNull(act);
		assertEquals(PACKED, act.getStorage());
		assertTrue(act.isPeeled());
		assertFalse(act.isSymbolic());
		assertEquals(exp.getName(), act.getName());
		assertEquals(exp.getObjectId(), act.getObjectId());
		assertNull(act.getPeeledObjectId());
		assertFalse(r.next());

		r = seek(table, MASTER);
		assertTrue(r.next());
		act = r.getRef();
		assertNotNull(act);
		assertEquals(exp.getName(), act.getName());
		assertFalse(r.next());
	}

	@Test
	public void oneTagRef() throws IOException {
		Ref exp = tag(V1_0, 1, 2);
		byte[] table = write(exp);
		assertEquals(8 + 2 + V1_0.length() + 40 + 12 + 16, table.length);

		ReftableReader r = seekToFirstRef(table);
		assertTrue(r.next());
		Ref act = r.getRef();
		assertNotNull(act);
		assertEquals(PACKED, act.getStorage());
		assertTrue(act.isPeeled());
		assertFalse(act.isSymbolic());
		assertEquals(exp.getName(), act.getName());
		assertEquals(exp.getObjectId(), act.getObjectId());
		assertEquals(exp.getPeeledObjectId(), act.getPeeledObjectId());
	}

	@Test
	public void oneSymbolicRef() throws IOException {
		Ref exp = sym(HEAD, MASTER);
		byte[] table = write(exp);
		assertEquals(
				8 + 2 + HEAD.length() + 1 + MASTER.length() + 12 + 16,
				table.length);

		ReftableReader r = seekToFirstRef(table);
		assertTrue(r.next());
		Ref act = r.getRef();
		assertNotNull(act);
		assertTrue(act.isSymbolic());
		assertEquals(exp.getName(), act.getName());
		assertNotNull(act.getLeaf());
		assertEquals(MASTER, act.getTarget().getName());
		assertNull(act.getObjectId());
	}

	@Test
	public void oneDeletedRef() throws IOException {
		Ref exp = newRef("refs/heads/gone");
		byte[] table = write(exp);
		assertEquals(8 + 2 + exp.getName().length() + 12 + 16, table.length);

		ReftableReader r = seekToFirstRef(table);
		assertTrue(r.next());
		Ref act = r.getRef();
		assertNotNull(act);
		assertFalse(act.isSymbolic());
		assertEquals(exp.getName(), act.getName());
		assertEquals(NEW, act.getStorage());
		assertNull(act.getObjectId());
	}

	@Test
	public void seekNotFound() throws IOException {
		Ref exp = ref(MASTER, 1);
		ReftableReader r = read(write(exp));
		r.seek("refs/heads/a");
		assertFalse(r.next());

		r.seek("refs/heads/n");
		assertFalse(r.next());
	}

	@Test
	public void unpeeledDoesNotWrite() {
		try {
			write(new ObjectIdRef.Unpeeled(PACKED, MASTER, id(1)));
			fail("expected IOException");
		} catch (IOException e) {
			assertEquals(JGitText.get().peeledRefIsRequired, e.getMessage());
		}
	}

	@Test
	public void badCrc32() throws IOException {
		byte[] table = write();
		table[table.length - 1] = 0x42;

		try {
			assertFalse(seek(table, HEAD).next());
			fail("expected IOException");
		} catch (IOException e) {
			assertEquals(JGitText.get().invalidReftableCRC, e.getMessage());
		}
	}


	private static Ref ref(String name, int id) {
		return new ObjectIdRef.PeeledNonTag(PACKED, name, id(id));
	}

	private static Ref tag(String name, int id1, int id2) {
		return new ObjectIdRef.PeeledTag(PACKED, name, id(id1), id(id2));
	}

	private static Ref sym(String name, String target) {
		return new SymbolicRef(name, newRef(target));
	}

	private static Ref newRef(String name) {
		return new ObjectIdRef.Unpeeled(NEW, name, null);
	}

	private static ObjectId id(int i) {
		byte[] buf = new byte[OBJECT_ID_LENGTH];
		buf[0] = (byte) (i & 0xff);
		buf[1] = (byte) ((i >>> 8) & 0xff);
		buf[2] = (byte) ((i >>> 16) & 0xff);
		buf[3] = (byte) (i >>> 24);
		return ObjectId.fromRaw(buf);
	}

	private static ReftableReader seekToFirstRef(byte[] table)
			throws IOException {
		ReftableReader r = read(table);
		r.seekToFirstRef();
		return r;
	}

	private static ReftableReader seek(byte[] table, String name)
			throws IOException {
		ReftableReader r = read(table);
		r.seek(name);
		return r;
	}

	private static ReftableReader read(byte[] table) {
		return new ReftableReader(BlockSource.from(table));
	}

	private static byte[] write(Ref... refs) throws IOException {
		return write(Arrays.asList(refs));
	}

	private static byte[] write(Collection<Ref> refs) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter().begin(buffer);
		for (Ref r : RefComparator.sort(refs)) {
			writer.write(r);
		}
		writer.finish();
		return buffer.toByteArray();
	}
}
