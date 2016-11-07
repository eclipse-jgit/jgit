package org.eclipse.jgit.internal.storage.dfs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.eclipse.jgit.internal.JGitText;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DfsBlockCacheConfigTest {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void blockSizeNotPowerOfTwoExpectsException() {
		DfsBlockCacheConfig config = new DfsBlockCacheConfig();
		config.setBlockSize(1000);

		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage(is(JGitText.get().bSizeMustBePowerOf2));

		DfsBlockCache.reconfigure(config);
	}

	@Test
	public void negativeBlockSizeIsConvertedToDefault() {
		DfsBlockCacheConfig config = new DfsBlockCacheConfig();
		config.setBlockSize(-1);
		DfsBlockCache.reconfigure(config);

		assertThat(DfsBlockCache.getInstance().getBlockSize(), is(512));
	}

	@Test
	public void validBlockSize() {
		DfsBlockCacheConfig config = new DfsBlockCacheConfig();
		config.setBlockSize(65536);
		DfsBlockCache.reconfigure(config);

		assertThat(DfsBlockCache.getInstance().getBlockSize(), is(65536));
	}
}
