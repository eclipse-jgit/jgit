/*
 * Copyright (C) 2021, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api.errors;

import java.text.MessageFormat;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

/**
 * A given object is not of an expected object type.
 *
 * @since 5.11
 */
public class WrongObjectTypeException extends GitAPIException {

	private static final long serialVersionUID = 1L;

	private String name;

	private int type;

	/**
	 * Construct a {@link WrongObjectTypeException} for the specified object id,
	 * giving the expected type.
	 *
	 * @param id
	 *            {@link ObjectId} of the object with the unexpected type
	 * @param type
	 *            expected object type code; see
	 *            {@link Constants}{@code .OBJ_*}.
	 */
	public WrongObjectTypeException(ObjectId id, int type) {
		super(MessageFormat.format(JGitText.get().objectIsNotA, id.name(),
				Constants.typeString(type)));
		this.name = id.name();
		this.type = type;
	}

	/**
	 * Retrieves the name (SHA-1) of the object.
	 *
	 * @return the name
	 */
	public String getObjectId() {
		return name;
	}

	/**
	 * Retrieves the expected type code. See {@link Constants}{@code .OBJ_*}.
	 *
	 * @return the type code
	 */
	public int getExpectedType() {
		return type;
	}
}
