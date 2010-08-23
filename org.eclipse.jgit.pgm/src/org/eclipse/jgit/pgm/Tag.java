/*
 * Copyright (C) 2008, Charles O'Farrell <charleso@charleso.org>
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg.lists@dewire.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

import java.text.MessageFormat;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_CreateATag")
class Tag extends TextBuiltin {
	@Option(name = "-f", usage = "usage_forceReplacingAnExistingTag")
	private boolean force;

	@Option(name = "-m", metaVar = "metaVar_message", usage = "usage_tagMessage")
	private String message = "";

	@Argument(index = 0, required = true, metaVar = "metaVar_name")
	private String tagName;

	@Argument(index = 1, metaVar = "metaVar_object")
	private ObjectId object;

	@Override
	protected void run() throws Exception {
		if (object == null) {
			object = db.resolve(Constants.HEAD);
			if (object == null)
				throw die(MessageFormat.format(CLIText.get().cannotResolve, Constants.HEAD));
		}

		if (!tagName.startsWith(Constants.R_TAGS))
			tagName = Constants.R_TAGS + tagName;

		String shortName = tagName.substring(Constants.R_TAGS.length());
		if (!force && db.resolve(tagName) != null) {
			throw die(MessageFormat.format(CLIText.get().fatalErrorTagExists
					, shortName));
		}

		final ObjectLoader ldr = db.open(object);
		final ObjectInserter inserter = db.newObjectInserter();
		final ObjectId id;
		try {
			final org.eclipse.jgit.lib.TagBuilder tag;

			tag = new org.eclipse.jgit.lib.TagBuilder();
			tag.setObjectId(object, ldr.getType());
			tag.setTagger(new PersonIdent(db));
			tag.setMessage(message.replaceAll("\r", ""));
			tag.setTag(shortName);
			id = inserter.insert(tag);
			inserter.flush();
		} finally {
			inserter.release();
		}

		RefUpdate ru = db.updateRef(tagName);
		ru.setForceUpdate(force);
		ru.setNewObjectId(id);
		ru.setRefLogMessage("tagged " + shortName, false);
		switch (ru.update()) {
		case NEW:
		case FAST_FORWARD:
		case FORCED:
			break;

		case REJECTED:
			throw die(MessageFormat.format(CLIText.get().fatalErrorTagExists,
					shortName));

		default:
			throw die(MessageFormat.format(CLIText.get().failedToLockTag,
					shortName, ru.getResult()));
		}
	}
}
