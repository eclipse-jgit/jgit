/*
 * Copyright (C) 2020, Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lib;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Objects;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.util.References;

/**
 * Common base class for {@link CommitBuilder} and {@link TagBuilder}.
 *
 * @since 5.11
 */
public abstract class ObjectBuilder {

	/** Byte representation of "encoding". */
	private static final byte[] hencoding = Constants.encodeASCII("encoding"); //$NON-NLS-1$

	private PersonIdent author;

	private GpgSignature gpgSignature;

	private String message;

	private Charset encoding = StandardCharsets.UTF_8;

	/**
	 * Retrieves the author of this object.
	 *
	 * @return the author of this object, or {@code null} if not set yet
	 */
	protected PersonIdent getAuthor() {
		return author;
	}

	/**
	 * Sets the author (name, email address, and date) of this object.
	 *
	 * @param newAuthor
	 *            the new author, must be non-{@code null}
	 */
	protected void setAuthor(PersonIdent newAuthor) {
		author = Objects.requireNonNull(newAuthor);
	}

	/**
	 * Sets the GPG signature of this object.
	 * <p>
	 * Note, the signature set here will change the payload of the object, i.e.
	 * the output of {@link #build()} will include the signature. Thus, the
	 * typical flow will be:
	 * <ol>
	 * <li>call {@link #build()} without a signature set to obtain payload</li>
	 * <li>create {@link GpgSignature} from payload</li>
	 * <li>set {@link GpgSignature}</li>
	 * </ol>
	 *
	 * @param gpgSignature
	 *            the signature to set or {@code null} to unset
	 * @since 5.3
	 */
	public void setGpgSignature(@Nullable GpgSignature gpgSignature) {
		this.gpgSignature = gpgSignature;
	}

	/**
	 * Retrieves the GPG signature of this object.
	 *
	 * @return the GPG signature of this object, or {@code null} if the object
	 *         is not signed
	 * @since 5.3
	 */
	@Nullable
	public GpgSignature getGpgSignature() {
		return gpgSignature;
	}

	/**
	 * Retrieves the complete message of the object.
	 *
	 * @return the complete message; can be {@code null}.
	 */
	@Nullable
	public String getMessage() {
		return message;
	}

	/**
	 * Sets the message (commit message, or message of an annotated tag).
	 *
	 * @param message
	 *            the message.
	 */
	public void setMessage(@Nullable String message) {
		this.message = message;
	}

	/**
	 * Retrieves the encoding that should be used for the message text.
	 *
	 * @return the encoding that should be used for the message text.
	 */
	@NonNull
	public Charset getEncoding() {
		return encoding;
	}

	/**
	 * Sets the encoding for the object message.
	 *
	 * @param encoding
	 *            the encoding to use.
	 */
	public void setEncoding(@NonNull Charset encoding) {
		this.encoding = encoding;
	}

	/**
	 * Format this builder's state as a git object.
	 *
	 * @return this object in the canonical git format, suitable for storage in
	 *         a repository.
	 * @throws java.io.UnsupportedEncodingException
	 *             the encoding specified by {@link #getEncoding()} is not
	 *             supported by this Java runtime.
	 */
	@NonNull
	public abstract byte[] build() throws UnsupportedEncodingException;

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
	 * @param enforceAscii
	 *            whether to throw {@link IllegalArgumentException} if non-ASCII
	 *            characters are encountered
	 * @throws IOException
	 *             thrown by the output stream
	 * @throws IllegalArgumentException
	 *             if the signature string contains non 7-bit ASCII chars and
	 *             {@code enforceAscii == true}
	 */
	static void writeMultiLineHeader(@NonNull String in,
			@NonNull OutputStream out, boolean enforceAscii)
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
				if (ch > 127 && enforceAscii)
					throw new IllegalArgumentException(MessageFormat
							.format(JGitText.get().notASCIIString, in));
				out.write(ch);
				break;
			}
		}
	}

	/**
	 * Writes an "encoding" header.
	 *
	 * @param encoding
	 *            to write
	 * @param out
	 *            to write to
	 * @throws IOException
	 *             if writing fails
	 */
	static void writeEncoding(@NonNull Charset encoding,
			@NonNull OutputStream out) throws IOException {
		if (!References.isSameObject(encoding, UTF_8)) {
			out.write(hencoding);
			out.write(' ');
			out.write(Constants.encodeASCII(encoding.name()));
			out.write('\n');
		}
	}
}
