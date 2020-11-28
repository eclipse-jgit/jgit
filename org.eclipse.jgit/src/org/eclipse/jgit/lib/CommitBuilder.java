/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2006-2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2007, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.lib;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.References;

/**
 * Mutable builder to construct a commit recording the state of a project.
 *
 * Applications should use this object when they need to manually construct a
 * commit and want precise control over its fields. For a higher level interface
 * see {@link org.eclipse.jgit.api.CommitCommand}.
 *
 * To read a commit object, construct a {@link org.eclipse.jgit.revwalk.RevWalk}
 * and obtain a {@link org.eclipse.jgit.revwalk.RevCommit} instance by calling
 * {@link org.eclipse.jgit.revwalk.RevWalk#parseCommit(AnyObjectId)}.
 */
public class CommitBuilder {
	private static final ObjectId[] EMPTY_OBJECTID_LIST = new ObjectId[0];

	private static final byte[] htree = Constants.encodeASCII("tree"); //$NON-NLS-1$

	private static final byte[] hparent = Constants.encodeASCII("parent"); //$NON-NLS-1$

	private static final byte[] hauthor = Constants.encodeASCII("author"); //$NON-NLS-1$

	private static final byte[] hcommitter = Constants.encodeASCII("committer"); //$NON-NLS-1$

	private static final byte[] hgpgsig = Constants.encodeASCII("gpgsig"); //$NON-NLS-1$

	private static final byte[] hencoding = Constants.encodeASCII("encoding"); //$NON-NLS-1$

	private ObjectId treeId;

	private ObjectId[] parentIds;

	private PersonIdent author;

	private PersonIdent committer;

	private GpgSignature gpgSignature;

	private String message;

	private Charset encoding;

	/**
	 * Initialize an empty commit.
	 */
	public CommitBuilder() {
		parentIds = EMPTY_OBJECTID_LIST;
		encoding = UTF_8;
	}

	/**
	 * Get id of the root tree listing this commit's snapshot.
	 *
	 * @return id of the root tree listing this commit's snapshot.
	 */
	public ObjectId getTreeId() {
		return treeId;
	}

	/**
	 * Set the tree id for this commit object.
	 *
	 * @param id
	 *            the tree identity.
	 */
	public void setTreeId(AnyObjectId id) {
		treeId = id.copy();
	}

	/**
	 * Get the author of this commit (who wrote it).
	 *
	 * @return the author of this commit (who wrote it).
	 */
	public PersonIdent getAuthor() {
		return author;
	}

	/**
	 * Set the author (name, email address, and date) of who wrote the commit.
	 *
	 * @param newAuthor
	 *            the new author. Should not be null.
	 */
	public void setAuthor(PersonIdent newAuthor) {
		author = newAuthor;
	}

	/**
	 * Get the committer and commit time for this object.
	 *
	 * @return the committer and commit time for this object.
	 */
	public PersonIdent getCommitter() {
		return committer;
	}

	/**
	 * Set the committer and commit time for this object.
	 *
	 * @param newCommitter
	 *            the committer information. Should not be null.
	 */
	public void setCommitter(PersonIdent newCommitter) {
		committer = newCommitter;
	}

	/**
	 * Set the GPG signature of this commit.
	 * <p>
	 * Note, the signature set here will change the payload of the commit, i.e.
	 * the output of {@link #build()} will include the signature. Thus, the
	 * typical flow will be:
	 * <ol>
	 * <li>call {@link #build()} without a signature set to obtain payload</li>
	 * <li>create {@link GpgSignature} from payload</li>
	 * <li>set {@link GpgSignature}</li>
	 * </ol>
	 * </p>
	 *
	 * @param newSignature
	 *            the signature to set or <code>null</code> to unset
	 * @since 5.3
	 */
	public void setGpgSignature(GpgSignature newSignature) {
		gpgSignature = newSignature;
	}

	/**
	 * Get the GPG signature of this commit.
	 *
	 * @return the GPG signature of this commit, maybe <code>null</code> if the
	 *         commit is not to be signed
	 * @since 5.3
	 */
	public GpgSignature getGpgSignature() {
		return gpgSignature;
	}

	/**
	 * Get the ancestors of this commit.
	 *
	 * @return the ancestors of this commit. Never null.
	 */
	public ObjectId[] getParentIds() {
		return parentIds;
	}

	/**
	 * Set the parent of this commit.
	 *
	 * @param newParent
	 *            the single parent for the commit.
	 */
	public void setParentId(AnyObjectId newParent) {
		parentIds = new ObjectId[] { newParent.copy() };
	}

	/**
	 * Set the parents of this commit.
	 *
	 * @param parent1
	 *            the first parent of this commit. Typically this is the current
	 *            value of the {@code HEAD} reference and is thus the current
	 *            branch's position in history.
	 * @param parent2
	 *            the second parent of this merge commit. Usually this is the
	 *            branch being merged into the current branch.
	 */
	public void setParentIds(AnyObjectId parent1, AnyObjectId parent2) {
		parentIds = new ObjectId[] { parent1.copy(), parent2.copy() };
	}

	/**
	 * Set the parents of this commit.
	 *
	 * @param newParents
	 *            the entire list of parents for this commit.
	 */
	public void setParentIds(ObjectId... newParents) {
		parentIds = new ObjectId[newParents.length];
		for (int i = 0; i < newParents.length; i++)
			parentIds[i] = newParents[i].copy();
	}

	/**
	 * Set the parents of this commit.
	 *
	 * @param newParents
	 *            the entire list of parents for this commit.
	 */
	public void setParentIds(List<? extends AnyObjectId> newParents) {
		parentIds = new ObjectId[newParents.size()];
		for (int i = 0; i < newParents.size(); i++)
			parentIds[i] = newParents.get(i).copy();
	}

	/**
	 * Add a parent onto the end of the parent list.
	 *
	 * @param additionalParent
	 *            new parent to add onto the end of the current parent list.
	 */
	public void addParentId(AnyObjectId additionalParent) {
		if (parentIds.length == 0) {
			setParentId(additionalParent);
		} else {
			ObjectId[] newParents = new ObjectId[parentIds.length + 1];
			System.arraycopy(parentIds, 0, newParents, 0, parentIds.length);
			newParents[parentIds.length] = additionalParent.copy();
			parentIds = newParents;
		}
	}

	/**
	 * Get the complete commit message.
	 *
	 * @return the complete commit message.
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Set the commit message.
	 *
	 * @param newMessage
	 *            the commit message. Should not be null.
	 */
	public void setMessage(String newMessage) {
		message = newMessage;
	}

	/**
	 * Set the encoding for the commit information.
	 *
	 * @param encodingName
	 *            the encoding name. See
	 *            {@link java.nio.charset.Charset#forName(String)}.
	 * @deprecated use {@link #setEncoding(Charset)} instead.
	 */
	@Deprecated
	public void setEncoding(String encodingName) {
		encoding = Charset.forName(encodingName);
	}

	/**
	 * Set the encoding for the commit information.
	 *
	 * @param enc
	 *            the encoding to use.
	 */
	public void setEncoding(Charset enc) {
		encoding = enc;
	}

	/**
	 * Get the encoding that should be used for the commit message text.
	 *
	 * @return the encoding that should be used for the commit message text.
	 */
	public Charset getEncoding() {
		return encoding;
	}

	/**
	 * Format this builder's state as a commit object.
	 *
	 * @return this object in the canonical commit format, suitable for storage
	 *         in a repository.
	 * @throws java.io.UnsupportedEncodingException
	 *             the encoding specified by {@link #getEncoding()} is not
	 *             supported by this Java runtime.
	 */
	public byte[] build() throws UnsupportedEncodingException {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		OutputStreamWriter w = new OutputStreamWriter(os, getEncoding());
		try {
			os.write(htree);
			os.write(' ');
			getTreeId().copyTo(os);
			os.write('\n');

			for (ObjectId p : getParentIds()) {
				os.write(hparent);
				os.write(' ');
				p.copyTo(os);
				os.write('\n');
			}

			os.write(hauthor);
			os.write(' ');
			w.write(getAuthor().toExternalString());
			w.flush();
			os.write('\n');

			os.write(hcommitter);
			os.write(' ');
			w.write(getCommitter().toExternalString());
			w.flush();
			os.write('\n');

			if (getGpgSignature() != null) {
				os.write(hgpgsig);
				os.write(' ');
				writeGpgSignatureString(getGpgSignature().toExternalString(), os);
				os.write('\n');
			}

			if (!References.isSameObject(getEncoding(), UTF_8)) {
				os.write(hencoding);
				os.write(' ');
				os.write(Constants.encodeASCII(getEncoding().name()));
				os.write('\n');
			}

			os.write('\n');

			if (getMessage() != null) {
				w.write(getMessage());
				w.flush();
			}
		} catch (IOException err) {
			// This should never occur, the only way to get it above is
			// for the ByteArrayOutputStream to throw, but it doesn't.
			//
			throw new RuntimeException(err);
		}
		return os.toByteArray();
	}

	/**
	 * Writes signature to output as per <a href=
	 * "https://github.com/git/git/blob/master/Documentation/technical/signature-format.txt#L66,L89">gpgsig
	 * header</a>.
	 * <p>
	 * CRLF and CR will be sanitized to LF and signature will have a hanging
	 * indent of one space starting with line two. A trailing line break is
	 * <em>not</em> written; the caller is supposed to terminate the GPG
	 * signature header by writing a single newline.
	 * </p>
	 *
	 * @param in
	 *            signature string with line breaks
	 * @param out
	 *            output stream
	 * @throws IOException
	 *             thrown by the output stream
	 * @throws IllegalArgumentException
	 *             if the signature string contains non 7-bit ASCII chars
	 */
	static void writeGpgSignatureString(String in, OutputStream out)
			throws IOException, IllegalArgumentException {
		int length = in.length();
		for (int i = 0; i < length; ++i) {
			char ch = in.charAt(i);
			switch (ch) {
			case '\r':
				if (i + 1 < length && in.charAt(i + 1) == '\n') {
					++i;
				}
				if (i + 1 < length) {
					out.write('\n');
					out.write(' ');
				}
				break;
			case '\n':
				if (i + 1 < length) {
					out.write('\n');
					out.write(' ');
				}
				break;
			default:
				// sanity check
				if (ch > 127)
					throw new IllegalArgumentException(MessageFormat
							.format(JGitText.get().notASCIIString, in));
				out.write(ch);
				break;
			}
		}
	}

	/**
	 * Format this builder's state as a commit object.
	 *
	 * @return this object in the canonical commit format, suitable for storage
	 *         in a repository.
	 * @throws java.io.UnsupportedEncodingException
	 *             the encoding specified by {@link #getEncoding()} is not
	 *             supported by this Java runtime.
	 */
	public byte[] toByteArray() throws UnsupportedEncodingException {
		return build();
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		r.append("Commit");
		r.append("={\n");

		r.append("tree ");
		r.append(treeId != null ? treeId.name() : "NOT_SET");
		r.append("\n");

		for (ObjectId p : parentIds) {
			r.append("parent ");
			r.append(p.name());
			r.append("\n");
		}

		r.append("author ");
		r.append(author != null ? author.toString() : "NOT_SET");
		r.append("\n");

		r.append("committer ");
		r.append(committer != null ? committer.toString() : "NOT_SET");
		r.append("\n");

		r.append("gpgSignature ");
		r.append(gpgSignature != null ? gpgSignature.toString() : "NOT_SET");
		r.append("\n");

		if (encoding != null && !References.isSameObject(encoding, UTF_8)) {
			r.append("encoding ");
			r.append(encoding.name());
			r.append("\n");
		}

		r.append("\n");
		r.append(message != null ? message : "");
		r.append("}");
		return r.toString();
	}
}
