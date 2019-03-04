/*
 * Copyright (C) 2017, David Pursehouse <david.pursehouse@gmail.com>
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

package org.eclipse.jgit.lfs.server.internal;

import java.io.Reader;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

/**
 * Wrapper for {@link com.google.gson.Gson} used by LFS servlets.
 */
public class LfsGson {
	private static final Gson gson = new GsonBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			.disableHtmlEscaping()
			.create();

	/**
	 * Wrapper class only used for serialization of error messages.
	 */
	static class Error {
		String message;

		Error(String m) {
			this.message = m;
		}
	}

	/**
	 * Serializes the specified object into its equivalent Json representation.
	 *
	 * @param src
	 *            the object for which Json representation is to be created. If
	 *            this is a String, it is wrapped in an instance of
	 *            {@link org.eclipse.jgit.lfs.server.internal.LfsGson.Error}.
	 * @param writer
	 *            Writer to which the Json representation needs to be written
	 * @throws com.google.gson.JsonIOException
	 *             if there was a problem writing to the writer
	 * @see Gson#toJson(Object, Appendable)
	 */
	public static void toJson(Object src, Appendable writer)
			throws JsonIOException {
		if (src instanceof String) {
			gson.toJson(new Error((String) src), writer);
		} else {
			gson.toJson(src, writer);
		}
	}

	/**
	 * Deserializes the Json read from the specified reader into an object of
	 * the specified type.
	 *
	 * @param json
	 *            reader producing json from which the object is to be
	 *            deserialized
	 * @param classOfT
	 *            specified type to deserialize
	 * @return an Object of type T
	 * @throws com.google.gson.JsonIOException
	 *             if there was a problem reading from the Reader
	 * @throws com.google.gson.JsonSyntaxException
	 *             if json is not a valid representation for an object of type
	 * @see Gson#fromJson(Reader, java.lang.reflect.Type)
	 * @param <T>
	 *            a T object.
	 */
	public static <T> T fromJson(Reader json, Class<T> classOfT)
			throws JsonSyntaxException, JsonIOException {
		return gson.fromJson(json, classOfT);
	}
}
