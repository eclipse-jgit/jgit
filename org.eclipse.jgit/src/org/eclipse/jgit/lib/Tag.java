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

import org.eclipse.jgit.revwalk.RevObject;

/**
 * Mutable builder to construct an annotated tag recording a project state.
 *
 * Applications should use this object when they need to manually construct a
 * tag and want precise control over its fields.
 */
public class Tag {
	private ObjectId tagId;

	private ObjectId object;

	private int type = Constants.OBJ_BAD;

	private String tag;

	private PersonIdent tagger;

	private String message;

	/** @return this tag's object id. */
	public ObjectId getTagId() {
		return tagId;
	}

	/**
	 * Set the id of this tag object.
	 *
	 * @param id
	 *            the id that we calculated for this object.
	 */
	public void setTagId(ObjectId id) {
		tagId = id;
	}

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
		tagId = null;
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
		tagId = null;
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
		tagId = null;
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
		tagId = null;
	}

	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		r.append("Tag");
		if (tagId != null)
			r.append("[" + tagId.name() + "]");
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
