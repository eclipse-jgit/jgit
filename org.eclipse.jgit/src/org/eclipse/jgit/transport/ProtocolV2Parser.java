package org.eclipse.jgit.transport;

import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_DEEPEN_RELATIVE;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_FILTER;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_INCLUDE_TAG;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_NO_PROGRESS;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_OFS_DELTA;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_SIDE_BAND_64K;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_THIN_PACK;
import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_WANT_REF;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;

class ProtocolV2Parser {

	private TransferConfig transferConfig;

	ProtocolV2Parser(TransferConfig transferConfig) {
		this.transferConfig = transferConfig;
	}

	FetchV2Request fetch(PacketLineIn pckIn, RefDatabase refdb)
			throws PackProtocolException, IOException {
		FetchV2Request.Builder reqBuilder = FetchV2Request.builder();

		// Packs are always sent multiplexed and using full 64K
		// lengths.
		reqBuilder.addOption(OPTION_SIDE_BAND_64K);

		String line;

		// Currently, we do not support any capabilities, so the next
		// line is DELIM.
		if ((line = pckIn.readString()) != PacketLineIn.DELIM) {
			throw new PackProtocolException(MessageFormat
					.format(JGitText.get().unexpectedPacketLine, line));
		}

		boolean filterReceived = false;
		while ((line = pckIn.readString()) != PacketLineIn.END) {
			if (line.startsWith("want ")) { //$NON-NLS-1$
				reqBuilder.addWantsIds(ObjectId.fromString(line.substring(5)));
			} else if (transferConfig.isAllowRefInWant()
					&& line.startsWith(OPTION_WANT_REF + " ")) { //$NON-NLS-1$
				String refName = line.substring(OPTION_WANT_REF.length() + 1);
				// TODO(ifrade): This validation should be done after the
				// protocol parsing. It is not a protocol problem asking for an
				// unexisting ref and we wouldn't need the ref database here
				Ref ref = refdb.exactRef(refName);
				if (ref == null) {
					throw new PackProtocolException(MessageFormat
							.format(JGitText.get().invalidRefName, refName));
				}
				ObjectId oid = ref.getObjectId();
				if (oid == null) {
					throw new PackProtocolException(MessageFormat
							.format(JGitText.get().invalidRefName, refName));
				}
				reqBuilder.addWantedRef(refName, oid);
				reqBuilder.addWantsIds(oid);
			} else if (line.startsWith("have ")) { //$NON-NLS-1$
				reqBuilder.addPeerHas(ObjectId.fromString(line.substring(5)));
			} else if (line.equals("done")) { //$NON-NLS-1$
				reqBuilder.setDoneReceived();
			} else if (line.equals(OPTION_THIN_PACK)) {
				reqBuilder.addOption(OPTION_THIN_PACK);
			} else if (line.equals(OPTION_NO_PROGRESS)) {
				reqBuilder.addOption(OPTION_NO_PROGRESS);
			} else if (line.equals(OPTION_INCLUDE_TAG)) {
				reqBuilder.addOption(OPTION_INCLUDE_TAG);
			} else if (line.equals(OPTION_OFS_DELTA)) {
				reqBuilder.addOption(OPTION_OFS_DELTA);
			} else if (line.startsWith("shallow ")) { //$NON-NLS-1$
				reqBuilder.addClientShallowCommit(
						ObjectId.fromString(line.substring(8)));
			} else if (line.startsWith("deepen ")) { //$NON-NLS-1$
				int parsedDepth = Integer.parseInt(line.substring(7));
				if (parsedDepth <= 0) {
					throw new PackProtocolException(
							MessageFormat.format(JGitText.get().invalidDepth,
									Integer.valueOf(parsedDepth)));
				}
				if (reqBuilder.getShallowSince() != 0) {
					throw new PackProtocolException(
							JGitText.get().deepenSinceWithDeepen);
				}
				if (reqBuilder.hasDeepenNotRefs()) {
					throw new PackProtocolException(
							JGitText.get().deepenNotWithDeepen);
				}
				reqBuilder.setDepth(parsedDepth);
			} else if (line.startsWith("deepen-not ")) { //$NON-NLS-1$
				reqBuilder.addDeepenNotRef(line.substring(11));
				if (reqBuilder.getDepth() != 0) {
					throw new PackProtocolException(
							JGitText.get().deepenNotWithDeepen);
				}
			} else if (line.equals(OPTION_DEEPEN_RELATIVE)) {
				reqBuilder.addOption(OPTION_DEEPEN_RELATIVE);
			} else if (line.startsWith("deepen-since ")) { //$NON-NLS-1$
				int parsedShallowSince = Integer.parseInt(line.substring(13));
				if (parsedShallowSince <= 0) {
					throw new PackProtocolException(MessageFormat
							.format(JGitText.get().invalidTimestamp, line));
				}
				if (reqBuilder.getDepth() != 0) {
					throw new PackProtocolException(
							JGitText.get().deepenSinceWithDeepen);
				}
				reqBuilder.setShallowSince(parsedShallowSince);
			} else if (transferConfig.isAllowFilter()
					&& line.startsWith(OPTION_FILTER + ' ')) {
				if (filterReceived) {
					throw new PackProtocolException(
							JGitText.get().tooManyFilters);
				}
				filterReceived = true;
				reqBuilder.setFilterBlobLimit(filterLine(
						line.substring(OPTION_FILTER.length() + 1)));
			} else {
				throw new PackProtocolException(MessageFormat
						.format(JGitText.get().unexpectedPacketLine, line));
			}
		}

		return reqBuilder.build();
	}

	LsRefsV2Request lsRef(PacketLineIn pckIn) throws IOException {
		LsRefsV2Request.Builder builder = LsRefsV2Request.builder();
		List<String> prefixes = new ArrayList<>();
		String line = pckIn.readString();
		// Currently, we do not support any capabilities, so the next
		// line is DELIM if there are arguments or END if not.
		if (line == PacketLineIn.DELIM) {
			while ((line = pckIn.readString()) != PacketLineIn.END) {
				if (line.equals("peel")) { //$NON-NLS-1$
					builder.setPeel(true);
				} else if (line.equals("symrefs")) { //$NON-NLS-1$
					builder.setSymrefs(true);
				} else if (line.startsWith("ref-prefix ")) { //$NON-NLS-1$
					prefixes.add(line.substring("ref-prefix ".length())); //$NON-NLS-1$
				} else {
					throw new PackProtocolException(MessageFormat
							.format(JGitText.get().unexpectedPacketLine, line));
				}
			}
		} else if (line != PacketLineIn.END) {
			throw new PackProtocolException(MessageFormat
					.format(JGitText.get().unexpectedPacketLine, line));
		}

		return builder.setRefPrefixes(prefixes).build();
	}

	long filterLine(String arg) throws PackProtocolException {
		long blobLimit = -1;

		if (arg.equals("blob:none")) { //$NON-NLS-1$
			blobLimit = 0;
		} else if (arg.startsWith("blob:limit=")) { //$NON-NLS-1$
			try {
				blobLimit = Long
						.parseLong(arg.substring("blob:limit=".length())); //$NON-NLS-1$
			} catch (NumberFormatException e) {
				throw new PackProtocolException(MessageFormat
						.format(JGitText.get().invalidFilter, arg));
			}
		}
		/*
		 * We must have (1) either "blob:none" or "blob:limit=" set (because we
		 * only support blob size limits for now), and (2) if the latter, then
		 * it must be nonnegative. Throw if (1) or (2) is not met.
		 */
		if (blobLimit < 0) {
			throw new PackProtocolException(
					MessageFormat.format(JGitText.get().invalidFilter, arg));
		}

		return blobLimit;
	}

}
