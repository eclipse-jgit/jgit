/*
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.jgit.attributes.AttributeSet;
import org.eclipse.jgit.lib.CoreConfig.StreamType;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.util.Debug;

/**
 * Factory used to handle end-of-line conversion
 */
public final class StreamConversionFactory {
	private StreamConversionFactory() {
	}

	/**
	 * @param in
	 * @param conversion
	 * @return the converted stream depending on {@link StreamType}
	 */
	public static InputStream checkInStream(InputStream in,
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
	public static OutputStream checkOutStream(OutputStream out,
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

	/**
	 * Convenience method used to determine if CRLF conversion has been
	 * configured using the global CRLF options <b>and</b> also the global, info
	 * and working tree .gitattributes
	 *
	 * @param repo
	 *
	 * @param path
	 *            is the relative path of the file or directory in the
	 *            {@link Repository#getWorkTree()}
	 * @param fileMode
	 * @return the stream conversion to be performed on check-in
	 * @throws IOException
	 */
	public static StreamType checkInStreamType(Repository repo, String path,
			FileMode fileMode) throws IOException {
		StreamType streamType = checkInStreamType0(repo, path, fileMode);
		if (Debug.isDetail())
			Debug.println(" check-in  " + streamType + " " + path); //$NON-NLS-1$ //$NON-NLS-2$
		return streamType;
	}

	/**
	 * @param repo
	 * @param path
	 * @param fileMode
	 * @return the stream conversion to be performed on check-out
	 * @throws IOException
	 */
	public static StreamType checkOutStreamType(Repository repo,
			String path,
			FileMode fileMode) throws IOException {
		StreamType streamType = checkOutStreamType0(repo, path, fileMode);
		if (Debug.isDetail())
			Debug.println(" check-out " + streamType + " " + path); //$NON-NLS-1$ //$NON-NLS-2$
		return streamType;
	}

	private static StreamType checkInStreamType0(Repository repo, String path,
			FileMode fileMode) throws IOException {
		AttributeSet atts = repo.getAttributesHierarchy().getAttributes(path,
				fileMode);
		if (atts.isUnset("text")) {//$NON-NLS-1$
			return StreamType.DIRECT;
		}

		String eol = atts.getValue("eol"); //$NON-NLS-1$
		if (eol != null && "crlf".equals(eol)) //$NON-NLS-1$
			return StreamType.TEXT_LF;
		if (eol != null && "lf".equals(eol)) //$NON-NLS-1$
			return StreamType.TEXT_LF;

		if (atts.isSet("text")) { //$NON-NLS-1$
			return StreamType.TEXT_LF;
		}

		if ("auto".equals(atts.getValue("text"))) { //$NON-NLS-1$ //$NON-NLS-2$
				return StreamType.AUTO_LF;
		}

		WorkingTreeOptions options = repo.getConfig()
				.get(WorkingTreeOptions.KEY);

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

	private static StreamType checkOutStreamType0(Repository repo, String path,
			FileMode fileMode) throws IOException {
		AttributeSet atts = repo.getAttributesHierarchy().getAttributes(path,
				fileMode);

		if (atts.isUnset("text")) {//$NON-NLS-1$
			return StreamType.DIRECT;
		}

		String eol = atts.getValue("eol"); //$NON-NLS-1$
		if (eol != null && "crlf".equals(eol)) //$NON-NLS-1$
			return StreamType.TEXT_CRLF;
		if (eol != null && "lf".equals(eol)) //$NON-NLS-1$
			return isForceEOL() ? StreamType.TEXT_LF : StreamType.DIRECT;

		WorkingTreeOptions options = repo.getConfig()
				.get(WorkingTreeOptions.KEY);

		if (atts.isSet("text")) { //$NON-NLS-1$
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

		if ("auto".equals(atts.getValue("text"))) { //$NON-NLS-1$ //$NON-NLS-2$
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

	private static boolean isCRLFSystem() {
		return "\r\n".equals(System.getProperty("line.separator")); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
