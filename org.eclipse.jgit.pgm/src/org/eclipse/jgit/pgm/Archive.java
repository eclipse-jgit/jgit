/*
 * Copyright (C) 2012 Google Inc.
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

package org.eclipse.jgit.pgm;

import java.lang.String;
import java.lang.System;
import java.io.IOException;
import java.io.OutputStream;
import java.util.EnumMap;
import java.util.Map;
import java.text.MessageFormat;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.pgm.CLIText;
import org.eclipse.jgit.pgm.TextBuiltin;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_archive")
class Archive extends TextBuiltin {
	@Argument(index = 0, metaVar = "metaVar_treeish")
	private AbstractTreeIterator tree;

	@Option(name = "--format", metaVar = "metaVar_archiveFormat", usage = "usage_archiveFormat")
	private Format format = Format.ZIP;

	@Override
	protected void run() throws Exception {
		final TreeWalk walk = new TreeWalk(db);
		final ObjectReader reader = walk.getObjectReader();
		final MutableObjectId idBuf = new MutableObjectId();
		final Archiver fmt = formats.get(format);
		final ArchiveOutputStream out = fmt.createArchiveOutputStream(outs);

		if (tree == null)
			throw die(CLIText.get().treeIsRequired);

		walk.reset();
		walk.addTree(tree);
		walk.setRecursive(true);
		while (walk.next()) {
			final String name = walk.getPathString();
			final FileMode mode = walk.getFileMode(0);

			if (mode == FileMode.TREE)
				// ZIP entries for directories are optional.
				// Leave them out, mimicking "git archive".
				continue;

			walk.getObjectId(idBuf, 0);
			fmt.putEntry(name, mode, reader.open(idBuf), out);
		}

		out.close();
	}

	static private void warnArchiveEntryModeIgnored(String name) {
		System.err.println(MessageFormat.format( //
				CLIText.get().archiveEntryModeIgnored, //
				name));
	}

	public enum Format {
		ZIP,
		TAR
	}

	private static interface Archiver {
		ArchiveOutputStream createArchiveOutputStream(OutputStream s);
		void putEntry(String path, FileMode mode, //
				ObjectLoader loader, ArchiveOutputStream out) //
				throws IOException;
	}

	private static final Map<Format, Archiver> formats;
	static {
		Map<Format, Archiver> fmts = new EnumMap<Format, Archiver>(Format.class);
		fmts.put(Format.ZIP, new Archiver() {
			@Override
			public ArchiveOutputStream createArchiveOutputStream(OutputStream s) {
				return new ZipArchiveOutputStream(s);
			}

			@Override
			public void putEntry(String path, FileMode mode, //
					ObjectLoader loader, ArchiveOutputStream out) //
					throws IOException {
				final ZipArchiveEntry entry = new ZipArchiveEntry(path);

				if (mode == FileMode.REGULAR_FILE)
					; // ok
				else if (mode == FileMode.EXECUTABLE_FILE ||
					 mode == FileMode.SYMLINK)
					entry.setUnixMode(mode.getBits());
				else
					warnArchiveEntryModeIgnored(path);
				entry.setSize(loader.getSize());
				out.putArchiveEntry(entry);
				loader.copyTo(out);
				out.closeArchiveEntry();
			}
		});
		fmts.put(Format.TAR, new Archiver() {
			@Override
			public ArchiveOutputStream createArchiveOutputStream(OutputStream s) {
				return new TarArchiveOutputStream(s);
			}

			@Override
			public void putEntry(String path, FileMode mode, //
					ObjectLoader loader, ArchiveOutputStream out) //
					throws IOException {
				if (mode == FileMode.SYMLINK) {
					final TarArchiveEntry entry = new TarArchiveEntry( //
							path, TarConstants.LF_SYMLINK);
					entry.setLinkName(new String( //
						loader.getCachedBytes(100), "UTF-8"));
					out.putArchiveEntry(entry);
					out.closeArchiveEntry();
					return;
				}

				final TarArchiveEntry entry = new TarArchiveEntry(path);
				if (mode == FileMode.REGULAR_FILE ||
				    mode == FileMode.EXECUTABLE_FILE)
					entry.setMode(mode.getBits());
				else
					warnArchiveEntryModeIgnored(path);
				entry.setSize(loader.getSize());
				out.putArchiveEntry(entry);
				loader.copyTo(out);
				out.closeArchiveEntry();
			}
		});
		formats = fmts;
	}
}
