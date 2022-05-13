/*
 * Copyright (C) 2018, Google LLC. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.transport;

import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_DEEPEN_RELATIVE;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_FILTER;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_AGENT;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_INCLUDE_TAG;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_NO_PROGRESS;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_OFS_DELTA;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_SERVER_OPTION;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_SIDEBAND_ALL;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_SIDE_BAND_64K;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_THIN_PACK;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_WAIT_FOR_DONE;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_WANT_REF;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Parse the incoming git protocol lines from the wire and translate them into a
 * Request object.
 *
 * It requires a transferConfig object to know what the server supports (e.g.
 * ref-in-want and/or filters).
 */
final class ProtocolV2Parser {

	private final TransferConfig transferConfig;

	ProtocolV2Parser(TransferConfig transferConfig) {
		this.transferConfig = transferConfig;
	}

	/*
	 * Read lines until DELIM or END, calling the appropiate consumer.
	 *
	 * Returns the last read line (so caller can check if there is more to read
	 * in the line).
	 */
	private static String consumeCapabilities(PacketLineIn pckIn,
			Consumer<String> serverOptionConsumer,
			Consumer<String> agentConsumer) throws IOException {

		String serverOptionPrefix = OPTION_SERVER_OPTION + '=';
		String agentPrefix = OPTION_AGENT + '=';

		String line = pckIn.readString();
		while (!PacketLineIn.isDelimiter(line) && !PacketLineIn.isEnd(line)) {
			if (line.startsWith(serverOptionPrefix)) {
				serverOptionConsumer
						.accept(line.substring(serverOptionPrefix.length()));
			} else if (line.startsWith(agentPrefix)) {
				agentConsumer.accept(line.substring(agentPrefix.length()));
			} else {
				// Unrecognized capability. Ignore it.
			}
			line = pckIn.readString();
		}

		return line;
	}

	/**
	 * Parse the incoming fetch request arguments from the wire. The caller must
	 * be sure that what is comings is a fetch request before coming here.
	 *
	 * @param pckIn
	 *            incoming lines
	 * @return A FetchV2Request populated with information received from the
	 *         wire.
	 * @throws PackProtocolException
	 *             incompatible options, wrong type of arguments or other issues
	 *             where the request breaks the protocol.
	 * @throws IOException
	 *             an IO error prevented reading the incoming message.
	 */
	FetchV2Request parseFetchRequest(PacketLineIn pckIn)
			throws PackProtocolException, IOException {
		FetchV2Request.Builder reqBuilder = FetchV2Request.builder();

		// Packs are always sent multiplexed and using full 64K
		// lengths.
		reqBuilder.addClientCapability(OPTION_SIDE_BAND_64K);

		String line = consumeCapabilities(pckIn,
				serverOption -> reqBuilder.addServerOption(serverOption),
				agent -> reqBuilder.setAgent(agent));

		if (PacketLineIn.isEnd(line)) {
			return reqBuilder.build();
		}

		if (!PacketLineIn.isDelimiter(line)) {
			throw new PackProtocolException(
					MessageFormat.format(JGitText.get().unexpectedPacketLine,
							line));
		}

		boolean filterReceived = false;
		for (String line2 : pckIn.readStrings()) {
			if (line2.startsWith("want ")) { //$NON-NLS-1$
				reqBuilder.addWantId(ObjectId.fromString(line2.substring(5)));
			} else if (transferConfig.isAllowRefInWant()
					&& line2.startsWith(OPTION_WANT_REF + " ")) { //$NON-NLS-1$
				reqBuilder.addWantedRef(
						line2.substring(OPTION_WANT_REF.length() + 1));
			} else if (line2.startsWith("have ")) { //$NON-NLS-1$
				reqBuilder.addPeerHas(ObjectId.fromString(line2.substring(5)));
			} else if (line2.equals("done")) { //$NON-NLS-1$
				reqBuilder.setDoneReceived();
			} else if (line2.equals(OPTION_WAIT_FOR_DONE)) {
				reqBuilder.setWaitForDone();
			} else if (line2.equals(OPTION_THIN_PACK)) {
				reqBuilder.addClientCapability(OPTION_THIN_PACK);
			} else if (line2.equals(OPTION_NO_PROGRESS)) {
				reqBuilder.addClientCapability(OPTION_NO_PROGRESS);
			} else if (line2.equals(OPTION_INCLUDE_TAG)) {
				reqBuilder.addClientCapability(OPTION_INCLUDE_TAG);
			} else if (line2.equals(OPTION_OFS_DELTA)) {
				reqBuilder.addClientCapability(OPTION_OFS_DELTA);
			} else if (line2.startsWith("shallow ")) { //$NON-NLS-1$
				reqBuilder.addClientShallowCommit(
						ObjectId.fromString(line2.substring(8)));
			} else if (line2.startsWith("deepen ")) { //$NON-NLS-1$
				int parsedDepth = Integer.parseInt(line2.substring(7));
				if (parsedDepth <= 0) {
					throw new PackProtocolException(
							MessageFormat.format(JGitText.get().invalidDepth,
									Integer.valueOf(parsedDepth)));
				}
				if (reqBuilder.getDeepenSince() != 0) {
					throw new PackProtocolException(
							JGitText.get().deepenSinceWithDeepen);
				}
				if (reqBuilder.hasDeepenNots()) {
					throw new PackProtocolException(
							JGitText.get().deepenNotWithDeepen);
				}
				reqBuilder.setDepth(parsedDepth);
			} else if (line2.startsWith("deepen-not ")) { //$NON-NLS-1$
				reqBuilder.addDeepenNot(line2.substring(11));
				if (reqBuilder.getDepth() != 0) {
					throw new PackProtocolException(
							JGitText.get().deepenNotWithDeepen);
				}
			} else if (line2.equals(OPTION_DEEPEN_RELATIVE)) {
				reqBuilder.addClientCapability(OPTION_DEEPEN_RELATIVE);
			} else if (line2.startsWith("deepen-since ")) { //$NON-NLS-1$
				int ts = Integer.parseInt(line2.substring(13));
				if (ts <= 0) {
					throw new PackProtocolException(MessageFormat
							.format(JGitText.get().invalidTimestamp, line2));
				}
				if (reqBuilder.getDepth() != 0) {
					throw new PackProtocolException(
							JGitText.get().deepenSinceWithDeepen);
				}
				reqBuilder.setDeepenSince(ts);
			} else if (transferConfig.isAllowFilter()
					&& line2.startsWith(OPTION_FILTER + ' ')) {
				if (filterReceived) {
					throw new PackProtocolException(
							JGitText.get().tooManyFilters);
				}
				filterReceived = true;
				reqBuilder.setFilterSpec(FilterSpec.fromFilterLine(
						line2.substring(OPTION_FILTER.length() + 1)));
			} else if (transferConfig.isAllowSidebandAll()
					&& line2.equals(OPTION_SIDEBAND_ALL)) {
				reqBuilder.setSidebandAll(true);
			} else if (line2.startsWith("packfile-uris ")) { //$NON-NLS-1$
				for (String s : line2.substring(14).split(",")) { //$NON-NLS-1$
					reqBuilder.addPackfileUriProtocol(s);
				}
			} else {
				throw new PackProtocolException(MessageFormat
						.format(JGitText.get().unexpectedPacketLine, line2));
			}
		}

		return reqBuilder.build();
	}

	/**
	 * Parse the incoming ls-refs request arguments from the wire. This is meant
	 * for calling immediately after the caller has consumed a "command=ls-refs"
	 * line indicating the beginning of a ls-refs request.
	 *
	 * The incoming PacketLineIn is consumed until an END line, but the caller
	 * is responsible for closing it (if needed)
	 *
	 * @param pckIn
	 *            incoming lines. This method will read until an END line.
	 * @return a LsRefsV2Request object with the data received in the wire.
	 * @throws PackProtocolException
	 *             for inconsistencies in the protocol (e.g. unexpected lines)
	 * @throws IOException
	 *             reporting problems reading the incoming messages from the
	 *             wire
	 */
	LsRefsV2Request parseLsRefsRequest(PacketLineIn pckIn)
			throws PackProtocolException, IOException {
		LsRefsV2Request.Builder builder = LsRefsV2Request.builder();
		List<String> prefixes = new ArrayList<>();

		String line = consumeCapabilities(pckIn,
				serverOption -> builder.addServerOption(serverOption),
				agent -> builder.setAgent(agent));

		if (PacketLineIn.isEnd(line)) {
			return builder.build();
		}

		if (!PacketLineIn.isDelimiter(line)) {
			throw new PackProtocolException(MessageFormat
					.format(JGitText.get().unexpectedPacketLine, line));
		}

		for (String line2 : pckIn.readStrings()) {
			if (line2.equals("peel")) { //$NON-NLS-1$
				builder.setPeel(true);
			} else if (line2.equals("symrefs")) { //$NON-NLS-1$
				builder.setSymrefs(true);
			} else if (line2.startsWith("ref-prefix ")) { //$NON-NLS-1$
				prefixes.add(line2.substring("ref-prefix ".length())); //$NON-NLS-1$
			} else {
				throw new PackProtocolException(MessageFormat
						.format(JGitText.get().unexpectedPacketLine, line2));
			}
		}

		return builder.setRefPrefixes(prefixes).build();
	}

	ObjectInfoRequest parseObjectInfoRequest(PacketLineIn pckIn)
			throws PackProtocolException, IOException {
		ObjectInfoRequest.Builder builder = ObjectInfoRequest.builder();
		List<ObjectId> objectIDs = new ArrayList<>();

		String line = pckIn.readString();

		if (PacketLineIn.isEnd(line)) {
			return builder.build();
		}

		if (!line.equals("size")) { //$NON-NLS-1$
			throw new PackProtocolException(MessageFormat
					.format(JGitText.get().unexpectedPacketLine, line));
		}

		for (String line2 : pckIn.readStrings()) {
			if (!line2.startsWith("oid ")) { //$NON-NLS-1$
				throw new PackProtocolException(MessageFormat
						.format(JGitText.get().unexpectedPacketLine, line2));
			}

			String oidStr = line2.substring("oid ".length()); //$NON-NLS-1$

			try {
				objectIDs.add(ObjectId.fromString(oidStr));
			} catch (InvalidObjectIdException e) {
				throw new PackProtocolException(MessageFormat
						.format(JGitText.get().invalidObject, oidStr), e);
			}
		}

		return builder.setObjectIDs(objectIDs).build();
	}
}
