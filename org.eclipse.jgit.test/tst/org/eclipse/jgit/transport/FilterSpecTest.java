package org.eclipse.jgit.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.eclipse.jgit.errors.PackProtocolException;
import org.junit.Test;

public class FilterSpecTest {
	@Test
	public void singleFilter_blobNone() throws PackProtocolException {
		FilterSpec spec = FilterSpec.fromFilterLine("blob:none");
		assertEquals(0L, spec.getBlobLimit());
		assertEquals(-1L, spec.getTreeDepthLimit());
		assertFalse(spec.isNoOp());
	}

	@Test
	public void singleFilter_blobX() throws PackProtocolException {
		FilterSpec spec = FilterSpec.fromFilterLine("blob:limit=10");
		assertEquals(10L, spec.getBlobLimit());
		assertEquals(-1L, spec.getTreeDepthLimit());
		assertFalse(spec.isNoOp());
	}

	@Test
	public void singleFilter_treeZero() throws PackProtocolException {
		FilterSpec spec = FilterSpec.fromFilterLine("tree:0");
		assertEquals(-1L, spec.getBlobLimit());
		assertEquals(0L, spec.getTreeDepthLimit());
		assertFalse(spec.isNoOp());
	}

	@Test
	public void combinedFilter_blobTree() throws PackProtocolException {
		FilterSpec spec = FilterSpec
				.fromFilterLine("combine:blob:limit=10+tree:3");
		assertEquals(10L, spec.getBlobLimit());
		assertEquals(3L, spec.getTreeDepthLimit());
		assertFalse(spec.isNoOp());
	}

	@Test
	public void singleFilter_negativeBlobSize() {
		assertThrows(PackProtocolException.class, () -> FilterSpec.fromFilterLine("blob:limit=-100"));
	}

	@Test
	public void singleFilter_negativeTreeDepth() {
		assertThrows(PackProtocolException.class,
				() -> FilterSpec.fromFilterLine("tree:-100"));
	}

	@Test
	public void invalidFilter() {
		assertThrows(PackProtocolException.class,
				() -> FilterSpec.fromFilterLine("random-text"));
	}
}
