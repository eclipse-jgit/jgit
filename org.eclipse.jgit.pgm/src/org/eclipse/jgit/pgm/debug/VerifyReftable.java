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
import java.util.List;
import java.util.Random;

import org.eclipse.jgit.internal.storage.reftable.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.ReftableReader;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.pgm.Command;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.kohsuke.args4j.Argument;

@Command
class VerifyReftable extends TextBuiltin {
	private static final long SEED = 0xaba8bb4de4caf86cL;

	@Argument(index = 0)
	private String lsRemotePath;

	@Argument(index = 1)
	private String reftablePath;

	@Override
	protected void run() throws Exception {
		List<Ref> refs = WriteReftable.read(lsRemotePath);

		try (FileInputStream in = new FileInputStream(reftablePath);
				BlockSource src = BlockSource.from(in);
				ReftableReader reader = new ReftableReader(src)) {
			scan(refs, reader);
			seek(refs, reader);
		}
	}

	@SuppressWarnings("nls")
	private void scan(List<Ref> refs, ReftableReader reader)
			throws IOException {
		errw.print(String.format("%-20s", "sequential scan..."));
		errw.flush();

		reader.seekToFirstRef();
		for (Ref exp : refs) {
			verify(exp, reader);
		}
		if (reader.next()) {
			throw die("expected end of table");
		}
		errw.println(" OK");
	}

	@SuppressWarnings("nls")
	private void seek(List<Ref> refs, ReftableReader reader)
			throws IOException {
		List<Ref> rnd = new ArrayList<>(refs);
		Collections.shuffle(rnd, new Random(SEED));

		TextProgressMonitor pm = new TextProgressMonitor(errw);
		pm.beginTask("random seek", rnd.size());

		for (Ref exp : refs) {
			reader.seek(exp.getName());
			verify(exp, reader);
			if (reader.next()) {
				throw die("should not have more refs after " + exp.getName());
			}
			pm.update(1);
		}
		pm.endTask();
		errw.println(String.format("%-20s OK", "random seek..."));
	}

	@SuppressWarnings("nls")
	private void verify(Ref exp, ReftableReader reader) throws IOException {
		if (!reader.next()) {
			throw die("ended before " + exp.getName());
		}

		Ref act = reader.getRef();
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
