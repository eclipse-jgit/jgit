package org.eclipse.jgit.diff;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;


public class RawTextLoadTest extends RepositoryTestCase {
	private byte[] generate(int size, int nullAt) {
		byte[] data = new byte[size];
		for (int i = 0; i < data.length; i++) {
			data[i] = (byte) ((i % 72 == 0) ? '\n' : (i%10) + '0');
		}
		if (nullAt >= 0) {
			data[nullAt] = '\0';
		}
		return data;
	}

	private RawText textFor(byte[] data, int threshold) throws IOException {
		FileRepository repo = createBareRepository();
		ObjectId id;
		try (ObjectInserter ins = repo.getObjectDatabase().newInserter()) {
			id = ins.insert(Constants.OBJ_BLOB, data);
		}
		ObjectLoader ldr = repo.open(id);
		return RawText.load(ldr, threshold);
	}

	@Test
	public void testSmallOK() throws Exception {
		byte[] data = generate(1000, -1);
		RawText result = textFor(data, 1 << 20);
		assertNotNull(result);
		Arrays.equals(result.content, data);
	}

	@Test
	public void testSmallNull() throws Exception {
		byte[] data = generate(1000, 22);
		RawText result = textFor(data, 1 << 20);
		assertNull(result);
	}

	@Test
	public void testBigOK() throws Exception {
		byte[] data = generate(10000, -1);
		RawText result = textFor(data, 1 << 20);
		assertNotNull(result);
		Arrays.equals(result.content, data);
	}

	@Test
	public void testBigWithNullAtStart() throws Exception {
		byte[] data = generate(10000, 22);
		RawText result = textFor(data, 1 << 20);
		assertNull(result);
	}

	@Test
	public void testBinaryThreshold() throws Exception {
		byte[] data = generate(2 << 20, -1);
		RawText result = textFor(data, 1 << 20);
		assertNull(result);
	}
}
