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

package org.eclipse.jgit.pgm.debug;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.MASTER;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.RefCursor;
import org.eclipse.jgit.internal.storage.reftable.ReftableReader;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.util.RefList;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command
class BenchmarkReftable extends TextBuiltin {
	enum Test {
		SCAN,
		SEEK_COLD, SEEK_HOT,
		BY_ID_COLD, BY_ID_HOT;
	}

	@Option(name = "--tries")
	private int tries = 10;

	@Option(name = "--test")
	private Test test = Test.SCAN;

	@Option(name = "--ref")
	private String ref;

	@Option(name = "--object-id")
	private String objectId;

	@Argument(index = 0)
	private String lsRemotePath;

	@Argument(index = 1)
	private String reftablePath;

	@Override
	protected void run() throws Exception {
		switch (test) {
		case SCAN:
			scan();
			break;

		case SEEK_COLD:
			seekCold(ref);
			break;
		case SEEK_HOT:
			seekHot(ref);
			break;

		case BY_ID_COLD:
			byIdCold(ObjectId.fromString(objectId));
			break;
		case BY_ID_HOT:
			byIdHot(ObjectId.fromString(objectId));
			break;
		}
	}

	private void printf(String fmt, Object... args) throws IOException {
		errw.println(String.format(fmt, args));
	}

	@SuppressWarnings({ "nls", "boxing" })
	private void scan() throws Exception {
		long start, tot;

		start = System.currentTimeMillis();
		for (int i = 0; i < tries; i++) {
			readLsRemote();
		}
		tot = System.currentTimeMillis() - start;
		printf("%12s %10d ms  %6d ms/run", "packed-refs", tot, tot / tries);

		start = System.currentTimeMillis();
		for (int i = 0; i < tries; i++) {
			try (FileInputStream in = new FileInputStream(reftablePath);
					BlockSource src = BlockSource.from(in);
					ReftableReader reader = new ReftableReader(src)) {
				try (RefCursor rc = reader.allRefs()) {
					while (rc.next()) {
						rc.getRef();
					}
				}
			}
		}
		tot = System.currentTimeMillis() - start;
		printf("%12s %10d ms  %6d ms/run", "reftable", tot, tot / tries);
	}

	private RefList<Ref> readLsRemote()
			throws IOException, FileNotFoundException {
		RefList.Builder<Ref> list = new RefList.Builder<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(lsRemotePath), UTF_8))) {
			Ref last = null;
			String line;
			while ((line = br.readLine()) != null) {
				ObjectId id = ObjectId.fromString(line.substring(0, 40));
				String name = line.substring(41, line.length());
				if (last != null && name.endsWith("^{}")) { //$NON-NLS-1$
					last = new ObjectIdRef.PeeledTag(PACKED, last.getName(),
							last.getObjectId(), id);
					list.set(list.size() - 1, last);
					continue;
				}

				if (name.equals(HEAD)) {
					last = new SymbolicRef(name, new ObjectIdRef.Unpeeled(NEW,
							R_HEADS + MASTER, null));
				} else {
					last = new ObjectIdRef.PeeledNonTag(PACKED, name, id);
				}
				list.add(last);
			}
		}
		list.sort();
		return list.toRefList();
	}

	@SuppressWarnings({ "nls", "boxing" })
	private void seekCold(String refName) throws Exception {
		long start, tot;

		int lsTries = Math.min(tries, 64);
		start = System.nanoTime();
		for (int i = 0; i < lsTries; i++) {
			readLsRemote().get(refName);
		}
		tot = System.nanoTime() - start;
		printf("%12s %10d usec  %9.1f usec/run  %5d runs", "packed-refs",
				tot / 1000,
				(((double) tot) / lsTries) / 1000,
				lsTries);

		start = System.nanoTime();
		for (int i = 0; i < tries; i++) {
			try (FileInputStream in = new FileInputStream(reftablePath);
					BlockSource src = BlockSource.from(in);
					ReftableReader reader = new ReftableReader(src)) {
				try (RefCursor rc = reader.seekRef(refName)) {
					while (rc.next()) {
						rc.getRef();
					}
				}
			}
		}
		tot = System.nanoTime() - start;
		printf("%12s %10d usec  %9.1f usec/run  %5d runs", "reftable",
				tot / 1000,
				(((double) tot) / tries) / 1000,
				tries);
	}

	@SuppressWarnings({ "nls", "boxing" })
	private void seekHot(String refName) throws Exception {
		long start, tot;

		int lsTries = Math.min(tries, 64);
		start = System.nanoTime();
		RefList<Ref> lsRemote = readLsRemote();
		for (int i = 0; i < lsTries; i++) {
			lsRemote.get(refName);
		}
		tot = System.nanoTime() - start;
		printf("%12s %10d usec  %9.1f usec/run  %5d runs", "packed-refs",
				tot / 1000, (((double) tot) / lsTries) / 1000, lsTries);

		start = System.nanoTime();
		try (FileInputStream in = new FileInputStream(reftablePath);
				BlockSource src = BlockSource.from(in);
				ReftableReader reader = new ReftableReader(src)) {
			for (int i = 0; i < tries; i++) {
				try (RefCursor rc = reader.seekRef(refName)) {
					while (rc.next()) {
						rc.getRef();
					}
				}
			}
		}
		tot = System.nanoTime() - start;
		printf("%12s %10d usec  %9.1f usec/run  %5d runs", "reftable",
				tot / 1000, (((double) tot) / tries) / 1000, tries);
	}

	@SuppressWarnings({ "nls", "boxing" })
	private void byIdCold(ObjectId id) throws Exception {
		long start, tot;

		int lsTries = Math.min(tries, 64);
		start = System.nanoTime();
		for (int i = 0; i < lsTries; i++) {
			for (Ref r : readLsRemote()) {
				if (id.equals(r.getObjectId())) {
					continue;
				}
			}
		}
		tot = System.nanoTime() - start;
		printf("%12s %10d usec  %9.1f usec/run  %5d runs", "packed-refs",
				tot / 1000, (((double) tot) / lsTries) / 1000, lsTries);

		start = System.nanoTime();
		for (int i = 0; i < tries; i++) {
			try (FileInputStream in = new FileInputStream(reftablePath);
					BlockSource src = BlockSource.from(in);
					ReftableReader reader = new ReftableReader(src)) {
				try (RefCursor rc = reader.byObjectId(id)) {
					while (rc.next()) {
						rc.getRef();
					}
				}
			}
		}
		tot = System.nanoTime() - start;
		printf("%12s %10d usec  %9.1f usec/run  %5d runs", "reftable",
				tot / 1000, (((double) tot) / tries) / 1000, tries);
	}

	@SuppressWarnings({ "nls", "boxing" })
	private void byIdHot(ObjectId id) throws Exception {
		long start, tot;

		int lsTries = Math.min(tries, 64);
		start = System.nanoTime();
		RefList<Ref> lsRemote = readLsRemote();
		for (int i = 0; i < lsTries; i++) {
			for (Ref r : lsRemote) {
				if (id.equals(r.getObjectId())) {
					continue;
				}
			}
		}
		tot = System.nanoTime() - start;
		printf("%12s %10d usec  %9.1f usec/run  %5d runs", "packed-refs",
				tot / 1000, (((double) tot) / lsTries) / 1000, lsTries);

		start = System.nanoTime();
		try (FileInputStream in = new FileInputStream(reftablePath);
				BlockSource src = BlockSource.from(in);
				ReftableReader reader = new ReftableReader(src)) {
			for (int i = 0; i < tries; i++) {
				try (RefCursor rc = reader.byObjectId(id)) {
					while (rc.next()) {
						rc.getRef();
					}
				}
			}
		}
		tot = System.nanoTime() - start;
		printf("%12s %10d usec  %9.1f usec/run  %5d runs", "reftable",
				tot / 1000, (((double) tot) / tries) / 1000, tries);
	}
}
