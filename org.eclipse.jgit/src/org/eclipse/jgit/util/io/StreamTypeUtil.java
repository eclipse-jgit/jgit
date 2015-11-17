/*
 * Copyright (C) 2015, Ivan Motsch <ivan.motsch@bsiag.com>
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

package org.eclipse.jgit.util.io;

import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.lib.CoreConfig.StreamType;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;

/**
 * Convenience utility used to create input and output stream wrappers depending
 * on {@link StreamType}
 *
 * @since 4.3
 */
public final class StreamTypeUtil {
	private StreamTypeUtil() {
	}

	/**
	 * Convenience method used to detect if CRLF conversion has been configured
	 * using the
	 * <ul>
	 * <li>global repo options</li>
	 * <li>global attributes</li>
	 * <li>info attributes</li>
	 * <li>working tree .gitattributes</li>
	 *
	 * @param op
	 * @param options
	 * @param attrs
	 * @return the stream conversion {@link StreamType} to be performed for the
	 *         selected {@link OperationType}
	 */
	public static StreamType detectStreamType(OperationType op,
			WorkingTreeOptions options, Attributes attrs) {
		switch (op) {
		case CHECKIN_OP:
			return checkInStreamType(options, attrs);
		case CHECKOUT_OP:
			return checkOutStreamType(options, attrs);
		default:
			throw new IllegalArgumentException("unknown OperationType " + op); //$NON-NLS-1$
		}
	}

	/**
	 * @param in
	 * @param conversion
	 * @return the converted stream depending on {@link StreamType}
	 */
	public static InputStream wrapInputStream(InputStream in,
			StreamType conversion) {
		switch (conversion) {
		case TEXT_CRLF:
			return new AutoCRLFInputStream(in, false);
		case TEXT_LF:
			return new AutoLFInputStream(in, false);
		case AUTO_CRLF:
			return new AutoCRLFInputStream(in, true);
		case AUTO_LF:
			return new AutoLFInputStream(in, true);
		default:
			return in;
		}
	}

	/**
	 * @param out
	 * @param conversion
	 * @return the converted stream depending on {@link StreamType}
	 */
	public static OutputStream wrapOutputStream(OutputStream out,
			StreamType conversion) {
		switch (conversion) {
		case TEXT_CRLF:
		case AUTO_CRLF:
			return new AutoCRLFOutputStream(out);
		case TEXT_LF:
		case AUTO_LF:
			return new AutoLFOutputStream(out);
		default:
			return out;
		}
	}

	private static StreamType checkInStreamType(WorkingTreeOptions options,
			Attributes attrs) {
		if (attrs.isUnset("text")) {//$NON-NLS-1$
			return StreamType.DIRECT;
		}

		String eol = attrs.getValue("eol"); //$NON-NLS-1$
		if (eol != null && "crlf".equals(eol)) //$NON-NLS-1$
			return StreamType.TEXT_LF;
		if (eol != null && "lf".equals(eol)) //$NON-NLS-1$
			return StreamType.TEXT_LF;

		if (attrs.isSet("text")) { //$NON-NLS-1$
			return StreamType.TEXT_LF;
		}

		if ("auto".equals(attrs.getValue("text"))) { //$NON-NLS-1$ //$NON-NLS-2$
				return StreamType.AUTO_LF;
		}

		switch (options.getAutoCRLF()) {
		case FALSE:
			return StreamType.DIRECT;
		case TRUE:
			return StreamType.AUTO_LF;
		case INPUT:
			return StreamType.AUTO_LF;
		}

		return StreamType.DIRECT;
	}

	private static StreamType checkOutStreamType(WorkingTreeOptions options,
			Attributes attrs) {
		if (attrs.isUnset("text")) {//$NON-NLS-1$
			return StreamType.DIRECT;
		}

		String eol = attrs.getValue("eol"); //$NON-NLS-1$
		if (eol != null && "crlf".equals(eol)) //$NON-NLS-1$
			return StreamType.TEXT_CRLF;
		if (eol != null && "lf".equals(eol)) //$NON-NLS-1$
			return isForceEOL() ? StreamType.TEXT_LF : StreamType.DIRECT;

		if (attrs.isSet("text")) { //$NON-NLS-1$
			switch (options.getAutoCRLF()) {
			case FALSE:
				switch (options.getEOL()) {
				case CRLF:
					return StreamType.TEXT_CRLF;
				case LF:
					return isForceEOL() ? StreamType.TEXT_LF
							: StreamType.DIRECT;
				case NATIVE:
				default:
					return isForceEOL() ? (isCRLFSystem() ? StreamType.TEXT_CRLF
							: StreamType.TEXT_LF) : StreamType.DIRECT;
				}
			case TRUE:
				return StreamType.TEXT_CRLF;
			case INPUT:
				// conflicts with (EOL.CRLF || (EOL.NATIVE && isCRLFSystem))
				return isForceEOL() ? StreamType.TEXT_LF : StreamType.DIRECT;
			default:
				// no decision
			}
		}

		if ("auto".equals(attrs.getValue("text"))) { //$NON-NLS-1$ //$NON-NLS-2$
			switch (options.getAutoCRLF()) {
			case FALSE:
				switch (options.getEOL()) {
				case CRLF:
					return StreamType.AUTO_CRLF;
				case LF:
					return isForceEOL() ? StreamType.AUTO_LF
							: StreamType.DIRECT;
				case NATIVE:
				default:
					return isForceEOL() ? (isCRLFSystem() ? StreamType.AUTO_CRLF
							: StreamType.AUTO_LF) : StreamType.DIRECT;
				}
			case TRUE:
				return StreamType.AUTO_CRLF;
			case INPUT:
				// conflicts with (EOL.CRLF || (EOL.NATIVE && isCRLFSystem))
				return isForceEOL() ? StreamType.AUTO_LF : StreamType.DIRECT;
			default:
				// no decision
			}
		}

		switch (options.getAutoCRLF()) {
		case FALSE:
			return StreamType.DIRECT;
		case TRUE:
			return StreamType.AUTO_CRLF;
		case INPUT:
			return isForceEOL() ? StreamType.AUTO_LF : StreamType.DIRECT;
		}

		return StreamType.DIRECT;
	}

	private static boolean isCRLFSystem() {
		return "\r\n".equals(System.getProperty("line.separator")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * @return true if EOL should be enforced (gitbash uses enforcement, jgit by
	 *         default does not). The default is false in order to have better
	 *         performance
	 *
	 *         Use the system property jgit.forceEOL=true to activate EOL
	 *         enforcement
	 */
	private static boolean isForceEOL() {
		return "true".equals(System.getProperty("jgit.forceEOL", "false")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
