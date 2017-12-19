/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.transport;

import static org.eclipse.jgit.transport.SideBandOutputStream.HDR_SIZE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Unmultiplexes the data portion of a side-band channel.
 * <p>
 * Reading from this input stream obtains data from channel 1, which is
 * typically the bulk data stream.
 * <p>
 * Channel 2 is transparently unpacked and "scraped" to update a progress
 * monitor. The scraping is performed behind the scenes as part of any of the
 * read methods offered by this stream.
 * <p>
 * Channel 3 results in an exception being thrown, as the remote side has issued
 * an unrecoverable error.
 *
 * @see SideBandOutputStream
 */
class SideBandInputStream extends InputStream {
	static final int CH_DATA = 1;
	static final int CH_PROGRESS = 2;
	static final int CH_ERROR = 3;

	private static Pattern P_UNBOUNDED = Pattern
			.compile("^([\\w ]+): +(\\d+)(?:, done\\.)? *[\r\n]$"); //$NON-NLS-1$

	private static Pattern P_BOUNDED = Pattern
			.compile("^([\\w ]+): +\\d+% +\\( *(\\d+)/ *(\\d+)\\)(?:, done\\.)? *[\r\n]$"); //$NON-NLS-1$

	private final InputStream rawIn;

	private final PacketLineIn pckIn;

	private final ProgressMonitor monitor;

	private final Writer messages;

	private final OutputStream out;

	private String progressBuffer = ""; //$NON-NLS-1$

	private String currentTask;

	private int lastCnt;

	private boolean eof;

	private int channel;

	private int available;

	SideBandInputStream(final InputStream in, final ProgressMonitor progress,
			final Writer messageStream, OutputStream outputStream) {
		rawIn = in;
		pckIn = new PacketLineIn(rawIn);
		monitor = progress;
		messages = messageStream;
		currentTask = ""; //$NON-NLS-1$
		out = outputStream;
	}

	/** {@inheritDoc} */
	@Override
	public int read() throws IOException {
		needDataPacket();
		if (eof)
			return -1;
		available--;
		return rawIn.read();
	}

	/** {@inheritDoc} */
	@Override
	public int read(final byte[] b, int off, int len) throws IOException {
		int r = 0;
		while (len > 0) {
			needDataPacket();
			if (eof)
				break;
			final int n = rawIn.read(b, off, Math.min(len, available));
			if (n < 0)
				break;
			r += n;
			off += n;
			len -= n;
			available -= n;
		}
		return eof && r == 0 ? -1 : r;
	}

	private void needDataPacket() throws IOException {
		if (eof || (channel == CH_DATA && available > 0))
			return;
		for (;;) {
			available = pckIn.readLength();
			if (available == 0) {
				eof = true;
				return;
			}

			channel = rawIn.read() & 0xff;
			available -= HDR_SIZE; // length header plus channel indicator
			if (available == 0)
				continue;

			switch (channel) {
			case CH_DATA:
				return;
			case CH_PROGRESS:
				progress(readString(available));
				continue;
			case CH_ERROR:
				eof = true;
				throw new TransportException(remote(readString(available)));
			default:
				throw new PackProtocolException(
						MessageFormat.format(JGitText.get().invalidChannel,
								Integer.valueOf(channel)));
			}
		}
	}

	private void progress(String pkt) throws IOException {
		pkt = progressBuffer + pkt;
		for (;;) {
			final int lf = pkt.indexOf('\n');
			final int cr = pkt.indexOf('\r');
			final int s;
			if (0 <= lf && 0 <= cr)
				s = Math.min(lf, cr);
			else if (0 <= lf)
				s = lf;
			else if (0 <= cr)
				s = cr;
			else
				break;

			doProgressLine(pkt.substring(0, s + 1));
			pkt = pkt.substring(s + 1);
		}
		progressBuffer = pkt;
	}

	private void doProgressLine(final String msg) throws IOException {
		Matcher matcher;

		matcher = P_BOUNDED.matcher(msg);
		if (matcher.matches()) {
			final String taskname = matcher.group(1);
			if (!currentTask.equals(taskname)) {
				currentTask = taskname;
				lastCnt = 0;
				beginTask(Integer.parseInt(matcher.group(3)));
			}
			final int cnt = Integer.parseInt(matcher.group(2));
			monitor.update(cnt - lastCnt);
			lastCnt = cnt;
			return;
		}

		matcher = P_UNBOUNDED.matcher(msg);
		if (matcher.matches()) {
			final String taskname = matcher.group(1);
			if (!currentTask.equals(taskname)) {
				currentTask = taskname;
				lastCnt = 0;
				beginTask(ProgressMonitor.UNKNOWN);
			}
			final int cnt = Integer.parseInt(matcher.group(2));
			monitor.update(cnt - lastCnt);
			lastCnt = cnt;
			return;
		}

		messages.write(msg);
		if (out != null)
			out.write(msg.getBytes());
	}

	private void beginTask(final int totalWorkUnits) {
		monitor.beginTask(remote(currentTask), totalWorkUnits);
	}

	private static String remote(String msg) {
		String prefix = JGitText.get().prefixRemote;
		StringBuilder r = new StringBuilder(prefix.length() + msg.length() + 1);
		r.append(prefix);
		if (prefix.length() > 0 && prefix.charAt(prefix.length() - 1) != ' ') {
			r.append(' ');
		}
		r.append(msg);
		return r.toString();
	}

	private String readString(final int len) throws IOException {
		final byte[] raw = new byte[len];
		IO.readFully(rawIn, raw, 0, len);
		return RawParseUtils.decode(Constants.CHARSET, raw, 0, len);
	}
}
