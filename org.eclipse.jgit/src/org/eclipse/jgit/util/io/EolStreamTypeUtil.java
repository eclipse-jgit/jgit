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
import org.eclipse.jgit.lib.CoreConfig.EolStreamType;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;

/**
 * Utility used to create input and output stream wrappers for
 * {@link org.eclipse.jgit.lib.CoreConfig.EolStreamType}
 *
 * @since 4.3
 */
public final class EolStreamTypeUtil {
	private static final boolean FORCE_EOL_LF_ON_CHECKOUT = false;

	private EolStreamTypeUtil() {
	}

	/**
	 * Convenience method used to detect if CRLF conversion has been configured
	 * using the
	 * <ul>
	 * <li>global repo options</li>
	 * <li>global attributes</li>
	 * <li>info attributes</li>
	 * <li>working tree .gitattributes</li>
	 * </ul>
	 *
	 * @param op
	 *            is the
	 *            {@link org.eclipse.jgit.treewalk.TreeWalk.OperationType} of
	 *            the current traversal
	 * @param options
	 *            are the {@link org.eclipse.jgit.lib.Config} options with key
	 *            {@link org.eclipse.jgit.treewalk.WorkingTreeOptions#KEY}
	 * @param attrs
	 *            are the {@link org.eclipse.jgit.attributes.Attributes} of the
	 *            file for which the
	 *            {@link org.eclipse.jgit.lib.CoreConfig.EolStreamType} is to be
	 *            detected
	 * @return the stream conversion
	 *         {@link org.eclipse.jgit.lib.CoreConfig.EolStreamType} to be
	 *         performed for the selected
	 *         {@link org.eclipse.jgit.treewalk.TreeWalk.OperationType}
	 */
	public static EolStreamType detectStreamType(OperationType op,
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
	 * Wrap the input stream depending on
	 * {@link org.eclipse.jgit.lib.CoreConfig.EolStreamType}
	 *
	 * @param in
	 *            original stream
	 * @param conversion
	 *            to be performed
	 * @return the converted stream depending on
	 *         {@link org.eclipse.jgit.lib.CoreConfig.EolStreamType}
	 */
	public static InputStream wrapInputStream(InputStream in,
			EolStreamType conversion) {
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
	 * Wrap the output stream depending on
	 * {@link org.eclipse.jgit.lib.CoreConfig.EolStreamType}
	 *
	 * @param out
	 *            original stream
	 * @param conversion
	 *            to be performed
	 * @return the converted stream depending on
	 *         {@link org.eclipse.jgit.lib.CoreConfig.EolStreamType}
	 */
	public static OutputStream wrapOutputStream(OutputStream out,
			EolStreamType conversion) {
		switch (conversion) {
		case TEXT_CRLF:
			return new AutoCRLFOutputStream(out, false);
		case AUTO_CRLF:
			return new AutoCRLFOutputStream(out, true);
		case TEXT_LF:
			return new AutoLFOutputStream(out, false);
		case AUTO_LF:
			return new AutoLFOutputStream(out, true);
		default:
			return out;
		}
	}

	private static EolStreamType checkInStreamType(WorkingTreeOptions options,
			Attributes attrs) {
		if (attrs.isUnset("text")) {//$NON-NLS-1$
			// "binary" or "-text" (which is included in the binary expansion)
			return EolStreamType.DIRECT;
		}

		// old git system
		if (attrs.isSet("crlf")) {//$NON-NLS-1$
			return EolStreamType.TEXT_LF;
		} else if (attrs.isUnset("crlf")) {//$NON-NLS-1$
			return EolStreamType.DIRECT;
		} else if ("input".equals(attrs.getValue("crlf"))) {//$NON-NLS-1$ //$NON-NLS-2$
			return EolStreamType.TEXT_LF;
		}

		// new git system
		String eol = attrs.getValue("eol"); //$NON-NLS-1$
		if (eol != null)
			// check-in is always normalized to LF
			return EolStreamType.TEXT_LF;

		if (attrs.isSet("text")) { //$NON-NLS-1$
			return EolStreamType.TEXT_LF;
		}

		if ("auto".equals(attrs.getValue("text"))) { //$NON-NLS-1$ //$NON-NLS-2$
			return EolStreamType.AUTO_LF;
		}

		switch (options.getAutoCRLF()) {
		case TRUE:
		case INPUT:
			return EolStreamType.AUTO_LF;
		case FALSE:
			return EolStreamType.DIRECT;
		}

		return EolStreamType.DIRECT;
	}

	private static EolStreamType checkOutStreamType(WorkingTreeOptions options,
			Attributes attrs) {
		if (attrs.isUnset("text")) {//$NON-NLS-1$
			// "binary" or "-text" (which is included in the binary expansion)
			return EolStreamType.DIRECT;
		}

		// old git system
		if (attrs.isSet("crlf")) {//$NON-NLS-1$
			return FORCE_EOL_LF_ON_CHECKOUT ? EolStreamType.TEXT_LF
					: EolStreamType.DIRECT;
		} else if (attrs.isUnset("crlf")) {//$NON-NLS-1$
			return EolStreamType.DIRECT;
		} else if ("input".equals(attrs.getValue("crlf"))) {//$NON-NLS-1$ //$NON-NLS-2$
			return EolStreamType.DIRECT;
		}

		// new git system
		String eol = attrs.getValue("eol"); //$NON-NLS-1$
		if (eol != null && "crlf".equals(eol)) //$NON-NLS-1$
			return EolStreamType.TEXT_CRLF;
		if (eol != null && "lf".equals(eol)) //$NON-NLS-1$
			return FORCE_EOL_LF_ON_CHECKOUT ? EolStreamType.TEXT_LF
					: EolStreamType.DIRECT;

		if (attrs.isSet("text")) { //$NON-NLS-1$
			switch (options.getAutoCRLF()) {
			case TRUE:
				return EolStreamType.TEXT_CRLF;
			default:
				// no decision
			}
			switch (options.getEOL()) {
			case CRLF:
				return EolStreamType.TEXT_CRLF;
			case LF:
				return FORCE_EOL_LF_ON_CHECKOUT ? EolStreamType.TEXT_LF
						: EolStreamType.DIRECT;
			case NATIVE:
			default:
				return EolStreamType.DIRECT;
			}
		}

		if ("auto".equals(attrs.getValue("text"))) { //$NON-NLS-1$ //$NON-NLS-2$
			switch (options.getAutoCRLF()) {
			case TRUE:
				return EolStreamType.AUTO_CRLF;
			default:
				// no decision
			}
			switch (options.getEOL()) {
			case CRLF:
				return EolStreamType.AUTO_CRLF;
			case LF:
				return FORCE_EOL_LF_ON_CHECKOUT ? EolStreamType.TEXT_LF
						: EolStreamType.DIRECT;
			case NATIVE:
			default:
				return EolStreamType.DIRECT;
			}
		}

		switch (options.getAutoCRLF()) {
		case TRUE:
			return EolStreamType.AUTO_CRLF;
		default:
			// no decision
		}

		return EolStreamType.DIRECT;
	}

}
