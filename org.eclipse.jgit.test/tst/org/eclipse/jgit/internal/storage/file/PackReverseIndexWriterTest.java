/*
TODO
 */

package org.eclipse.jgit.internal.storage.file;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;

import org.junit.Test;

public class PackReverseIndexWriterTest {

	@Test
	public void createWriter_defaultVersion() {
		PackReverseIndexWriter version1 = PackReverseIndexWriter.createWriter(new ByteArrayOutputStream());

		assertTrue(version1 instanceof PackReverseIndexWriterV1);
	}

	@Test
	public void createWriter_version1() {
		PackReverseIndexWriter version1 = PackReverseIndexWriter.createWriter(new ByteArrayOutputStream(), 1);

		assertTrue(version1 instanceof PackReverseIndexWriterV1);
	}

	@Test
	public void createWriter_unsupportedVersion() {
		assertThrows(IllegalArgumentException.class,
				() -> PackReverseIndexWriter.createWriter(new ByteArrayOutputStream(), 2));
	}
}
