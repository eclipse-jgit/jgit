/*
 * Copyright (C) 2007-2008, Charles O'Farrell <charleso@charleso.org>
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefComparator;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.pgm.opt.CmdLineParser;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.ExampleMode;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "List, create, or delete branches")
class Branch extends TextBuiltin {

	@Option(name = "--remote", aliases = { "-r" }, usage = "act on remote-tracking branches")
	private boolean remote = false;

	@Option(name = "--all", aliases = { "-a" }, usage = "list both remote-tracking and local branches")
	private boolean all = false;

	@Option(name = "--delete", aliases = { "-d" }, usage = "delete fully merged branch")
	private boolean delete = false;

	@Option(name = "--delete-force", aliases = { "-D" }, usage = "delete branch (even if not merged)")
	private boolean deleteForce = false;

	@Option(name = "--create-force", aliases = { "-f" }, usage = "force create branch even exists")
	private boolean createForce = false;

	@Option(name = "-m", usage = "move/rename a branch")
	private boolean rename = false;

	@Option(name = "--verbose", aliases = { "-v" }, usage = "be verbose")
	private boolean verbose = false;

	@Argument
	private List<String> branches = new ArrayList<String>();

	private final Map<String, Ref> printRefs = new LinkedHashMap<String, Ref>();

	/** Only set for verbose branch listing at-the-moment */
	private RevWalk rw;

	private int maxNameLength;

	@Override
	protected void run() throws Exception {
		if (delete || deleteForce)
			delete(deleteForce);
		else {
			if (branches.size() > 2)
				throw die("Too many refs given\n" + new CmdLineParser(this).printExample(ExampleMode.ALL));

			if (rename) {
				String src, dst;
				if (branches.size() == 1) {
					final Ref head = db.getRef(Constants.HEAD);
					if (head != null && head.isSymbolic())
						src = head.getLeaf().getName();
					else
						throw die("Cannot rename detached HEAD");
					dst = branches.get(0);
				} else {
					src = branches.get(0);
					final Ref old = db.getRef(src);
					if (old == null)
						throw die(String.format("%s does not exist", src));
					if (!old.getName().startsWith(Constants.R_HEADS))
						throw die(String.format("%s is not a branch", src));
					src = old.getName();
					dst = branches.get(1);
				}

				if (!dst.startsWith(Constants.R_HEADS))
					dst = Constants.R_HEADS + dst;
				if (!Repository.isValidRefName(dst))
					throw die(String.format("%s is not a valid ref name", dst));

				RefRename r = db.renameRef(src, dst);
				if (r.rename() != Result.RENAMED)
					throw die(String.format("%s cannot be renamed", src));

			} else if (branches.size() > 0) {
				String newHead = branches.get(0);
				String startBranch;
				if (branches.size() == 2)
					startBranch = branches.get(1);
				else
					startBranch = Constants.HEAD;
				Ref startRef = db.getRef(startBranch);
				ObjectId startAt = db.resolve(startBranch + "^0");
				if (startRef != null)
					startBranch = startRef.getName();
				else
					startBranch = startAt.name();
				startBranch = db.shortenRefName(startBranch);
				String newRefName = newHead;
				if (!newRefName.startsWith(Constants.R_HEADS))
					newRefName = Constants.R_HEADS + newRefName;
				if (!Repository.isValidRefName(newRefName))
					throw die(String.format("%s is not a valid ref name", newRefName));
				if (!createForce && db.resolve(newRefName) != null)
					throw die(String.format("branch %s already exists", newHead));
				RefUpdate updateRef = db.updateRef(newRefName);
				updateRef.setNewObjectId(startAt);
				updateRef.setForceUpdate(createForce);
				updateRef.setRefLogMessage("branch: Created from " + startBranch, false);
				Result update = updateRef.update();
				if (update == Result.REJECTED)
					throw die(String.format("Could not create branch %s: %s", newHead, update.toString()));
			} else {
				if (verbose)
					rw = new RevWalk(db);
				list();
			}
		}
	}

	private void list() throws Exception {
		Map<String, Ref> refs = db.getAllRefs();
		Ref head = refs.get(Constants.HEAD);
		// This can happen if HEAD is stillborn
		if (head != null) {
			String current = head.getLeaf().getName();
			if (current.equals(Constants.HEAD))
				addRef("(no branch)", head);
			addRefs(refs, Constants.R_HEADS, !remote);
			addRefs(refs, Constants.R_REMOTES, remote);
			for (final Entry<String, Ref> e : printRefs.entrySet()) {
				final Ref ref = e.getValue();
				printHead(e.getKey(), current.equals(ref.getName()), ref);
			}
		}
	}

	private void addRefs(final Map<String, Ref> allRefs, final String prefix,
			final boolean add) {
		if (all || add) {
			for (final Ref ref : RefComparator.sort(allRefs.values())) {
				final String name = ref.getName();
				if (name.startsWith(prefix))
					addRef(name.substring(name.indexOf('/', 5) + 1), ref);
			}
		}
	}

	private void addRef(final String name, final Ref ref) {
		printRefs.put(name, ref);
		maxNameLength = Math.max(maxNameLength, name.length());
	}

	private void printHead(final String ref, final boolean isCurrent,
			final Ref refObj) throws Exception {
		out.print(isCurrent ? '*' : ' ');
		out.print(' ');
		out.print(ref);
		if (verbose) {
			final int spaces = maxNameLength - ref.length() + 1;
			out.print(String.format("%" + spaces + "s", ""));
			final ObjectId objectId = refObj.getObjectId();
			out.print(objectId.abbreviate(db).name());
			out.print(' ');
			out.print(rw.parseCommit(objectId).getShortMessage());
		}
		out.println();
	}

	private void delete(boolean force) throws IOException {
		String current = db.getBranch();
		ObjectId head = db.resolve(Constants.HEAD);
		for (String branch : branches) {
			if (current.equals(branch)) {
				String err = "Cannot delete the branch '%s' which you are currently on.";
				throw die(String.format(err, branch));
			}
			RefUpdate update = db.updateRef((remote ? Constants.R_REMOTES
					: Constants.R_HEADS)
					+ branch);
			update.setNewObjectId(head);
			update.setForceUpdate(force || remote);
			Result result = update.delete();
			if (result == Result.REJECTED) {
				String err = "The branch '%s' is not an ancestor of your current HEAD.\n"
						+ "If you are sure you want to delete it, run 'jgit branch -D %1$s'.";
				throw die(String.format(err, branch));
			} else if (result == Result.NEW)
				throw die(String.format("branch '%s' not found.", branch));
			if (remote)
				out.println(String.format("Deleted remote branch %s", branch));
			else if (verbose)
				out.println(String.format("Deleted branch %s", branch));
		}
	}
}
