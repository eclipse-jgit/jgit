/*
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Charles O'Farrell <charleso@charleso.org>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg.lists@dewire.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, 2021 Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListTagCommand;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.api.VerificationResult;
import org.eclipse.jgit.api.VerifySignatureCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.GpgSignatureVerifier.SignatureVerification;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.internal.VerificationUtils;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_CreateATag")
class Tag extends TextBuiltin {

	@Option(name = "--force", aliases = { "-f" }, forbids = { "--delete",
			"--verify" }, usage = "usage_forceReplacingAnExistingTag")
	private boolean force;

	@Option(name = "--delete", aliases = { "-d" }, forbids = {
			"--verify" }, usage = "usage_tagDelete")
	private boolean delete;

	@Option(name = "--annotate", aliases = {
			"-a" }, forbids = { "--delete",
					"--verify" }, usage = "usage_tagAnnotated")
	private boolean annotated;

	@Option(name = "-m", forbids = { "--delete",
			"--verify" }, metaVar = "metaVar_message", usage = "usage_tagMessage")
	private String message;

	@Option(name = "--sign", aliases = { "-s" }, forbids = {
			"--no-sign", "--delete", "--verify" }, usage = "usage_tagSign")
	private boolean sign;

	@Option(name = "--no-sign", usage = "usage_tagNoSign", forbids = {
			"--sign", "--delete", "--verify" })
	private boolean noSign;

	@Option(name = "--local-user", aliases = {
			"-u" }, forbids = { "--delete",
					"--verify" }, metaVar = "metaVar_tagLocalUser", usage = "usage_tagLocalUser")
	private String gpgKeyId;

	@Option(name = "--verify", aliases = { "-v" }, forbids = { "--delete",
			"--force", "--annotate", "-m", "--sign", "--no-sign",
			"--local-user" }, usage = "usage_tagVerify")
	private boolean verify;

	@Option(name = "--contains", forbids = { "--delete", "--force",
			"--annotate", "-m", "--sign", "--no-sign",
			"--local-user" }, metaVar = "metaVar_commitish", usage = "usage_tagContains")
	private RevCommit contains;

	@Argument(index = 0, metaVar = "metaVar_name")
	private String tagName;

	@Argument(index = 1, metaVar = "metaVar_object")
	private ObjectId object;

	@Override
	protected void run() {
		try (Git git = new Git(db)) {
			if (tagName != null) {
				if (verify) {
					VerifySignatureCommand verifySig = git.verifySignature()
							.setMode(VerifySignatureCommand.VerifyMode.TAGS)
							.addName(tagName);

					VerificationResult verification = verifySig.call()
							.get(tagName);
					if (verification == null) {
						showUnsigned(git, tagName);
					} else {
						Throwable error = verification.getException();
						if (error != null) {
							throw die(error.getMessage(), error);
						}
						writeVerification(verifySig.getVerifier().getName(),
								(RevTag) verification.getObject(),
								verification.getVerification());
					}
				} else if (delete) {
					List<String> deletedTags = git.tagDelete().setTags(tagName)
							.call();
					if (deletedTags.isEmpty()) {
						throw die(MessageFormat
								.format(CLIText.get().tagNotFound, tagName));
					}
				} else {
					TagCommand command = git.tag().setForceUpdate(force)
							.setMessage(message).setName(tagName);

					if (object != null) {
						try (RevWalk walk = new RevWalk(db)) {
							command.setObjectId(walk.parseAny(object));
						}
					}
					if (noSign) {
						command.setSigned(false);
					} else if (sign) {
						command.setSigned(true);
					}
					if (annotated) {
						command.setAnnotated(true);
					} else if (message == null && !sign && gpgKeyId == null) {
						// None of -a, -m, -s, -u given
						command.setAnnotated(false);
					}
					command.setSigningKey(gpgKeyId);
					try {
						command.call();
					} catch (RefAlreadyExistsException e) {
						throw die(MessageFormat.format(
								CLIText.get().tagAlreadyExists, tagName), e);
					}
				}
			} else {
				ListTagCommand command = git.tagList();
				if (contains != null) {
					command.setContains(contains);
				}
				List<Ref> list = command.call();
				for (Ref ref : list) {
					outw.println(Repository.shortenRefName(ref.getName()));
				}
			}
		} catch (GitAPIException | IOException e) {
			throw die(e.getMessage(), e);
		}
	}

	private void showUnsigned(Git git, String wantedTag) throws IOException {
		ObjectId id = git.getRepository().resolve(wantedTag);
		if (id != null && !ObjectId.zeroId().equals(id)) {
			try (RevWalk walk = new RevWalk(git.getRepository())) {
				showTag(walk.parseTag(id));
			}
		} else {
			throw die(
					MessageFormat.format(CLIText.get().tagNotFound, wantedTag));
		}
	}

	private void showTag(RevTag tag) throws IOException {
		outw.println("object " + tag.getObject().name()); //$NON-NLS-1$
		outw.println("type " + Constants.typeString(tag.getObject().getType())); //$NON-NLS-1$
		outw.println("tag " + tag.getTagName()); //$NON-NLS-1$
		outw.println("tagger " + tag.getTaggerIdent().toExternalString()); //$NON-NLS-1$
		outw.println();
		outw.print(tag.getFullMessage());
	}

	private void writeVerification(String name, RevTag tag,
			SignatureVerification verification) throws IOException {
		showTag(tag);
		if (verification == null) {
			outw.println();
			return;
		}
		VerificationUtils.writeVerification(outw, verification, name,
				tag.getTaggerIdent());
	}
}
