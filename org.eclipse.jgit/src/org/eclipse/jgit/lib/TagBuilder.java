/*
 * Copyright (C) 2006-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com> and others
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

import org.eclipse.jgit.revwalk.RevObject;

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
public class TagBuilder {
	private ObjectId object;

	private int type = Constants.OBJ_BAD;

	private String tag;

	private PersonIdent tagger;

	private String message;

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
		return tagger;
	}

	/**
	 * Set the creator of this tag.
	 *
	 * @param taggerIdent
	 *            the creator. May be null.
	 */
	public void setTagger(PersonIdent taggerIdent) {
		tagger = taggerIdent;
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
	 * Set the tag's message.
	 *
	 * @param newMessage
	 *            the tag's message.
	 */
	public void setMessage(String newMessage) {
		message = newMessage;
	}

	/**
	 * Format this builder's state as an annotated tag object.
	 *
	 * @return this object in the canonical annotated tag format, suitable for
	 *         storage in a repository.
	 */
	public byte[] build() {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try (OutputStreamWriter w = new OutputStreamWriter(os,
				UTF_8)) {
			w.write("object "); //$NON-NLS-1$
			getObjectId().copyTo(w);
			w.write('\n');

			w.write("type "); //$NON-NLS-1$
			w.write(Constants.typeString(getObjectType()));
			w.write("\n"); //$NON-NLS-1$

			w.write("tag "); //$NON-NLS-1$
			w.write(getTag());
			w.write("\n"); //$NON-NLS-1$

			if (getTagger() != null) {
				w.write("tagger "); //$NON-NLS-1$
				w.write(getTagger().toExternalString());
				w.write('\n');
			}

			w.write('\n');
			if (getMessage() != null)
				w.write(getMessage());
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
	 *         storage in a repository.
	 */
	public byte[] toByteArray() {
		return build();
	}

	/** {@inheritDoc} */
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

		if (tagger != null) {
			r.append("tagger ");
			r.append(tagger);
			r.append("\n");
		}

		r.append("\n");
		r.append(message != null ? message : "");
		r.append("}");
		return r.toString();
	}
}
