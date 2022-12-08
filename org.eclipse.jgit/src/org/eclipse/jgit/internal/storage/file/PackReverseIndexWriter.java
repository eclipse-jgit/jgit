// TODO: copyright?

package org.eclipse.jgit.internal.storage.file;

import static org.eclipse.jgit.internal.storage.file.PackReverseIndex.VERSION_1;

import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.PackedObjectInfo;

/**
 * TODO
 */
public abstract class PackReverseIndexWriter {
	protected final DigestOutputStream out;
	protected final DataOutput dataOutput;

	protected List<? extends PackedObjectInfo> objectsSortedByIndexPosition;
	private byte[] packChecksum;

	private static final int DEFAULT_VERSION = VERSION_1;

	protected PackReverseIndexWriter(OutputStream dst) {
		out = new DigestOutputStream(dst instanceof BufferedOutputStream ? dst : new BufferedOutputStream(dst),
				Constants.newMessageDigest());
		dataOutput = new SimpleDataOutput(out);
	}

	public static PackReverseIndexWriter createWriter(final OutputStream dst) {
		return createWriter(dst, DEFAULT_VERSION);
	}

	public static PackReverseIndexWriter createWriter(final OutputStream dst, final int version) {
		if (version == VERSION_1) {
			return new PackReverseIndexWriterV1(dst);
		} else {
			throw new IllegalArgumentException(
					MessageFormat.format(JGitText.get().unsupportedPackReverseIndexVersion, version));
		}
	}

	public void write(List<? extends PackedObjectInfo> objectsSortedByIndexPosition, byte[] packChecksum)
			throws IOException {
		this.objectsSortedByIndexPosition = objectsSortedByIndexPosition;
		this.packChecksum = packChecksum;
		writeHeader();
		writeBody();
		writeFooter();
		out.flush();
	}

	protected abstract void writeHeader() throws IOException;

	protected abstract void writeBody() throws IOException;

	private void writeFooter() throws IOException {
		out.write(packChecksum);
		byte[] selfChecksum = out.getMessageDigest().digest();
		out.write(selfChecksum);
	}
}
