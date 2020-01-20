package org.eclipse.jgit.util.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An output stream that writes all data to two streams.
 */
public class TeeOutputStream extends OutputStream {

	private final OutputStream stream1;
	private final OutputStream stream2;

	/**
	 * Initialize a tee output stream.
	 *
	 * @param stream1 first output stream
	 * @param stream2 second output stream
	 */
	public TeeOutputStream(OutputStream stream1, OutputStream stream2) {
		this.stream1 = stream1;
		this.stream2 = stream2;
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] buf) throws IOException {
		this.stream1.write(buf);
		this.stream2.write(buf);
	}

	/** {@inheritDoc} */
	@Override
	public void write(byte[] buf, int off, int len) throws IOException {
		this.stream1.write(buf, off, len);
		this.stream2.write(buf, off, len);
	}

	/** {@inheritDoc} */
	@Override
	public void write(int b) throws IOException {
		this.stream1.write(b);
		this.stream2.write(b);
	}

	/** {@inheritDoc} */
	@Override
	public void flush() throws IOException {
		this.stream1.flush();
		this.stream2.flush();
	}

	/** {@inheritDoc} */
	@Override
	public void close() throws IOException {
		this.stream1.close();
		this.stream2.close();
	}
}
