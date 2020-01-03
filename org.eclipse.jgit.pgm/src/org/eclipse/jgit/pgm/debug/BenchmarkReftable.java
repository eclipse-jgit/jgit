/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm.debug;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.MASTER;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import org.eclipse.jgit.internal.storage.file.FileReftableStack;
import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.RefCursor;
import org.eclipse.jgit.internal.storage.reftable.ReftableReader;
import org.eclipse.jgit.lib.Config;
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
		BY_ID_COLD, BY_ID_HOT,
		WRITE_STACK,
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

	/** {@inheritDoc} */
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
		case WRITE_STACK:
			writeStack();
			break;
		}
	}

	private void printf(String fmt, Object... args) throws IOException {
		errw.println(String.format(fmt, args));
	}

	@SuppressWarnings({ "nls", "boxing" })
	private void writeStack() throws Exception {
		File dir = new File(reftablePath);
		File stackFile = new File(reftablePath + ".stack");

		dir.mkdirs();

		long start = System.currentTimeMillis();
		try (FileReftableStack stack = new FileReftableStack(stackFile, dir,
				null, () -> new Config())) {

			List<Ref> refs = readLsRemote().asList();
			for (Ref r : refs) {
				final long j = stack.getMergedReftable().maxUpdateIndex() + 1;
				if (!stack.addReftable(w -> {
					w.setMaxUpdateIndex(j).setMinUpdateIndex(j).begin()
							.writeRef(r);
				})) {
					throw new IOException("should succeed");
				}
			}
			long dt = System.currentTimeMillis() - start;
			printf("%12s %10d ms  avg %6d us/write", "reftable", dt,
					(dt * 1000) / refs.size());
		}
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
