/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, 2022 Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import static java.nio.charset.StandardCharsets.UTF_8;
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
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @since 4.11
 */
public class SideBandInputStream extends InputStream {

	private static final Logger LOG = LoggerFactory
			.getLogger(SideBandInputStream.class);

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
	public int read(byte[] b, int off, int len) throws IOException {
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

	private void doProgressLine(String msg) throws IOException {
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
			out.write(msg.getBytes(UTF_8));
	}

	private void beginTask(int totalWorkUnits) {
		monitor.beginTask(remote(currentTask), totalWorkUnits);
	}

	/**
	 * Forces any buffered progress messages to be written.
	 */
	void drainMessages() {
		if (!progressBuffer.isEmpty()) {
			try {
				progress("\n"); //$NON-NLS-1$
			} catch (IOException e) {
				// Just log; otherwise this IOException might hide a real
				// TransportException
				LOG.error(e.getMessage(), e);
			}
		}
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

	private String readString(int len) throws IOException {
		final byte[] raw = new byte[len];
		IO.readFully(rawIn, raw, 0, len);
		return RawParseUtils.decode(UTF_8, raw, 0, len);
	}
}
