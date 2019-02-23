/*
 * Copyright (C) 2018-2019, Andre Bossert <andre.bossert@siemens.com>
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

import static org.eclipse.jgit.lib.Constants.HEAD;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.diff.ContentSource;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.Side;
import org.eclipse.jgit.diffmergetool.BooleanOption;
import org.eclipse.jgit.diffmergetool.DiffToolManager;
import org.eclipse.jgit.diffmergetool.IDiffTool;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.PathTreeFilterHandler;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(name = "difftool", common = true, usage = "usage_DiffTool")
class DiffTool extends TextBuiltin {
	private DiffFormatter diffFmt;

	private DiffToolManager diffToolMgr;

	@Argument(index = 0, metaVar = "metaVar_treeish")
	private AbstractTreeIterator oldTree;

	@Argument(index = 1, metaVar = "metaVar_treeish")
	private AbstractTreeIterator newTree;

	@Option(name = "--tool", aliases = {
			"-t" }, metaVar = "metaVar_tool", usage = "usage_ToolForDiff")
	private String toolName;

	@Option(name = "--cached", aliases = { "--staged" }, usage = "usage_cached")
	private boolean cached;

	private BooleanOption prompt = BooleanOption.notDefinedFalse;

	@Option(name = "--prompt", usage = "usage_prompt")
	void setPrompt(@SuppressWarnings("unused") boolean on) {
		prompt = BooleanOption.True;
	}

	@Option(name = "--no-prompt", aliases = { "-y" }, usage = "usage_noPrompt")
	void noPrompt(@SuppressWarnings("unused") boolean on) {
		prompt = BooleanOption.False;
	}

	@Option(name = "--tool-help", usage = "usage_toolHelp")
	private boolean toolHelp;

	private BooleanOption gui = BooleanOption.notDefinedFalse;

	@Option(name = "--gui", aliases = { "-g" }, usage = "usage_Gui")
	void setGui(@SuppressWarnings("unused") boolean on) {
		gui = BooleanOption.True;
	}

	@Option(name = "--no-gui", usage = "usage_noGui")
	void noGui(@SuppressWarnings("unused") boolean on) {
		gui = BooleanOption.False;
	}

	private BooleanOption trustExitCode = BooleanOption.notDefinedFalse;

	@Option(name = "--trust-exit-code", usage = "usage_trustExitCode")
	void setTrustExitCode(@SuppressWarnings("unused") boolean on) {
		trustExitCode = BooleanOption.True;
	}

	@Option(name = "--no-trust-exit-code", usage = "usage_noTrustExitCode")
	void noTrustExitCode(@SuppressWarnings("unused") boolean on) {
		trustExitCode = BooleanOption.False;
	}

	@Option(name = "--", metaVar = "metaVar_paths", handler = PathTreeFilterHandler.class)
	private TreeFilter pathFilter = TreeFilter.ALL;

	@Override
	protected void init(Repository repository, String gitDir) {
		super.init(repository, gitDir);
		diffFmt = new DiffFormatter(new BufferedOutputStream(outs));
		diffToolMgr = new DiffToolManager(repository);
	}

	@Override
	protected void run() {
		try {
			if (toolHelp) {
				outw.println(
						"'git difftool --tool=<tool>' may be set to one of the following:"); //$NON-NLS-1$
				for (String name : diffToolMgr.getAvailableTools().keySet()) {
					outw.println("\t\t" + name); //$NON-NLS-1$
				}
				outw.println(""); //$NON-NLS-1$
				outw.println("\tuser-defined:"); //$NON-NLS-1$
				Map<String, IDiffTool> userTools = diffToolMgr
						.getUserDefinedTools();
				for (String name : userTools.keySet()) {
					outw.println("\t\t" + name + ".cmd " //$NON-NLS-1$ //$NON-NLS-2$
							+ userTools.get(name).getCommand());
				}
				outw.println(""); //$NON-NLS-1$
				outw.println(
						"The following tools are valid, but not currently available:"); //$NON-NLS-1$
				for (String name : diffToolMgr.getNotAvailableTools()
						.keySet()) {
					outw.println("\t\t" + name); //$NON-NLS-1$
				}
				outw.println(""); //$NON-NLS-1$
				outw.println("Some of the tools listed above only work in a windowed"); //$NON-NLS-1$
				outw.println(
						"environment. If run in a terminal-only session, they will fail."); //$NON-NLS-1$
				return;
			}
			diffFmt.setRepository(db);
			if (cached) {
				if (oldTree == null) {
					ObjectId head = db.resolve(HEAD + "^{tree}"); //$NON-NLS-1$
					if (head == null) {
						die(MessageFormat.format(CLIText.get().notATree, HEAD));
					}
					CanonicalTreeParser p = new CanonicalTreeParser();
					try (ObjectReader reader = db.newObjectReader()) {
						p.reset(reader, head);
					}
					oldTree = p;
				}
				newTree = new DirCacheIterator(db.readDirCache());
			} else if (oldTree == null) {
				oldTree = new DirCacheIterator(db.readDirCache());
				newTree = new FileTreeIterator(db);
			} else if (newTree == null) {
				newTree = new FileTreeIterator(db);
			}

			TextProgressMonitor pm = new TextProgressMonitor(errw);
			pm.setDelayStart(2, TimeUnit.SECONDS);
			diffFmt.setProgressMonitor(pm);
			diffFmt.setPathFilter(pathFilter);

			List<DiffEntry> files = diffFmt.scan(oldTree, newTree);
			ContentSource.Pair sourcePair = new ContentSource.Pair(
					source(oldTree), source(newTree));

			/*
			 * TODO: this only is for prototyping and will be removed with next
			 * commit:
			 */
			for (DiffEntry ent : files) {
				switch (ent.getChangeType()) {
				case MODIFY:
					outw.println("M\t" + ent.getNewPath() //$NON-NLS-1$
							+ " (" + ent.getNewId().name() + ")" //$NON-NLS-1$ //$NON-NLS-2$
							+ "\t" + ent.getOldPath() //$NON-NLS-1$
							+ " (" + ent.getOldId().name() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
					outw.println("--- NEW-DATA ---"); //$NON-NLS-1$

					ObjectStream newFileStream = sourcePair.open(Side.NEW, ent)
							.openStream();
					showStream(newFileStream);
					outw.println("--- OLD-DATA ---"); //$NON-NLS-1$
					ObjectStream oldFileStream = sourcePair.open(Side.OLD, ent)
							.openStream();
					showStream(oldFileStream);

					diffToolMgr.compare(ent.getNewPath(),
							ent.getOldPath(), ent.getNewId().name(),
							ent.getOldId().name(), toolName, prompt, gui,
							trustExitCode);
					break;
				default:
					break;
				}
			}

			outw.flush();
		} catch (RevisionSyntaxException | IOException e) {
			throw die(e.getMessage(), e);
		} finally {
			diffFmt.close();
		}

	}

	private ContentSource source(AbstractTreeIterator iterator) {
		if (iterator instanceof WorkingTreeIterator)
			return ContentSource.create((WorkingTreeIterator) iterator);
		return ContentSource.create(db.newObjectReader());
	}

	private void showStream(ObjectStream stream)
			throws UnsupportedEncodingException, IOException {
		int read = 0;
		byte[] bytes = new byte[1024];
		while ((read = stream.read(bytes)) != -1) {
			outw.write(new String(bytes, "UTF-8").toCharArray(), 0, read); //$NON-NLS-1$
		}
	}
}
