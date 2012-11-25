/*
 * Copyright (C) 2006-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
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

package org.eclipse.jgit.lib;

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

	/** @return the type of object this tag refers to. */
	public int getObjectType() {
		return type;
	}

	/** @return the object this tag refers to. */
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

	/** @return short name of the tag (no {@code refs/tags/} prefix). */
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

	/** @return creator of this tag. May be null. */
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

	/** @return the complete commit message. */
	public String getMessage() {
		return message;
	}

	/**
	 * Set the tag's message.
	 *
	 * @param newMessage
	 *            the tag's message.
	 */
	public void setMessage(final String newMessage) {
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
		OutputStreamWriter w = new OutputStreamWriter(os, Constants.CHARSET);
		try {
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
			w.close();
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
