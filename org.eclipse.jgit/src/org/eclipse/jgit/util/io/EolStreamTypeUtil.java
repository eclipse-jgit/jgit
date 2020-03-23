/*
 * Copyright (C) 2015, Ivan Motsch <ivan.motsch@bsiag.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util.io;

import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.lib.CoreConfig.EolStreamType;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.util.SystemReader;

/**
 * Utility used to create input and output stream wrappers for
 * {@link org.eclipse.jgit.lib.CoreConfig.EolStreamType}
 *
 * @since 4.3
 */
public final class EolStreamTypeUtil {

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
			return EolStreamType.TEXT_LF; // Same as isSet("text")
		} else if (attrs.isUnset("crlf")) {//$NON-NLS-1$
			return EolStreamType.DIRECT; // Same as isUnset("text")
		} else if ("input".equals(attrs.getValue("crlf"))) {//$NON-NLS-1$ //$NON-NLS-2$
			return EolStreamType.TEXT_LF; // Same as eol=lf
		}

		// new git system
		if ("auto".equals(attrs.getValue("text"))) { //$NON-NLS-1$ //$NON-NLS-2$
			return EolStreamType.AUTO_LF;
		}

		String eol = attrs.getValue("eol"); //$NON-NLS-1$
		if (eol != null) {
			// check-in is always normalized to LF
			return EolStreamType.TEXT_LF;
		}
		if (attrs.isSet("text")) { //$NON-NLS-1$
			return EolStreamType.TEXT_LF;
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

	private static EolStreamType getOutputFormat(WorkingTreeOptions options) {
		switch (options.getAutoCRLF()) {
		case TRUE:
			return EolStreamType.TEXT_CRLF;
		default:
			// no decision
		}
		switch (options.getEOL()) {
		case CRLF:
			return EolStreamType.TEXT_CRLF;
		case NATIVE:
			if (SystemReader.getInstance().isWindows()) {
				return EolStreamType.TEXT_CRLF;
			}
			return EolStreamType.TEXT_LF;
		case LF:
		default:
			break;
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
			return getOutputFormat(options); // Same as isSet("text")
		} else if (attrs.isUnset("crlf")) {//$NON-NLS-1$
			return EolStreamType.DIRECT; // Same as isUnset("text")
		} else if ("input".equals(attrs.getValue("crlf"))) {//$NON-NLS-1$ //$NON-NLS-2$
			return EolStreamType.DIRECT; // Same as eol=lf
		}

		// new git system
		String eol = attrs.getValue("eol"); //$NON-NLS-1$
		if (eol != null) {
			if ("crlf".equals(eol)) { //$NON-NLS-1$
				if ("auto".equals(attrs.getValue("text"))) { //$NON-NLS-1$ //$NON-NLS-2$
					return EolStreamType.AUTO_CRLF;
				}
				return EolStreamType.TEXT_CRLF;
			} else if ("lf".equals(eol)) { //$NON-NLS-1$
				return EolStreamType.DIRECT;
			}
		}
		if (attrs.isSet("text")) { //$NON-NLS-1$
			return getOutputFormat(options);
		}

		if ("auto".equals(attrs.getValue("text"))) { //$NON-NLS-1$ //$NON-NLS-2$
			EolStreamType basic = getOutputFormat(options);
			switch (basic) {
			case TEXT_CRLF:
				return EolStreamType.AUTO_CRLF;
			case TEXT_LF:
				return EolStreamType.AUTO_LF;
			default:
				return basic;
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
