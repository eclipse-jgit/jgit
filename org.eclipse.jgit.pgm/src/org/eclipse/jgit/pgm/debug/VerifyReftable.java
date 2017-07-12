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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.RefCursor;
import org.eclipse.jgit.internal.storage.reftable.ReftableReader;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.kohsuke.args4j.Argument;

@Command
class VerifyReftable extends TextBuiltin {
	private static final long SEED1 = 0xaba8bb4de4caf86cL;
	private static final long SEED2 = 0x28bb5c25ad43ecb5L;

	@Argument(index = 0)
	private String lsRemotePath;

	@Argument(index = 1)
	private String reftablePath;

	@Override
	protected void run() throws Exception {
		List<Ref> refs = WriteReftable.readRefs(lsRemotePath);

		try (FileInputStream in = new FileInputStream(reftablePath);
				BlockSource src = BlockSource.from(in);
				ReftableReader reader = new ReftableReader(src)) {
			scan(refs, reader);
			seek(refs, reader);
			byId(refs, reader);
		}
	}

	@SuppressWarnings("nls")
	private void scan(List<Ref> refs, ReftableReader reader)
			throws IOException {
		errw.print(String.format("%-20s", "sequential scan..."));
		errw.flush();
		try (RefCursor rc = reader.allRefs()) {
			for (Ref exp : refs) {
				verify(exp, rc);
			}
			if (rc.next()) {
				throw die("expected end of table");
			}
		}
		errw.println(" OK");
	}

	@SuppressWarnings("nls")
	private void seek(List<Ref> refs, ReftableReader reader)
			throws IOException {
		List<Ref> rnd = new ArrayList<>(refs);
		Collections.shuffle(rnd, new Random(SEED1));

		TextProgressMonitor pm = new TextProgressMonitor(errw);
		pm.beginTask("random seek", rnd.size());
		for (Ref exp : rnd) {
			try (RefCursor rc = reader.seekRef(exp.getName())) {
				verify(exp, rc);
				if (rc.next()) {
					throw die("should not have ref after " + exp.getName());
				}
			}
			pm.update(1);
		}
		pm.endTask();
	}

	@SuppressWarnings("nls")
	private void byId(List<Ref> refs, ReftableReader reader)
			throws IOException {
		Map<ObjectId, List<Ref>> want = groupById(refs);
		List<List<Ref>> rnd = new ArrayList<>(want.values());
		Collections.shuffle(rnd, new Random(SEED2));

		TextProgressMonitor pm = new TextProgressMonitor(errw);
		pm.beginTask("byObjectId", rnd.size());
		for (List<Ref> exp : rnd) {
			Collections.sort(exp, RefComparator.INSTANCE);
			ObjectId id = exp.get(0).getObjectId();
			try (RefCursor rc = reader.byObjectId(id)) {
				for (Ref r : exp) {
					verify(r, rc);
				}
			}
			pm.update(1);
		}
		pm.endTask();
	}

	private static Map<ObjectId, List<Ref>> groupById(List<Ref> refs) {
		Map<ObjectId, List<Ref>> m = new HashMap<>();
		for (Ref r : refs) {
			ObjectId id = r.getObjectId();
			if (id != null) {
				List<Ref> c = m.get(id);
				if (c == null) {
					c = new ArrayList<>(2);
					m.put(id, c);
				}
				c.add(r);
			}
		}
		return m;
	}

	@SuppressWarnings("nls")
	private void verify(Ref exp, RefCursor rc) throws IOException {
		if (!rc.next()) {
			throw die("ended before " + exp.getName());
		}

		Ref act = rc.getRef();
		if (!exp.getName().equals(act.getName())) {
			throw die(String.format("expected %s, found %s",
					exp.getName(),
					act.getName()));
		}

		if (exp.isSymbolic()) {
			if (!act.isSymbolic()) {
				throw die("expected " + act.getName() + " to be symbolic");
			}
			if (!exp.getTarget().getName().equals(act.getTarget().getName())) {
				throw die(String.format("expected %s to be %s, found %s",
						exp.getName(),
						exp.getLeaf().getName(),
						act.getLeaf().getName()));
			}
			return;
		}

		if (!AnyObjectId.equals(exp.getObjectId(), act.getObjectId())) {
			throw die(String.format("expected %s to be %s, found %s",
					exp.getName(),
					id(exp.getObjectId()),
					id(act.getObjectId())));
		}

		if (exp.getPeeledObjectId() != null
				&& !AnyObjectId.equals(exp.getPeeledObjectId(), act.getPeeledObjectId())) {
			throw die(String.format("expected %s to be %s, found %s",
					exp.getName(),
					id(exp.getPeeledObjectId()),
					id(act.getPeeledObjectId())));
		}
	}

	@SuppressWarnings("nls")
	private static String id(ObjectId id) {
		return id != null ? id.name() : "<null>";
	}
}
