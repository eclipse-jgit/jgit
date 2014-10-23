package org.eclipse.jgit.lib;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jgit.transport.PackParser;
import org.junit.Before;
import org.junit.Test;

public class ObjectInserterTest {

	private ObjectInserter objectInserter;

	@Test
	public void testIdForWithCorrectLength() throws IOException {
		byte[] bytes = new byte[] { 1, 2, 3 };
		ObjectId objectId = objectInserter.idFor(OBJ_BLOB, bytes.length,
				asInputStream(bytes));
		assertNotNull(objectId);
	}

	@Test(expected = IOException.class)
	public void testIdForWithLengthGreaterThanStreamSize() throws IOException {
		byte[] bytes = new byte[] { 1, 2, 3 };
		objectInserter.idFor(OBJ_BLOB, bytes.length + 1, asInputStream(bytes));
	}

	@Test(expected = IOException.class)
	public void testIdForWithLengthLessThanStreamSize() throws IOException {
		byte[] bytes = new byte[] { 1, 2, 3 };
		objectInserter.idFor(OBJ_BLOB, bytes.length - 1, asInputStream(bytes));
	}

	@Test(expected = IOException.class)
	public void testIdForWithNegativeLength() throws IOException {
		byte[] bytes = new byte[] { 1, 2, 3 };
		objectInserter.idFor(OBJ_BLOB, -1, asInputStream(bytes));
	}

	@Before
	public void setUp() {
		objectInserter = new TestObjectInserter();
	}

	private ByteArrayInputStream asInputStream(byte[] bytes) {
		return new ByteArrayInputStream(bytes);
	}

	private static class TestObjectInserter extends ObjectInserter {
		@Override
		public ObjectId insert(int objectType, long length, InputStream in) {
			throw new UnsupportedOperationException();
		}

		@Override
		public PackParser newPackParser(InputStream in) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ObjectReader newReader() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void flush() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void release() {
			throw new UnsupportedOperationException();
		}
	}
}
