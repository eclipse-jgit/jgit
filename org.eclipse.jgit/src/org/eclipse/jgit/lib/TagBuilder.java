/*
 * Copyright (C) 2006, 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, 2020, Chris Aniszczyk <caniszczyk@gmail.com> and others
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
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.util.References;

/**
 * Mutable builder to construct an annotated tag recording a project state.
 *
 * Applications should use this object when they need to manually construct a
 * tag and want precise control over its fields.
 *
 * To read a tag object, construct a {@link org.eclipse.jgit.revwalk.RevWalk}
 * and obtain a {@link org.eclipse.jgit.revwalk.RevTag} instance by calling
 * {@link org.eclipse.jgit.revwalk.RevWalk#parseTag(AnyObjectId)}.
 */
public class TagBuilder extends ObjectBuilder {

	private static final byte[] hobject = Constants.encodeASCII("object"); //$NON-NLS-1$

	private static final byte[] htype = Constants.encodeASCII("type"); //$NON-NLS-1$

	private static final byte[] htag = Constants.encodeASCII("tag"); //$NON-NLS-1$

	private static final byte[] htagger = Constants.encodeASCII("tagger"); //$NON-NLS-1$

	private ObjectId object;

	private int type = Constants.OBJ_BAD;

	private String tag;

	/**
	 * Get the type of object this tag refers to.
	 *
	 * @return the type of object this tag refers to.
	 */
	public int getObjectType() {
		return type;
	}

	/**
	 * Get the object this tag refers to.
	 *
	 * @return the object this tag refers to.
	 */
	public ObjectId getObjectId() {
		return object;
	}

	/**
	 * Set the object this tag refers to, and its type.
	 *
	 * @param obj
	 *            the object.
	 * @param objType
	 *            the type of {@code obj}. Must be a valid type code.
	 */
	public void setObjectId(AnyObjectId obj, int objType) {
		object = obj.copy();
		type = objType;
	}

	/**
	 * Set the object this tag refers to, and infer its type.
	 *
	 * @param obj
	 *            the object the tag will refer to.
	 */
	public void setObjectId(RevObject obj) {
		setObjectId(obj, obj.getType());
	}

	/**
	 * Get short name of the tag (no {@code refs/tags/} prefix).
	 *
	 * @return short name of the tag (no {@code refs/tags/} prefix).
	 */
	public String getTag() {
		return tag;
	}

	/**
	 * Set the name of this tag.
	 *
	 * @param shortName
	 *            new short name of the tag. This short name should not start
	 *            with {@code refs/} as typically a tag is stored under the
	 *            reference derived from {@code "refs/tags/" + getTag()}.
	 */
	public void setTag(String shortName) {
		this.tag = shortName;
	}

	/**
	 * Get creator of this tag.
	 *
	 * @return creator of this tag. May be null.
	 */
	public PersonIdent getTagger() {
		return getAuthor();
	}

	/**
	 * Set the creator of this tag.
	 *
	 * @param taggerIdent
	 *            the creator. May be null.
	 */
	public void setTagger(PersonIdent taggerIdent) {
		setAuthor(taggerIdent);
	}

	/**
	 * Format this builder's state as an annotated tag object.
	 *
	 * @return this object in the canonical annotated tag format, suitable for
	 *         storage in a repository.
	 */
	@Override
	public byte[] build() throws UnsupportedEncodingException {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try (OutputStreamWriter w = new OutputStreamWriter(os,
				getEncoding())) {

			os.write(hobject);
			os.write(' ');
			getObjectId().copyTo(os);
			os.write('\n');

			os.write(htype);
			os.write(' ');
			os.write(Constants
					.encodeASCII(Constants.typeString(getObjectType())));
			os.write('\n');

			os.write(htag);
			os.write(' ');
			w.write(getTag());
			w.flush();
			os.write('\n');

			if (getTagger() != null) {
				os.write(htagger);
				os.write(' ');
				w.write(getTagger().toExternalString());
				w.flush();
				os.write('\n');
			}

			writeEncoding(getEncoding(), os);

			os.write('\n');
			String msg = getMessage();
			if (msg != null) {
				w.write(msg);
				w.flush();
			}

			GpgSignature signature = getGpgSignature();
			if (signature != null) {
				if (msg != null && !msg.isEmpty() && !msg.endsWith("\n")) { //$NON-NLS-1$
					// If signed, the message *must* end with a linefeed
					// character, otherwise signature verification will fail.
					// (The signature will have been computed over the payload
					// containing the message without LF, but will be verified
					// against a payload with the LF.) The signature must start
					// on a new line.
					throw new JGitInternalException(
							JGitText.get().signedTagMessageNoLf);
				}
				String externalForm = signature.toExternalString();
				w.write(externalForm);
				w.flush();
				if (!externalForm.endsWith("\n")) { //$NON-NLS-1$
					os.write('\n');
				}
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
	 * Format this builder's state as an annotated tag object.
	 *
	 * @return this object in the canonical annotated tag format, suitable for
	 *         storage in a repository, or {@code null} if the tag cannot be
	 *         encoded
	 * @deprecated since 5.11; use {@link #build()} instead
	 */
	@Deprecated
	public byte[] toByteArray() {
		try {
			return build();
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}

	@SuppressWarnings("nls")
	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		r.append("Tag");
		r.append("={\n");

		r.append("object ");
		r.append(object != null ? object.name() : "NOT_SET");
		r.append("\n");

		r.append("type ");
		r.append(object != null ? Constants.typeString(type) : "NOT_SET");
		r.append("\n");

		r.append("tag ");
		r.append(tag != null ? tag : "NOT_SET");
		r.append("\n");

		if (getTagger() != null) {
			r.append("tagger ");
			r.append(getTagger());
			r.append("\n");
		}

		Charset encoding = getEncoding();
		if (!References.isSameObject(encoding, UTF_8)) {
			r.append("encoding ");
			r.append(encoding.name());
			r.append("\n");
		}

		r.append("\n");
		r.append(getMessage() != null ? getMessage() : "");
		GpgSignature signature = getGpgSignature();
		r.append(signature != null ? signature.toExternalString() : "");
		r.append("}");
		return r.toString();
	}
}
