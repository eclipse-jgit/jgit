package org.eclipse.jgit.internal.storage.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.eclipse.jgit.internal.storage.dfs.ReadableChannel;

/**
 * Readable channel based on a File
 */
class ReadableFileChannel implements ReadableChannel {

	private static final int BLOCK_SIZE = 1 << 20;

	private final RandomAccessFile rf;

	private final FileChannel channel;

	/**
	 * Create a new ReadableFileChannel
	 *
	 * @param file
	 *            file to open
	 * @param mode
	 *            the access mode, as defined in {@link RandomAccessFile}
	 * @throws FileNotFoundException
	 */
	public ReadableFileChannel(File file, String mode)
			throws FileNotFoundException {
		this.rf = new RandomAccessFile(file, mode);
		this.channel = rf.getChannel();
	}

	@Override
	public int read(ByteBuffer dst) throws IOException {
		return channel.read(dst);
	}

	@Override
	public boolean isOpen() {
		return channel.isOpen();
	}

	@Override
	public void close() throws IOException {
		channel.close();
		rf.close();
	}

	@Override
	public long position() throws IOException {
		return channel.position();
	}

	@Override
	public void position(long newPosition) throws IOException {
		channel.position(newPosition);
	}

	@Override
	public long size() throws IOException {
		return channel.size();
	}

	@Override
	public int blockSize() {
		return BLOCK_SIZE;
	}

	@Override
	public void setReadAheadBytes(int bufferSize) throws IOException {
		// TODO Auto-generated method stub

	}

}
